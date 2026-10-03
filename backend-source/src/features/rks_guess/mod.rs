//! RKS 猜猜乐：短生命周期、服务端权威的单人/公开匹配小游戏。
//!
//! 游戏状态暂存于当前后端进程内。公开匹配使用双方各自的实时 B30；单人模式
//! 从服务器匿名题库固定抽取另一位玩家的脱敏快照，不保存 SessionToken 或 Bearer Token。

use axum::{
    Extension, Json, Router,
    extract::{Path, State},
    response::{IntoResponse, Response},
    routing::{get, post},
};
use chrono::Utc;
use once_cell::sync::Lazy;
use rand::{seq::SliceRandom, thread_rng};
use serde::{Deserialize, Serialize};
use std::{
    collections::{HashSet, VecDeque},
    time::Duration,
};
use tokio::sync::{Mutex, Semaphore};
use uuid::Uuid;

use crate::{
    AppError,
    features::{
        auth::bearer::BearerAuthState,
        rks::engine::calculate_player_rks,
        save::{handler::load_parsed_save_for_feature, provider::ParsedSave},
    },
    rks_contract::engine::PlayerRksResult,
    state::AppState,
};

const ROUND_LIMIT: u8 = 5;
const TIE_BREAK_ROUND: u8 = 6;
const ANSWER_WINDOW_MS: i64 = 60_000;
const WAITING_TTL_MS: i64 = 120_000;
const PLAYING_TTL_MS: i64 = 10 * 60_000;
const FINISHED_TTL_MS: i64 = 15 * 60_000;
const SINGLE_WIN_TOLERANCE: f64 = 0.10;
const EPSILON: f64 = 1e-9;
const MATCH_GATE_WAIT_MS: u64 = 3_000;
const SINGLE_TARGET_TIMEOUT_MS: u64 = 3_000;
const PUBLIC_SAVE_TIMEOUT_MS: u64 = 45_000;
const RESULT_CONFIRM_WINDOW_MS: i64 = 60_000;
const MAX_GAME_STORE_SIZE: usize = 2_048;
const MAX_SNAPSHOT_JSON_BYTES: usize = 256 * 1024;
const MAX_SNAPSHOT_CLUES: usize = 30;
const SINGLE_REQUIRED_CLUES: usize = (ROUND_LIMIT as usize) * 2;
const PUBLIC_REQUIRED_CLUES: usize = (TIE_BREAK_ROUND as usize) * 2;

