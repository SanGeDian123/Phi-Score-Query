//! Daily rewards are server-authoritative and idempotent per user / Shanghai date.
use axum::{extract::{State, Query}, Extension, Json, Router, routing::get};
use chrono::{Utc, NaiveDate, Datelike};
use rand::{Rng, seq::SliceRandom};
use serde::{Serialize, Deserialize};
use std::collections::HashMap;
use crate::{state::AppState, AppError, features::auth::bearer::BearerAuthState};

// Keep in sync with the append-only checkinPoems catalog in Android CheckinModels.kt.
const CHECKIN_QUOTE_COUNT: usize = 56;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CheckinChart {
    pub song_id: String, pub song_name: String, pub difficulty: String,
    pub target_score: u32, pub target_accuracy: f64,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CheckinRecord {
    pub date: String, pub coin: i64, pub luck: i64, pub quote_index: usize,
    pub chart: Option<CheckinChart>,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CheckinStatus {
    pub server_date: String, pub month: String, pub total_coin: i64, pub total_days: i64,
    pub today: Option<CheckinRecord>, pub dates: Vec<String>,
}
#[derive(Deserialize)]
pub struct MonthQuery { pub month: Option<String> }
pub fn router() -> Router<AppState> {
    Router::new().route("/checkin", get(status).post(checkin))
        .route("/checkin/leaderboard", get(leaderboard))
}
fn user(bearer: &BearerAuthState) -> Result<&str, AppError> {
    match bearer {
        BearerAuthState::Valid(ctx) => Ok(&ctx.claims.sub),
        _ => Err(AppError::Auth("请先登录".into())),
    }
}
pub fn today() -> String { Utc::now().with_timezone(&chrono_tz::Asia::Shanghai).format("%Y-%m-%d").to_string() }
fn month(value: Option<&str>, date: &str) -> Result<String, AppError> {
    let value = value.unwrap_or(&date[..7]);
    let parsed = NaiveDate::parse_from_str(&format!("{value}-01"), "%Y-%m-%d")
        .map_err(|_| AppError::Validation("月份格式须为 YYYY-MM".into()))?;
    if value.len() != 7 || !(2020..=2100).contains(&parsed.year()) { return Err(AppError::Validation("月份无效".into())); }
    Ok(value.to_string())
}
async fn status(State(state): State<AppState>, Extension(bearer): Extension<BearerAuthState>, Query(q): Query<MonthQuery>) -> Result<Json<CheckinStatus>, AppError> {
    let date = today(); let month = month(q.month.as_deref(), &date)?;
    let storage = state.stats_storage.as_ref().ok_or_else(|| AppError::Internal("签到存储不可用".into()))?;
    Ok(Json(hide_unknown_checkin_chart(storage.checkin_status(user(&bearer)?, &date, &month).await?, &state)))
}
async fn checkin(State(state): State<AppState>, Extension(bearer): Extension<BearerAuthState>) -> Result<Json<CheckinStatus>, AppError> {
    let id = user(&bearer)?; let date = today();
    let storage = state.stats_storage.as_ref().ok_or_else(|| AppError::Internal("签到存储不可用".into()))?;
    let existing = storage.checkin_status(id, &date, &date[..7]).await?;
    if existing.today.is_some() { return Ok(Json(hide_unknown_checkin_chart(existing, &state))); }
    // Recommendation failure must never prevent receiving the daily reward.
    let started = std::time::Instant::now();
    let chart = tokio::time::timeout(std::time::Duration::from_secs(8), recommendation(&state, &bearer)).await.ok().and_then(Result::ok).flatten();
    tracing::info!(target: "phi_backend::checkin::performance", recommendation_ms = started.elapsed().as_millis(), has_chart = chart.is_some(), "checkin recommendation ready");
    let date = today(); // A save download may have crossed midnight.
    let record = CheckinRecord { date: date.clone(), coin: rand::thread_rng().gen_range(15..=50), luck: rand::thread_rng().gen_range(0..=100), quote_index: rand::thread_rng().gen_range(0..CHECKIN_QUOTE_COUNT), chart };
    storage.insert_checkin(id, &record).await?;
    Ok(Json(hide_unknown_checkin_chart(storage.checkin_status(id, &date, &date[..7]).await?, &state)))
}
async fn recommendation(state: &AppState, bearer: &BearerAuthState) -> Result<Option<CheckinChart>, AppError> {
    let parsed = crate::features::save::handler::load_parsed_save_for_checkin(state, bearer, "cn").await?;
    let constants = state.chart_constants.snapshot();
    let catalog = state.song_catalog.snapshot();
    let known_song_ids = catalog.by_id.keys().cloned().collect::<std::collections::HashSet<_>>();
    let ranking_records = parsed.game_record.iter()
        .filter(|(song_id, _)| known_song_ids.contains(*song_id))
        .map(|(song_id, records)| (song_id.clone(), records.clone()))
        .collect::<HashMap<_, _>>();
    let ranking = tokio::task::spawn_blocking(move || crate::features::rks::engine::calculate_player_rks(&ranking_records, &constants))
        .await.map_err(|e| AppError::Internal(format!("checkin ranking: {e}")))?;
    let mut candidates = Vec::new();
    // Best27 charts below AP can make a real positive contribution by improving ACC.
    for ranked in ranking.b30_charts.iter().take(27) {
        if !catalog.by_id.contains_key(&ranked.song_id) { continue; }
        let Some(record) = parsed.game_record.get(&ranked.song_id).and_then(|r| r.iter().find(|r| r.difficulty == ranked.difficulty)) else { continue };
        if !record.accuracy.is_finite() || record.accuracy < 70.0 || record.accuracy >= 100.0 { continue; }
        let accuracy = ((f64::from(record.accuracy) + 0.5) * 100.0).ceil().min(10000.0) / 100.0;
        // Score depends on combo as well as ACC: this is an explicit estimated goal.
        let score = (900_000.0 * accuracy / 100.0 + 100_000.0).ceil() as u32;
        candidates.push(CheckinChart { song_id: ranked.song_id.clone(), song_name: catalog.by_id.get(&ranked.song_id).map(|s| s.name.clone()).unwrap_or(ranked.song_id.clone()), difficulty: ranked.difficulty.to_string(), target_score: score, target_accuracy: accuracy });
    }
    Ok(candidates.choose(&mut rand::thread_rng()).cloned())
}

fn hide_unknown_checkin_chart(mut status: CheckinStatus, state: &AppState) -> CheckinStatus {
    let catalog = state.song_catalog.snapshot();
    if status.today.as_ref().and_then(|record| record.chart.as_ref())
        .is_some_and(|chart| !catalog.by_id.contains_key(&chart.song_id))
    {
        if let Some(today) = status.today.as_mut() { today.chart = None; }
    }
    status
}
async fn leaderboard(State(state): State<AppState>, Extension(bearer): Extension<BearerAuthState>) -> Result<Json<serde_json::Value>, AppError> {
    user(&bearer)?;
    let storage = state.stats_storage.as_ref().ok_or_else(|| AppError::Internal("签到存储不可用".into()))?;
    Ok(Json(storage.checkin_leaderboard().await?))
}
