use axum::{
    Json,
    extract::{Query, State},
    http::HeaderMap,
};
use serde::{Deserialize, Serialize};

use crate::{
    error::AppError, features::leaderboard::handler::admin::require_admin, state::AppState,
};

use super::{
    DailyDauRow, DailyHttpTotalRow,
    queries::{query_daily_dau, query_daily_http},
    time::{parse_ymd, resolve_timezone, validate_date_range},
};

#[derive(Debug, Deserialize, utoipa::IntoParams)]
pub struct AdminDashboardQuery {
    start: String,
    end: String,
    /// 可选时区（IANA 名称，如 Asia/Shanghai），覆盖配置
    timezone: Option<String>,
}

#[derive(Serialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct AdminDashboardResponse {
    checkin_trend: Vec<serde_json::Value>,
    timezone: String,
    start: String,
    end: String,
    total_users: i64,
    user_count_trend: Vec<AdminUserCountTrendRow>,
    dau: Vec<DailyDauRow>,
    http_totals: Vec<DailyHttpTotalRow>,
}

#[derive(Debug, Serialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct AdminUserCountTrendRow {
    date: String,
    total_users: i64,
}

#[utoipa::path(
    get,
    path = "/admin/dashboard",
    summary = "读取管理后台汇总数据",
    description = "使用管理员令牌一次读取排行榜总用户数、DAU 与 HTTP 请求统计。",
    params(AdminDashboardQuery),
    security(("AdminToken" = [])),
    responses(
        (status = 200, description = "管理后台汇总数据", body = AdminDashboardResponse),
        (status = 401, description = "管理员令牌缺失或无效", body = crate::error::ProblemDetails),
        (status = 422, description = "参数校验失败", body = crate::error::ProblemDetails),
        (status = 500, description = "统计查询失败", body = crate::error::ProblemDetails)
    ),
    tag = "Stats"
)]
pub async fn get_admin_dashboard(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(query): Query<AdminDashboardQuery>,
) -> Result<Json<AdminDashboardResponse>, AppError> {
    let config = crate::config::AppConfig::global();
    require_admin(&headers)?;

    let start = parse_ymd(&query.start, "start")?;
    let end = parse_ymd(&query.end, "end")?;
    validate_date_range(start, end)?;
    let (timezone, tz) =
        resolve_timezone(config.stats.timezone.as_str(), query.timezone.as_deref())?;
    let storage = state
        .stats_storage
        .as_ref()
        .ok_or_else(|| AppError::Internal("统计存储未初始化".into()))?;

    let (total_users, user_count_trend) = tokio::try_join!(
        storage.query_admin_leaderboard_users_count(None, None),
        storage.query_leaderboard_user_count_trend(start, end, tz),
    )?;
    let user_count_trend = user_count_trend
        .into_iter()
        .map(|(date, total_users)| AdminUserCountTrendRow { date, total_users })
        .collect();
    let (dau, (http_totals, _)) = tokio::try_join!(
        query_daily_dau(storage, tz, start, end),
        query_daily_http(storage, tz, start, end, None, None, 1),
    )?;

    let checkin_trend = storage.checkin_trend(&query.start, &query.end).await?;
    Ok(Json(AdminDashboardResponse {
        checkin_trend,
        timezone,
        start: query.start,
        end: query.end,
        total_users,
        user_count_trend,
        dau,
        http_totals,
    }))
}