static GAME_STORE: Lazy<Mutex<GameStore>> = Lazy::new(|| Mutex::new(GameStore::default()));
// 创建对局需要读取存档。串行化该阶段，避免多个客户端同时点击时叠加下载、解压和
// RKS 计算造成进程内存尖峰；底层仍继续使用 `/save` 自身的解密/RKS 信号量。
static MATCH_START_GATE: Lazy<Semaphore> = Lazy::new(|| Semaphore::new(1));

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct MatchRequest {
    mode: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct AnswerRequest {
    answer: f64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum GameMode {
    Single,
    Public,
}

impl GameMode {
    fn parse(value: &str) -> Result<Self, AppError> {
        match value.trim().to_ascii_lowercase().as_str() {
            "single" => Ok(Self::Single),
            "public" => Ok(Self::Public),
            _ => Err(AppError::Validation("小游戏模式无效".into())),
        }
    }

    fn as_str(self) -> &'static str {
        match self {
            Self::Single => "single",
            Self::Public => "public",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RksGuessClue {
    pub slot: String,
    pub song_id: String,
    pub song_name: String,
    pub difficulty: String,
    pub score: u32,
    pub accuracy: f32,
    pub chart_constant: Option<f32>,
    pub rks: f64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RksGuessClueGroup {
    pub round: u8,
    pub clues: Vec<RksGuessClue>,
}

#[derive(Debug, Clone)]
struct PlayerSnapshot {
    user_id: String,
    nickname: String,
    rks: f64,
    clues: Vec<RksGuessClue>,
}

#[derive(Debug, Clone)]
struct SubmittedAnswer {
    value: f64,
    elapsed_ms: i64,
}

#[derive(Debug, Clone)]
struct Round {
    number: u8,
    clues: [Vec<RksGuessClue>; 2],
    started_at_ms: i64,
    deadline_at_ms: i64,
    answers: [Option<SubmittedAnswer>; 2],
}

#[derive(Debug, Clone)]
struct SettledRound {
    number: u8,
    answers: [Option<f64>; 2],
    elapsed_ms: [Option<i64>; 2],
    winner: Option<usize>,
    outcome: String,
    resolution: String,
}

#[derive(Debug)]
struct Game {
    id: String,
    mode: GameMode,
    players: Vec<PlayerSnapshot>,
    status: &'static str,
    created_at_ms: i64,
    round_number: u8,
    current_round: Option<Round>,
    clue_history: [Vec<RksGuessClueGroup>; 2],
    used_clue_slots: [HashSet<String>; 2],
    scores: [u8; 2],
    round_ready: [bool; 2],
    result_deadline_at_ms: Option<i64>,
    last_round: Option<SettledRound>,
    winner: Option<String>,
    finish_reason: Option<String>,
    result_recorded: bool,
}

#[derive(Debug, Default)]
struct GameStore {
    games: std::collections::HashMap<String, Game>,
    waiting: VecDeque<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RksGuessRoundResult {
    pub round: u8,
    pub my_answer: Option<f64>,
    pub opponent_answer: Option<f64>,
    pub my_elapsed_ms: Option<i64>,
    pub opponent_elapsed_ms: Option<i64>,
    pub outcome: String,
    pub resolution: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RksGuessStatus {
    pub game_id: String,
    pub mode: String,
    pub status: String,
    pub role: String,
    pub my_nickname: String,
    pub opponent_ready: bool,
    pub my_round_ready: bool,
    pub opponent_round_ready: bool,
    pub round: u8,
    pub total_rounds: u8,
    pub deadline_at_epoch_ms: Option<i64>,
    pub clues: Vec<RksGuessClue>,
    pub clue_history: Vec<RksGuessClueGroup>,
    pub my_answer: Option<f64>,
    pub opponent_answer: Option<f64>,
    pub opponent_submitted: bool,
    pub my_score: u8,
    pub opponent_score: u8,
    pub last_round: Option<RksGuessRoundResult>,
    pub winner: Option<String>,
    pub finish_reason: Option<String>,
    pub my_rks: Option<f64>,
    pub opponent_rks: Option<f64>,
    pub target_rks: Option<f64>,
}

pub fn create_rks_guess_router() -> Router<AppState> {
    Router::new()
        .route("/games/rks-guess/leaderboard", get(win_leaderboard))
        .route("/games/rks-guess/match", post(start_match))
        .route("/games/rks-guess/:game_id", get(get_game))
        .route("/games/rks-guess/:game_id/answer", post(submit_answer))
        .route("/games/rks-guess/:game_id/next", post(continue_round))
        .route("/games/rks-guess/:game_id/leave", post(leave_game))
}

async fn start_match(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    Json(request): Json<MatchRequest>,
) -> Result<Json<RksGuessStatus>, AppError> {
    let mode = GameMode::parse(&request.mode)?;
    let user_id = require_user_id(&bearer)?;
    let _start_permit = tokio::time::timeout(
        Duration::from_millis(MATCH_GATE_WAIT_MS),
        MATCH_START_GATE.acquire(),
    )
    .await
    .map_err(|_| AppError::Conflict("小游戏开局请求较多，请稍后再试".into()))?
    .map_err(|error| AppError::Internal(format!("小游戏创建队列已关闭: {error}")))?;

    // The gate serializes this check with game creation. Repeated taps reuse the
    // existing game instead of repeatedly loading snapshots and retaining copies.
    let now = now_ms();
    {
        let mut store = GAME_STORE.lock().await;
        persist_finished_wins(&mut store, &state).await?;
        cleanup_store(&mut store, now);
        if let Some((game, role)) = store.games.values().find_map(|game| {
            if game.mode != mode || !matches!(game.status, "waiting" | "playing" | "round_result") {
                return None;
            }
            game.players
                .iter()
                .position(|player| player.user_id == user_id)
                .map(|role| (game, role))
        }) {
            return Ok(Json(render_status(game, role)));
        }
        if store.games.len() >= MAX_GAME_STORE_SIZE {
            return Err(AppError::Conflict(
                "小游戏对局数量已达上限，请稍后再试".into(),
            ));
        }
    }

    let player = if mode == GameMode::Single {
        let (target, nickname) = tokio::join!(
            tokio::time::timeout(
                Duration::from_millis(SINGLE_TARGET_TIMEOUT_MS),
                load_anonymous_single_target(&state, user_id),
            ),
            resolve_player_nickname(&state, &bearer, user_id),
        );
        let mut target =
            target.map_err(|_| AppError::Conflict("匿名题库读取超时，请稍后再试".into()))??;
        target.nickname = nickname;
        target
    } else {
        tokio::time::timeout(
            Duration::from_millis(PUBLIC_SAVE_TIMEOUT_MS),
            load_player(&state, &bearer, user_id),
        )
        .await
        .map_err(|_| AppError::Conflict("玩家存档读取超时，请稍后再试".into()))??
    };
    let now = now_ms();
    let mut store = GAME_STORE.lock().await;
    persist_finished_wins(&mut store, &state).await?;
    cleanup_store(&mut store, now);
    if store.games.len() >= MAX_GAME_STORE_SIZE {
        return Err(AppError::Conflict(
            "小游戏对局数量已达上限，请稍后再试".into(),
        ));
    }

    if mode == GameMode::Single {
        let mut game = Game {
            id: Uuid::new_v4().to_string(),
            mode,
            players: vec![player],
            status: "playing",
            created_at_ms: now,
            round_number: 1,
            current_round: None,
            clue_history: [Vec::new(), Vec::new()],
            used_clue_slots: [HashSet::new(), HashSet::new()],
            scores: [0, 0],
            round_ready: [false, false],
            result_deadline_at_ms: None,
            last_round: None,
            winner: None,
            finish_reason: None,
            result_recorded: false,
        };
        start_new_round(&mut game, now);
        let game_id = game.id.clone();
        store.games.insert(game_id.clone(), game);
        let game = store
            .games
            .get(&game_id)
            .ok_or_else(|| AppError::Internal("创建小游戏失败".into()))?;
        return Ok(Json(render_status(game, 0)));
    }

    if let Some(existing_id) = store.waiting.iter().find(|game_id| {
        store.games.get(*game_id).is_some_and(|game| {
            game.status == "waiting"
                && game
                    .players
                    .first()
                    .is_some_and(|existing| existing.user_id == user_id)
        })
    }) {
        let game = store
            .games
            .get(existing_id)
            .ok_or_else(|| AppError::Internal("匹配队列状态无效".into()))?;
        return Ok(Json(render_status(game, 0)));
    }

    let matching_position = store.waiting.iter().position(|game_id| {
        store.games.get(game_id).is_some_and(|game| {
            game.status == "waiting"
                && game
                    .players
                    .first()
                    .is_some_and(|existing| existing.user_id != user_id)
        })
    });

    if let Some(position) = matching_position {
        let waiting_id = store
            .waiting
            .remove(position)
            .ok_or_else(|| AppError::Internal("匹配队列读取失败".into()))?;
        let game = store
            .games
            .get_mut(&waiting_id)
            .ok_or_else(|| AppError::Internal("匹配对局不存在".into()))?;
        game.players.push(player);
        game.status = "playing";
        game.round_number = 1;
        start_new_round(game, now);
        return Ok(Json(render_status(game, 1)));
    }

    let game = Game {
        id: Uuid::new_v4().to_string(),
        mode,
        players: vec![player],
        status: "waiting",
        created_at_ms: now,
        round_number: 0,
        current_round: None,
        clue_history: [Vec::new(), Vec::new()],
        used_clue_slots: [HashSet::new(), HashSet::new()],
        scores: [0, 0],
        round_ready: [false, false],
        result_deadline_at_ms: None,
        last_round: None,
        winner: None,
        finish_reason: None,
        result_recorded: false,
    };
    let game_id = game.id.clone();
    store.waiting.push_back(game_id.clone());
    store.games.insert(game_id.clone(), game);
    let game = store
        .games
        .get(&game_id)
        .ok_or_else(|| AppError::Internal("创建匹配对局失败".into()))?;
    Ok(Json(render_status(game, 0)))
}

async fn get_game(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    Path(game_id): Path<String>,
) -> Result<Json<RksGuessStatus>, AppError> {
    let user_id = require_user_id(&bearer)?;
    let mut store = GAME_STORE.lock().await;
    let now = now_ms();
    persist_finished_wins(&mut store, &state).await?;
    cleanup_store(&mut store, now);
    let game = store
        .games
        .get_mut(&game_id)
        .ok_or_else(|| AppError::Search(crate::error::SearchError::NotFound))?;
    let role = player_role(game, user_id)?;
    settle_if_expired(game, now);
    persist_and_render(game, role, &state).await
}

async fn submit_answer(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    Path(game_id): Path<String>,
    Json(request): Json<AnswerRequest>,
) -> Result<Json<RksGuessStatus>, AppError> {
    let user_id = require_user_id(&bearer)?;
    let answer = normalize_answer(request.answer)?;
    let mut store = GAME_STORE.lock().await;
    let now = now_ms();
    persist_finished_wins(&mut store, &state).await?;
    cleanup_store(&mut store, now);
    let game = store
        .games
        .get_mut(&game_id)
        .ok_or_else(|| AppError::Search(crate::error::SearchError::NotFound))?;
    let role = player_role(game, user_id)?;
    if game.status != "playing" {
        return Err(AppError::Conflict("当前对局不可作答".into()));
    }
    settle_if_expired(game, now);
    if game.status != "playing" {
        return persist_and_render(game, role, &state).await;
    }
    let round = game
        .current_round
        .as_mut()
        .ok_or_else(|| AppError::Internal("当前回合不存在".into()))?;
    if round.answers[role].is_some() {
        return Err(AppError::Conflict("本回合已经提交过答案".into()));
    }
    if now > round.deadline_at_ms {
        finish_on_timeout(game);
        return persist_and_render(game, role, &state).await;
    }
    round.answers[role] = Some(SubmittedAnswer {
        value: answer,
        elapsed_ms: (now - round.started_at_ms).clamp(0, ANSWER_WINDOW_MS),
    });
    if game.mode == GameMode::Single
        || game.players.len() == 2
            && game
                .current_round
                .as_ref()
                .is_some_and(|r| r.answers.iter().all(Option::is_some))
    {
        settle_current_round(game, now);
    }
    persist_and_render(game, role, &state).await
}

async fn continue_round(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    Path(game_id): Path<String>,
) -> Result<Json<RksGuessStatus>, AppError> {
    let user_id = require_user_id(&bearer)?;
    let mut store = GAME_STORE.lock().await;
    let now = now_ms();
    persist_finished_wins(&mut store, &state).await?;
    cleanup_store(&mut store, now);
    let game = store
        .games
        .get_mut(&game_id)
        .ok_or_else(|| AppError::Search(crate::error::SearchError::NotFound))?;
    let role = player_role(game, user_id)?;
    settle_if_expired(game, now);
    if game.status == "finished" {
        return persist_and_render(game, role, &state).await;
    }
    if game.status != "round_result" {
        return Err(AppError::Conflict("当前不在回合结算页面".into()));
    }
    game.round_ready[role] = true;
    let everyone_ready = game.mode == GameMode::Single
        || game.players.len() == 2 && game.round_ready.iter().all(|ready| *ready);
    if everyone_ready {
        let settled_round = game
            .last_round
            .as_ref()
            .map(|round| round.number)
            .ok_or_else(|| AppError::Internal("上一回合结算不存在".into()))?;
        game.round_number = settled_round.saturating_add(1);
        game.round_ready = [false, false];
        game.result_deadline_at_ms = None;
        game.status = "playing";
        start_new_round(game, now);
    }
    persist_and_render(game, role, &state).await
}

async fn leave_game(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    Path(game_id): Path<String>,
) -> Result<Response, AppError> {
    let user_id = require_user_id(&bearer)?;
    let mut store = GAME_STORE.lock().await;
    let game = store
        .games
        .get_mut(&game_id)
        .ok_or_else(|| AppError::Search(crate::error::SearchError::NotFound))?;
    let role = player_role(game, user_id)?;
    if game.status == "waiting" {
        game.status = "finished";
        game.finish_reason = Some("已取消匹配".into());
        game.winner = Some("none".into());
        store.waiting.retain(|id| id != &game_id);
    } else if matches!(game.status, "playing" | "round_result") {
        game.status = "finished";
        if game.mode == GameMode::Public && game.players.len() == 2 {
            game.winner = Some(if role == 0 { "B" } else { "A" }.into());
            game.finish_reason = Some("对方已退出游戏".into());
        } else {
            game.winner = Some("none".into());
            game.finish_reason = Some("已退出游戏".into());
        }
    }
    persist_finished_wins(&mut store, &state).await?;
    Ok(axum::http::StatusCode::NO_CONTENT.into_response())
}

async fn persist_game_win(game: &mut Game, state: &AppState) -> Result<(), AppError> {
    if game.status != "finished" || game.result_recorded {
        return Ok(());
    }
    let winner = match game.winner.as_deref() {
        Some("A") => game.players.first(),
        Some("B") if game.mode == GameMode::Public => game.players.get(1),
        _ => None,
    };
    if let Some(winner) = winner {
        let storage = state
            .stats_storage
            .as_ref()
            .ok_or_else(|| AppError::Internal("胜场存储不可用".into()))?;
        storage
            .record_rks_guess_win(
                &game.id,
                &winner.user_id,
                game.mode.as_str(),
                &Utc::now().to_rfc3339(),
            )
            .await?;
    }
    game.result_recorded = true;
    Ok(())
}

async fn persist_finished_wins(store: &mut GameStore, state: &AppState) -> Result<(), AppError> {
    let now = now_ms();
    for game in store.games.values_mut() {
        // Settle an abandoned timed-out game before TTL cleanup can remove an earned win.
        if now.saturating_sub(game.created_at_ms) > PLAYING_TTL_MS {
            settle_if_expired(game, now);
        }
        persist_game_win(game, state).await?;
    }
    Ok(())
}

async fn persist_and_render(
    game: &mut Game,
    role: usize,
    state: &AppState,
) -> Result<Json<RksGuessStatus>, AppError> {
    persist_game_win(game, state).await?;
    Ok(Json(render_status(game, role)))
}

async fn win_leaderboard(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
) -> Result<Json<serde_json::Value>, AppError> {
    let user = require_user_id(&bearer)?;
    {
        let mut store = GAME_STORE.lock().await;
        let now = now_ms();
        for game in store.games.values_mut() {
            settle_if_expired(game, now);
        }
        persist_finished_wins(&mut store, &state).await?;
        cleanup_store(&mut store, now);
    }
    let storage = state
        .stats_storage
        .as_ref()
        .ok_or_else(|| AppError::Internal("胜场存储不可用".into()))?;
    Ok(Json(storage.rks_guess_win_leaderboard(user).await?))
}

fn require_user_id(bearer: &BearerAuthState) -> Result<&str, AppError> {
    match bearer {
        BearerAuthState::Valid(context) => Ok(context.claims.sub.as_str()),
        BearerAuthState::Invalid(message) => Err(AppError::Auth(message.clone())),
        BearerAuthState::Absent => Err(AppError::Auth("请先登录".into())),
    }
}

async fn load_player(
    state: &AppState,
    bearer: &BearerAuthState,
    user_id: &str,
) -> Result<PlayerSnapshot, AppError> {
    let (parsed, nickname) = tokio::join!(
        load_parsed_save_for_feature(state, bearer, "cn"),
        resolve_player_nickname(state, bearer, user_id),
    );
    let parsed = parsed?;
    let chart_constants = state.chart_constants.snapshot();
    let known_song_ids = state.song_catalog.snapshot().by_id.keys().cloned().collect::<HashSet<_>>();
    let records = parsed.game_record.iter()
        .filter(|(song_id, _)| known_song_ids.contains(*song_id))
        .map(|(song_id, records)| (song_id.clone(), records.clone()))
        .collect::<std::collections::HashMap<_, _>>();
    let rks_permit = crate::features::save::save_rks_blocking_semaphore()
        .clone()
        .acquire_owned()
        .await
        .map_err(|error| AppError::Internal(format!("小游戏 RKS 计算队列已关闭: {error}")))?;
    let rks = tokio::task::spawn_blocking(move || {
        let _permit = rks_permit;
        calculate_player_rks(&records, &chart_constants)
    })
    .await
    .map_err(|error| AppError::Internal(format!("计算小游戏 RKS 失败: {error}")))?;
    tracing::info!(
        target: "phi_backend::rks_guess::performance",
        phase = "calculate_rks",
        chart_count = rks.b30_charts.len(),
        "RKS guess calculation completed"
    );
    let clues = build_clues(state, parsed.as_ref(), &rks)?;
    if unique_slot_count(&clues) < PUBLIC_REQUIRED_CLUES {
        return Err(AppError::Validation(
            "当前 B30 有效成绩不足 12 条，无法开始公开匹配".into(),
        ));
    }
    Ok(PlayerSnapshot {
        user_id: user_id.to_string(),
        nickname,
        rks: round_to_two(rks.total_rks),
        clues,
    })
}

async fn resolve_player_nickname(
    state: &AppState,
    bearer: &BearerAuthState,
    user_id: &str,
) -> String {
    if let BearerAuthState::Valid(context) = bearer {
        if let Ok(auth) = crate::features::auth::bearer::decode_embedded_auth_with_claims(
            &context.token,
            &context.claims,
        ) {
            if let Some(session_token) = auth.session_token.as_deref() {
                let official = tokio::time::timeout(
                    Duration::from_millis(3_000),
                    crate::features::image::handler::nickname::fetch_nickname(
                        session_token,
                        auth.taptap_version.as_deref(),
                    ),
                )
                .await
                .ok()
                .flatten()
                .map(|name| name.trim().to_string())
                .filter(|name| !name.is_empty());
                if let Some(name) = official {
                    return name;
                }
            }
        }
    }
    if let Some(storage) = state.stats_storage.as_ref() {
        if let Ok(Some(name)) = storage.player_display_name(user_id).await {
            if !name.trim().is_empty() {
                return name;
            }
        }
    }
    "Phigros Player".into()
}

async fn load_anonymous_single_target(
    state: &AppState,
    current_user_id: &str,
) -> Result<PlayerSnapshot, AppError> {
    let storage = state
        .stats_storage
        .as_ref()
        .ok_or_else(|| AppError::Conflict("匿名题库暂不可用，请稍后再试".into()))?;
    let snapshot = storage
        .random_rks_guess_snapshot_excluding(current_user_id)
        .await?
        .ok_or_else(|| AppError::Conflict("匿名题库暂时为空，请稍后再试".into()))?;
    if snapshot.clues_json.len() > MAX_SNAPSHOT_JSON_BYTES
        || !snapshot.total_rks.is_finite()
        || !(0.0..=30.0).contains(&snapshot.total_rks)
    {
        return Err(AppError::Conflict("匿名题库数据无效，请稍后再试".into()));
    }
    let stored_clues = serde_json::from_str::<Vec<RksGuessClue>>(&snapshot.clues_json)
        .map_err(|error| {
            tracing::warn!(target: "phi_backend::rks_guess", "invalid anonymous snapshot JSON: {error}");
            AppError::Conflict("匿名题库数据无效，请稍后再试".into())
        })?;
    let stored_clue_count = stored_clues.len();
    let known_song_ids = state.song_catalog.snapshot().by_id.keys().cloned().collect::<HashSet<_>>();
    let mut clues = stored_clues.into_iter()
        .filter(|clue| known_song_ids.contains(&clue.song_id))
        .collect::<Vec<_>>();
    let target_rks = if clues.len() == stored_clue_count {
        snapshot.total_rks
    } else {
        clues.iter().map(|clue| clue.rks).sum::<f64>() / 30.0
    };
    if !(SINGLE_REQUIRED_CLUES..=MAX_SNAPSHOT_CLUES).contains(&clues.len())
        || unique_slot_count(&clues) < SINGLE_REQUIRED_CLUES
        || clues.iter().any(|clue| !valid_snapshot_clue(clue))
    {
        return Err(AppError::Conflict("匿名题库数据无效，请稍后再试".into()));
    }
    Ok(PlayerSnapshot {
        // 对局归属仍是当前玩家；目标 RKS 和线索来自被抽中的匿名快照。
        user_id: current_user_id.to_string(),
        nickname: "Phigros Player".into(),
        rks: round_to_two(target_rks),
        clues,
    })
}

fn valid_snapshot_clue(clue: &RksGuessClue) -> bool {
    !clue.slot.is_empty()
        && clue.slot.len() <= 8
        && !clue.song_id.is_empty()
        && clue.song_id.len() <= 256
        && !clue.song_name.is_empty()
        && clue.song_name.len() <= 512
        && matches!(clue.difficulty.as_str(), "EZ" | "HD" | "IN" | "AT")
        && clue.score <= 1_000_000
        && clue.accuracy.is_finite()
        && (0.0..=100.0).contains(&clue.accuracy)
        && clue
            .chart_constant
            .is_none_or(|value| value.is_finite() && (0.0..=30.0).contains(&value))
        && clue.rks.is_finite()
        && (0.0..=30.0).contains(&clue.rks)
}

pub(crate) fn build_clues(
    state: &AppState,
    parsed: &ParsedSave,
    rks: &PlayerRksResult,
) -> Result<Vec<RksGuessClue>, AppError> {
    let catalog = state.song_catalog.snapshot();
    let chart_constants = state.chart_constants.snapshot();
    let mut result = Vec::with_capacity(rks.b30_charts.len());
    for (index, ranked) in rks.b30_charts.iter()
        .filter(|ranked| catalog.by_id.contains_key(&ranked.song_id))
        .take(30)
        .enumerate()
    {
        let record = parsed.game_record.get(&ranked.song_id).and_then(|records| {
            records
                .iter()
                .find(|record| record.difficulty == ranked.difficulty)
        });
        let Some(record) = record else { continue };
        result.push(RksGuessClue {
            slot: if index < 27 {
                format!("B{}", index + 1)
            } else {
                format!("P{}", index - 26)
            },
            song_id: ranked.song_id.clone(),
            song_name: catalog
                .by_id
                .get(&ranked.song_id)
                .map_or_else(|| ranked.song_id.clone(), |song| song.name.clone()),
            difficulty: ranked.difficulty.to_string(),
            score: record.score,
            accuracy: record.accuracy,
            chart_constant: chart_constants
                .get(&ranked.song_id)
                .and_then(|constants| match ranked.difficulty {
                    crate::save_contract::Difficulty::EZ => constants.ez,
                    crate::save_contract::Difficulty::HD => constants.hd,
                    crate::save_contract::Difficulty::IN => constants.in_level,
                    crate::save_contract::Difficulty::AT => constants.at,
                }),
            rks: ranked.rks,
        });
    }
    if result.len() < 2 {
        return Err(AppError::Validation(
            "当前 B30 有效成绩不足 2 条，无法开始小游戏".into(),
        ));
    }
    Ok(result)
}

pub(crate) fn serialize_snapshot_clues(
    state: &AppState,
    parsed: &ParsedSave,
    rks: &PlayerRksResult,
) -> Result<String, AppError> {
    let clues = build_clues(state, parsed, rks)?;
    serde_json::to_string(&clues)
        .map_err(|error| AppError::Internal(format!("序列化匿名小游戏题目失败: {error}")))
}

fn new_round(game: &Game, now: i64) -> Round {
    let clues_a = if game.players.len() == 2 {
        choose_two_unseen(&game.players[1].clues, &game.used_clue_slots[0])
    } else {
        choose_two_unseen(&game.players[0].clues, &game.used_clue_slots[0])
    };
    let clues_b = if game.players.len() == 2 {
        choose_two_unseen(&game.players[0].clues, &game.used_clue_slots[1])
    } else {
        Vec::new()
    };
    Round {
        number: game.round_number,
        clues: [clues_a, clues_b],
        started_at_ms: now,
        deadline_at_ms: now + ANSWER_WINDOW_MS,
        answers: [None, None],
    }
}

fn start_new_round(game: &mut Game, now: i64) {
    game.result_deadline_at_ms = None;
    let round = new_round(game, now);
    for role in 0..2 {
        if !round.clues[role].is_empty() {
            game.used_clue_slots[role]
                .extend(round.clues[role].iter().map(|clue| clue.slot.clone()));
            game.clue_history[role].push(RksGuessClueGroup {
                round: round.number,
                clues: round.clues[role].clone(),
            });
        }
    }
    game.current_round = Some(round);
}

fn choose_two_unseen(clues: &[RksGuessClue], used_slots: &HashSet<String>) -> Vec<RksGuessClue> {
    let mut seen_slots = used_slots.clone();
    let mut candidates = clues
        .iter()
        .filter(|clue| seen_slots.insert(clue.slot.clone()))
        .cloned()
        .collect::<Vec<_>>();
    candidates.shuffle(&mut thread_rng());
    candidates.into_iter().take(2).collect()
}

fn unique_slot_count(clues: &[RksGuessClue]) -> usize {
    clues
        .iter()
        .map(|clue| clue.slot.as_str())
        .collect::<HashSet<_>>()
        .len()
}

fn player_role(game: &Game, user_id: &str) -> Result<usize, AppError> {
    game.players
        .iter()
        .position(|player| player.user_id == user_id)
        .ok_or_else(|| AppError::Forbidden("你不属于这个小游戏对局".into()))
}

fn settle_if_expired(game: &mut Game, now: i64) {
    if game.status == "playing"
        && game
            .current_round
            .as_ref()
            .is_some_and(|round| now > round.deadline_at_ms)
    {
        finish_on_timeout(game);
    } else if game.status == "round_result"
        && game.mode == GameMode::Public
        && game
            .result_deadline_at_ms
            .is_some_and(|deadline| now > deadline)
    {
        finish_on_result_confirmation_timeout(game);
    }
}

fn finish_on_result_confirmation_timeout(game: &mut Game) {
    game.status = "finished";
    game.current_round = None;
    game.result_deadline_at_ms = None;
    match game.round_ready {
        [true, false] => {
            game.winner = Some("A".into());
            game.finish_reason = Some("result_confirm_timeout".into());
        }
        [false, true] => {
            game.winner = Some("B".into());
            game.finish_reason = Some("result_confirm_timeout".into());
        }
        _ => {
            game.winner = Some("none".into());
            game.finish_reason = Some("both_result_confirm_timeout".into());
        }
    }
    game.round_ready = [false, false];
}

fn finish_on_timeout(game: &mut Game) {
    let Some(round) = game.current_round.take() else {
        return;
    };
    let answered = [round.answers[0].is_some(), round.answers[1].is_some()];
    game.status = "finished";
    game.round_ready = [false, false];
    if game.mode == GameMode::Single {
        game.winner = Some("none".into());
        game.finish_reason = Some("single_timeout".into());
        return;
    }
    match answered {
        [true, false] => {
            game.winner = Some("A".into());
            game.finish_reason = Some("answer_timeout".into());
        }
        [false, true] => {
            game.winner = Some("B".into());
            game.finish_reason = Some("answer_timeout".into());
        }
        _ => {
            game.winner = Some("none".into());
            game.finish_reason = Some("both_timeout".into());
        }
    }
}

fn settle_current_round(game: &mut Game, now: i64) {
    let Some(round) = game.current_round.take() else {
        return;
    };
    let target_a = if game.players.len() == 2 {
        game.players[1].rks
    } else {
        game.players[0].rks
    };
    let target_b = game.players.first().map_or(0.0, |player| player.rks);
    let targets = [target_a, target_b];
    let answers = [
        round.answers[0].as_ref().map(|answer| answer.value),
        round.answers[1].as_ref().map(|answer| answer.value),
    ];
    let elapsed_ms = [
        round.answers[0].as_ref().map(|answer| answer.elapsed_ms),
        round.answers[1].as_ref().map(|answer| answer.elapsed_ms),
    ];
    let diffs = [
        answers[0].map(|answer| (answer - targets[0]).abs()),
        answers[1].map(|answer| (answer - targets[1]).abs()),
    ];

    let settled = if game.mode == GameMode::Single {
        let won = diffs[0].is_some_and(|diff| diff <= SINGLE_WIN_TOLERANCE + EPSILON);
        if won {
            game.scores[0] = game.scores[0].saturating_add(1);
        }
        SettledRound {
            number: round.number,
            answers,
            elapsed_ms,
            winner: won.then_some(0),
            outcome: if won { "win" } else { "miss" }.into(),
            resolution: "single-tolerance".into(),
        }
    } else {
        settle_public_round(game, round.number, answers, diffs, elapsed_ms)
    };
    let should_finish = if game.mode == GameMode::Single {
        settled.winner.is_some() || round.number >= ROUND_LIMIT
    } else if settled.resolution == "exact" || round.number >= TIE_BREAK_ROUND {
        true
    } else if round.number >= ROUND_LIMIT {
        if game.scores[0] != game.scores[1] {
            true
        } else if settled.outcome == "draw" {
            false
        } else {
            true
        }
    } else {
        false
    };

    game.last_round = Some(settled.clone());
    if should_finish {
        game.status = "finished";
        game.result_deadline_at_ms = None;
        game.current_round = None;
        if game.mode == GameMode::Single {
            game.winner = Some(
                if settled.winner.is_some() {
                    "A"
                } else {
                    "none"
                }
                .into(),
            );
            game.finish_reason = Some(if settled.winner.is_some() {
                "RKS 差值在 0.10 以内".into()
            } else {
                "5 回合内未达到 0.10 以内".into()
            });
        } else {
            game.winner = if settled.resolution == "exact" {
                settled
                    .winner
                    .map_or_else(|| Some("draw".into()), |role| Some(role_name(role)))
            } else {
                match game.scores[0].cmp(&game.scores[1]) {
                    std::cmp::Ordering::Greater => Some("A".into()),
                    std::cmp::Ordering::Less => Some("B".into()),
                    std::cmp::Ordering::Equal => Some("draw".into()),
                }
            };
            game.finish_reason = Some(if settled.resolution == "exact" {
                "命中对方准确 RKS，提前结束".into()
            } else if round.number == TIE_BREAK_ROUND && settled.resolution == "time" {
                "第 6 回合差值相同，按作答耗时判定".into()
            } else if round.number == TIE_BREAK_ROUND {
                "第 6 回合按答案差值判定".into()
            } else {
                "回合结束，分数较高者获胜".into()
            });
        }
    } else {
        game.status = "round_result";
        game.round_ready = [false, false];
        game.result_deadline_at_ms =
            (game.mode == GameMode::Public).then_some(now.saturating_add(RESULT_CONFIRM_WINDOW_MS));
        game.current_round = None;
    }
}

fn settle_public_round(
    game: &mut Game,
    number: u8,
    answers: [Option<f64>; 2],
    diffs: [Option<f64>; 2],
    elapsed_ms: [Option<i64>; 2],
) -> SettledRound {
    let exact_a = answers[0].is_some_and(|answer| (answer - game.players[1].rks).abs() <= EPSILON);
    let exact_b = answers[1].is_some_and(|answer| (answer - game.players[0].rks).abs() <= EPSILON);
    if exact_a || exact_b {
        let winner = match (exact_a, exact_b) {
            (true, false) => Some(0),
            (false, true) => Some(1),
            _ => None,
        };
        if let Some(role) = winner {
            game.scores[role] = game.scores[role].saturating_add(1);
        } else {
            game.scores[0] = game.scores[0].saturating_add(1);
            game.scores[1] = game.scores[1].saturating_add(1);
        }
        return SettledRound {
            number,
            answers,
            elapsed_ms,
            winner,
            outcome: winner.map_or_else(|| "draw".into(), role_name),
            resolution: "exact".into(),
        };
    }

    let (distance_winner, diff_tie) = match (diffs[0], diffs[1]) {
        (None, None) => (None, true),
        (Some(_), None) => (Some(0), false),
        (None, Some(_)) => (Some(1), false),
        (Some(a), Some(b)) if (a - b).abs() <= EPSILON => (None, true),
        (Some(a), Some(b)) if a < b => (Some(0), false),
        (Some(_), Some(_)) => (Some(1), false),
    };
    let winner = if number == TIE_BREAK_ROUND && diff_tie {
        match (elapsed_ms[0], elapsed_ms[1]) {
            (Some(a), Some(b)) if a < b => Some(0),
            (Some(a), Some(b)) if b < a => Some(1),
            _ => None,
        }
    } else {
        distance_winner
    };
    if let Some(role) = winner {
        game.scores[role] = game.scores[role].saturating_add(1);
    } else {
        game.scores[0] = game.scores[0].saturating_add(1);
        game.scores[1] = game.scores[1].saturating_add(1);
    }
    SettledRound {
        number,
        answers,
        elapsed_ms,
        winner,
        outcome: winner.map_or_else(|| "draw".into(), role_name),
        resolution: if number == TIE_BREAK_ROUND && diff_tie {
            "time".into()
        } else {
            "distance".into()
        },
    }
}

fn render_status(game: &Game, role: usize) -> RksGuessStatus {
    let opponent_role = if role == 0 { 1 } else { 0 };
    let current_round = game.current_round.as_ref();
    let my_answer =
        current_round.and_then(|round| round.answers[role].as_ref().map(|answer| answer.value));
    // 当前回合尚未结算时永不返回对手答案；双方提交后会立即结算，
    // 数值只通过 last_round 公开。
    let opponent_answer = None;
    let opponent_submitted = current_round
        .is_some_and(|round| game.players.len() == 2 && round.answers[opponent_role].is_some());
    let last_round = game.last_round.as_ref().map(|result| RksGuessRoundResult {
        round: result.number,
        my_answer: result.answers[role],
        opponent_answer: if game.players.len() == 2 {
            result.answers[opponent_role]
        } else {
            None
        },
        my_elapsed_ms: result.elapsed_ms[role],
        opponent_elapsed_ms: if game.players.len() == 2 {
            result.elapsed_ms[opponent_role]
        } else {
            None
        },
        outcome: if game.mode == GameMode::Single {
            result.outcome.clone()
        } else if result.winner == Some(role) {
            "win".into()
        } else if result.winner.is_some() {
            "lose".into()
        } else {
            "draw".into()
        },
        resolution: result.resolution.clone(),
    });
    let finish_reason = match game.finish_reason.as_deref() {
        Some("single_timeout") => Some("你未在 60 秒内作答，本局判负".into()),
        Some("answer_timeout")
            if game.winner.as_deref() == Some(if role == 0 { "A" } else { "B" }) =>
        {
            Some("对方未在 60 秒内作答，你获得本局胜利".into())
        }
        Some("answer_timeout") => Some("你未在 60 秒内作答，本局判负".into()),
        Some("both_timeout") => Some("双方均未在 60 秒内作答，本局结束".into()),
        Some("result_confirm_timeout")
            if game.winner.as_deref() == Some(if role == 0 { "A" } else { "B" }) =>
        {
            Some("对方未在 60 秒内确认回合结果，你获得本局胜利".into())
        }
        Some("result_confirm_timeout") => Some("你未在 60 秒内确认回合结果，本局判负".into()),
        Some("both_result_confirm_timeout") => {
            Some("双方均未在 60 秒内确认回合结果，本局结束".into())
        }
        _ => game.finish_reason.clone(),
    };
    RksGuessStatus {
        game_id: game.id.clone(),
        mode: game.mode.as_str().into(),
        status: game.status.into(),
        role: role_name(role),
        my_nickname: game.players[role].nickname.clone(),
        opponent_ready: game.mode == GameMode::Single || game.players.len() == 2,
        my_round_ready: game.round_ready[role],
        opponent_round_ready: game.players.len() == 2 && game.round_ready[opponent_role],
        round: game.round_number,
        total_rounds: if game.round_number >= TIE_BREAK_ROUND
            || game.status == "round_result"
                && game.last_round.as_ref().is_some_and(|round| {
                    round.number == ROUND_LIMIT
                        && game.scores[0] == game.scores[1]
                        && round.outcome == "draw"
                }) {
            TIE_BREAK_ROUND
        } else {
            ROUND_LIMIT
        },
        deadline_at_epoch_ms: current_round
            .map(|round| round.deadline_at_ms)
            .or(game.result_deadline_at_ms),
        clues: current_round.map_or_else(Vec::new, |round| round.clues[role].clone()),
        clue_history: game.clue_history[role].clone(),
        my_answer,
        opponent_answer,
        opponent_submitted,
        my_score: game.scores[role],
        opponent_score: if game.players.len() == 2 {
            game.scores[opponent_role]
        } else {
            0
        },
        last_round,
        winner: game.winner.clone(),
        finish_reason,
        my_rks: (game.status == "finished" && game.mode == GameMode::Public)
            .then(|| game.players[role].rks),
        opponent_rks: (game.status == "finished" && game.players.len() == 2)
            .then(|| game.players[opponent_role].rks),
        target_rks: (game.status == "finished" && game.mode == GameMode::Single)
            .then(|| game.players[0].rks),
    }
}

fn cleanup_store(store: &mut GameStore, now: i64) {
    let expired_ids: Vec<String> = store
        .games
        .iter()
        .filter_map(|(id, game)| {
            let ttl = if game.status == "finished" {
                FINISHED_TTL_MS
            } else if game.status == "waiting" {
                WAITING_TTL_MS
            } else if matches!(game.status, "playing" | "round_result") {
                PLAYING_TTL_MS
            } else {
                return None;
            };
            (now.saturating_sub(game.created_at_ms) > ttl).then(|| id.clone())
        })
        .collect();
    for id in expired_ids {
        store.games.remove(&id);
        store.waiting.retain(|waiting_id| waiting_id != &id);
    }
}

fn normalize_answer(value: f64) -> Result<f64, AppError> {
    if !value.is_finite() || !(0.0..=30.0).contains(&value) {
        return Err(AppError::Validation(
            "答案必须是 0.00 至 30.00 之间的数字".into(),
        ));
    }
    let scaled = (value * 100.0).round();
    if (value * 100.0 - scaled).abs() > 1e-6 {
        return Err(AppError::Validation("答案必须精确到小数点后两位".into()));
    }
    Ok(scaled / 100.0)
}

fn round_to_two(value: f64) -> f64 {
    (value * 100.0 + 0.5).floor() / 100.0
}

fn role_name(role: usize) -> String {
    if role == 0 { "A".into() } else { "B".into() }
}

fn now_ms() -> i64 {
    Utc::now().timestamp_millis()
}

#[cfg(test)]
mod tests {
    use std::collections::HashSet;

    use super::{
        Game, GameMode, GameStore, PLAYING_TTL_MS, PlayerSnapshot, RESULT_CONFIRM_WINDOW_MS,
        ROUND_LIMIT, RksGuessClue, Round, SubmittedAnswer, TIE_BREAK_ROUND, cleanup_store,
        finish_on_result_confirmation_timeout, finish_on_timeout, normalize_answer, render_status,
        round_to_two, settle_current_round, settle_public_round, start_new_round,
        valid_snapshot_clue,
    };

    fn player(id: &str, rks: f64) -> PlayerSnapshot {
        PlayerSnapshot {
            user_id: id.into(),
            nickname: format!("Player {id}"),
            rks,
            clues: (1..=12)
                .map(|index| RksGuessClue {
                    slot: format!("B{index}"),
                    song_id: format!("song-{index}"),
                    song_name: format!("Song {index}"),
                    difficulty: if index % 2 == 0 { "AT" } else { "IN" }.into(),
                    score: 1_000_000 - index,
                    accuracy: 100.0 - index as f32 / 10.0,
                    chart_constant: Some(15.0 + index as f32 / 10.0),
                    rks: 15.0 + index as f64 / 100.0,
                })
                .collect(),
        }
    }

    fn public_game(round_number: u8) -> Game {
        Game {
            id: "test".into(),
            mode: GameMode::Public,
            players: vec![player("a", 16.50), player("b", 15.50)],
            status: "playing",
            created_at_ms: 0,
            round_number,
            current_round: None,
            clue_history: [Vec::new(), Vec::new()],
            used_clue_slots: [HashSet::new(), HashSet::new()],
            scores: [0, 0],
            round_ready: [false, false],
            result_deadline_at_ms: None,
            last_round: None,
            winner: None,
            finish_reason: None,
            result_recorded: false,
        }
    }

    #[test]
    fn rks_uses_half_up_rounding_to_two_decimals() {
        assert_eq!(round_to_two(16.498), 16.50);
        assert_eq!(round_to_two(16.494), 16.49);
    }

    #[test]
    fn answer_requires_two_decimal_precision() {
        assert_eq!(normalize_answer(16.5).unwrap(), 16.5);
        assert!(normalize_answer(16.501).is_err());
        assert!(normalize_answer(-0.01).is_err());
    }

    #[test]
    fn invalid_non_finite_snapshot_clue_is_rejected() {
        let mut clue = player("target", 16.0).clues.remove(0);
        clue.rks = f64::NAN;
        assert!(!valid_snapshot_clue(&clue));
    }

    #[test]
    fn rendering_active_single_game_never_indexes_missing_player_b() {
        let mut game = public_game(1);
        game.mode = GameMode::Single;
        game.players.truncate(1);

        let status = render_status(&game, 0);

        assert_eq!(status.mode, "single");
        assert_eq!(status.opponent_rks, None);
        assert_eq!(status.target_rks, None);
    }

    #[test]
    fn abandoned_playing_game_is_removed_after_ttl() {
        let mut store = GameStore::default();
        let mut game = public_game(1);
        game.created_at_ms = 100;
        store.games.insert(game.id.clone(), game);

        cleanup_store(&mut store, 100 + PLAYING_TTL_MS + 1);

        assert!(store.games.is_empty());
    }

    #[test]
    fn result_confirmation_timeout_awards_ready_player() {
        let mut game = public_game(1);
        game.status = "round_result";
        game.round_ready = [true, false];
        game.result_deadline_at_ms = Some(RESULT_CONFIRM_WINDOW_MS);

        finish_on_result_confirmation_timeout(&mut game);

        assert_eq!(game.status, "finished");
        assert_eq!(game.winner.as_deref(), Some("A"));
        assert_eq!(
            game.finish_reason.as_deref(),
            Some("result_confirm_timeout")
        );
        assert_eq!(game.result_deadline_at_ms, None);
    }

    #[test]
    fn both_players_missing_confirmation_has_no_winner() {
        let mut game = public_game(1);
        game.status = "round_result";
        game.round_ready = [false, false];

        finish_on_result_confirmation_timeout(&mut game);

        assert_eq!(game.winner.as_deref(), Some("none"));
        assert_eq!(
            game.finish_reason.as_deref(),
            Some("both_result_confirm_timeout")
        );
    }

    #[test]
    fn equal_distance_awards_both_players() {
        let mut game = public_game(1);
        let result = settle_public_round(
            &mut game,
            1,
            [Some(15.0), Some(16.0)],
            [Some(0.5), Some(0.5)],
            [Some(1000), Some(1200)],
        );
        assert_eq!(result.outcome, "draw");
        assert_eq!(game.scores, [1, 1]);
    }

    #[test]
    fn sixth_round_uses_shorter_answer_time_when_distance_ties() {
        let mut game = public_game(TIE_BREAK_ROUND);
        game.scores = [2, 2];
        let result = settle_public_round(
            &mut game,
            TIE_BREAK_ROUND,
            [Some(15.0), Some(16.0)],
            [Some(0.5), Some(0.5)],
            [Some(900), Some(1200)],
        );
        assert_eq!(result.winner, Some(0));
        assert_eq!(game.scores, [3, 2]);
        assert_eq!(ROUND_LIMIT, 5);
    }

    #[test]
    fn both_timeouts_are_a_draw_instead_of_player_b_win() {
        let mut game = public_game(1);
        let result = settle_public_round(&mut game, 1, [None, None], [None, None], [None, None]);
        assert_eq!(result.winner, None);
        assert_eq!(result.outcome, "draw");
        assert_eq!(game.scores, [1, 1]);
    }

    #[test]
    fn answered_player_wins_when_only_opponent_times_out() {
        let mut game = public_game(1);
        let result = settle_public_round(
            &mut game,
            1,
            [Some(14.0), None],
            [Some(1.5), None],
            [Some(1_000), None],
        );
        assert_eq!(result.winner, Some(0));
        assert_eq!(game.scores, [1, 0]);
    }

    #[test]
    fn exact_hit_directly_decides_match_even_when_trailing_on_points() {
        let mut game = public_game(1);
        game.scores = [0, 4];
        game.current_round = Some(Round {
            number: 1,
            clues: [Vec::new(), Vec::new()],
            started_at_ms: 0,
            deadline_at_ms: 60_000,
            answers: [
                Some(SubmittedAnswer {
                    value: 15.50,
                    elapsed_ms: 1_000,
                }),
                Some(SubmittedAnswer {
                    value: 15.00,
                    elapsed_ms: 1_200,
                }),
            ],
        });
        settle_current_round(&mut game, 1_200);
        assert_eq!(game.status, "finished");
        assert_eq!(game.winner.as_deref(), Some("A"));
        assert_eq!(
            game.finish_reason.as_deref(),
            Some("命中对方准确 RKS，提前结束")
        );
    }

    #[test]
    fn active_round_hides_opponent_answer_but_reports_submission_state() {
        let mut game = public_game(1);
        game.current_round = Some(Round {
            number: 1,
            clues: [Vec::new(), Vec::new()],
            started_at_ms: 0,
            deadline_at_ms: 60_000,
            answers: [
                None,
                Some(SubmittedAnswer {
                    value: 16.0,
                    elapsed_ms: 800,
                }),
            ],
        });
        let status = render_status(&game, 0);
        assert_eq!(status.opponent_answer, None);
        assert!(status.opponent_submitted);
    }

    #[test]
    fn public_status_does_not_expose_opponent_nickname() {
        let game = public_game(1);
        let status = serde_json::to_value(render_status(&game, 0)).unwrap();

        assert_eq!(status["myNickname"], "Player a");
        assert!(status.get("opponentNickname").is_none());
    }

    #[test]
    fn settled_non_final_round_waits_on_result_page() {
        let mut game = public_game(1);
        game.current_round = Some(Round {
            number: 1,
            clues: [Vec::new(), Vec::new()],
            started_at_ms: 0,
            deadline_at_ms: 60_000,
            answers: [
                Some(SubmittedAnswer {
                    value: 15.0,
                    elapsed_ms: 1_000,
                }),
                Some(SubmittedAnswer {
                    value: 16.2,
                    elapsed_ms: 1_200,
                }),
            ],
        });

        settle_current_round(&mut game, 1_200);

        assert_eq!(game.status, "round_result");
        assert_eq!(game.round_number, 1);
        assert!(game.current_round.is_none());
        assert!(game.last_round.is_some());
        let public_json = serde_json::to_value(render_status(&game, 0)).unwrap();
        let result_json = &public_json["lastRound"];
        assert_eq!(result_json["myAnswer"], 15.0);
        assert_eq!(result_json["opponentAnswer"], 16.2);
        assert!(result_json.get("myDiff").is_none());
        assert!(result_json.get("opponentDiff").is_none());
    }

    #[test]
    fn timeout_finishes_match_instead_of_starting_next_round() {
        let mut game = public_game(1);
        game.current_round = Some(Round {
            number: 1,
            clues: [Vec::new(), Vec::new()],
            started_at_ms: 0,
            deadline_at_ms: 60_000,
            answers: [
                Some(SubmittedAnswer {
                    value: 15.0,
                    elapsed_ms: 1_000,
                }),
                None,
            ],
        });

        finish_on_timeout(&mut game);

        assert_eq!(game.status, "finished");
        assert_eq!(game.winner.as_deref(), Some("A"));
        assert!(game.current_round.is_none());
        let winner_status = render_status(&game, 0);
        let loser_status = render_status(&game, 1);
        assert!(
            winner_status
                .finish_reason
                .as_deref()
                .is_some_and(|reason| reason.contains("60 秒"))
        );
        assert!(
            loser_status
                .finish_reason
                .as_deref()
                .is_some_and(|reason| reason.contains("判负"))
        );
    }

    #[test]
    fn sixth_round_reports_six_total_rounds_and_new_clues_append_to_history() {
        let mut game = public_game(TIE_BREAK_ROUND);
        start_new_round(&mut game, 0);
        assert_eq!(game.clue_history[0].len(), 1);
        assert_eq!(game.clue_history[0][0].clues.len(), 2);
        let status = render_status(&game, 0);
        assert_eq!(status.total_rounds, TIE_BREAK_ROUND);
        assert_eq!(status.clue_history.len(), 1);
    }

    #[test]
    fn clue_slots_never_repeat_across_all_six_rounds() {
        let mut game = public_game(1);

        for round_number in 1..=TIE_BREAK_ROUND {
            game.round_number = round_number;
            start_new_round(&mut game, i64::from(round_number) * 1_000);
        }

        for role in 0..2 {
            let slots = game.clue_history[role]
                .iter()
                .flat_map(|group| group.clues.iter().map(|clue| clue.slot.as_str()))
                .collect::<Vec<_>>();
            let unique_slots = slots.iter().copied().collect::<HashSet<_>>();
            assert_eq!(slots.len(), 12);
            assert_eq!(unique_slots.len(), slots.len());
        }
    }
}
