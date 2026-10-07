use axum::{
    Json, Router,
    body::Body,
    extract::{DefaultBodyLimit, Path, Query, State},
    http::{HeaderMap, HeaderValue, header},
    response::{IntoResponse, Response},
    routing::{get, post},
};
use base64::{Engine as _, engine::general_purpose::STANDARD};
use serde::{Deserialize, Serialize};
use sqlx::{Row, SqlitePool};

use crate::{
    auth_contract::UnifiedSaveRequest,
    error::AppError,
    identity_hash::derive_user_identity_from_auth,
    state::AppState,
};

const PUBLIC_HIDDEN: &str = "public_hidden";
const ACCOUNT_SUSPENDED: &str = "account_suspended";

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/users/me/moderation/status", post(user_status))
        .route("/users/me/moderation/appeals", post(submit_appeal))
        .route("/admin/users/management/search", get(admin_search_users))
        .route("/admin/users/management/restricted", get(admin_list_restricted_users))
        .route(
            "/admin/users/management/restriction",
            post(admin_set_restriction),
        )
        .route("/admin/users/management/appeals", get(admin_list_appeals))
        .route(
            "/admin/users/management/appeals/:id",
            post(admin_review_appeal),
        )
        .route(
            "/admin/users/management/appeals/:id/images/:position",
            get(admin_appeal_image),
        )
        .layer(DefaultBodyLimit::max(5 * 1024 * 1024))
}

fn pool(state: &AppState) -> Result<&SqlitePool, AppError> {
    state
        .stats_storage
        .as_ref()
        .map(|storage| &storage.pool)
        .ok_or_else(|| AppError::Internal("统计存储未初始化".into()))
}

fn db(error: sqlx::Error) -> AppError {
    AppError::Internal(format!("user moderation storage: {error}"))
}

fn admin(headers: &HeaderMap) -> Result<String, AppError> {
    let provided = headers
        .get("x-admin-token")
        .and_then(|value| value.to_str().ok())
        .unwrap_or("")
        .trim();
    if provided.is_empty() {
        return Err(AppError::Auth("缺少管理员令牌".into()));
    }
    if !crate::config::AppConfig::global()
        .leaderboard
        .admin_tokens
        .iter()
        .any(|token| token.trim() == provided)
    {
        return Err(AppError::Auth("管理员令牌无效".into()));
    }
    Ok(format!("admin:{}", crate::identity_hash::hmac_hex16("moderation-audit", provided)))
}

fn session_hash(session_token: &str) -> Result<String, AppError> {
    let token = session_token.trim();
    if token.is_empty() || token.len() > 4096 {
        return Err(AppError::Auth("SessionToken 无效".into()));
    }
    let salt = crate::config::AppConfig::global()
        .stats
        .user_hash_salt
        .clone()
        .or_else(|| std::env::var("APP_STATS_USER_HASH_SALT").ok())
        .filter(|value| !value.trim().is_empty())
        .ok_or_else(|| AppError::Internal("stats.user_hash_salt 未配置".into()))?;
    let auth = UnifiedSaveRequest {
        session_token: Some(token.to_owned()),
        external_credentials: None,
        taptap_version: Some("cn".into()),
    };
    derive_user_identity_from_auth(Some(&salt), &auth)
        .0
        .ok_or_else(|| AppError::Auth("无法识别用户".into()))
}

fn avatar_is_official(value: Option<String>) -> Option<String> {
    let value = value?.trim().to_owned();
    include_str!("../../info/avatar.txt")
        .lines()
        .any(|candidate| candidate.trim() == value)
        .then_some(value)
}

fn restriction_type(value: &str) -> Result<&'static str, AppError> {
    match value.trim() {
        PUBLIC_HIDDEN => Ok(PUBLIC_HIDDEN),
        ACCOUNT_SUSPENDED => Ok(ACCOUNT_SUSPENDED),
        _ => Err(AppError::Validation(
            "restrictionType 必须为 public_hidden 或 account_suspended".into(),
        )),
    }
}

fn active_restriction(row: &sqlx::sqlite::SqliteRow, prefix: &str) -> RestrictionState {
    let active: i64 = row.try_get(format!("{prefix}_active").as_str()).unwrap_or(0);
    RestrictionState {
        active: active != 0,
        reason: row
            .try_get(format!("{prefix}_reason").as_str())
            .unwrap_or(None),
        expires_at: row
            .try_get(format!("{prefix}_expires_at").as_str())
            .unwrap_or(None),
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct RestrictionState {
    active: bool,
    reason: Option<String>,
    expires_at: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct UserModerationStatus {
    public_hidden: bool,
    public_reason: Option<String>,
    public_expires_at: Option<String>,
    account_suspended: bool,
    account_reason: Option<String>,
    account_expires_at: Option<String>,
    public_appeal_status: Option<String>,
    public_appeal_reply: Option<String>,
    account_appeal_status: Option<String>,
    account_appeal_reply: Option<String>,
}

async fn query_user_status(pool: &SqlitePool, user_hash: &str) -> Result<UserModerationStatus, AppError> {
    let row = sqlx::query(
        "SELECT
           CASE WHEN ph.user_hash IS NOT NULL THEN 1 ELSE 0 END AS public_active,
           ph.reason AS public_reason, ph.expires_at AS public_expires_at,
           CASE WHEN su.user_hash IS NOT NULL THEN 1 ELSE 0 END AS account_active,
           su.reason AS account_reason, su.expires_at AS account_expires_at,
           (SELECT status FROM moderation_appeals ma WHERE ma.user_hash=? AND ma.restriction_type='public_hidden'
             ORDER BY ma.created_at DESC LIMIT 1) AS public_appeal_status,
           (SELECT admin_reply FROM moderation_appeals ma WHERE ma.user_hash=? AND ma.restriction_type='public_hidden'
             ORDER BY ma.created_at DESC LIMIT 1) AS public_appeal_reply,
           (SELECT status FROM moderation_appeals ma WHERE ma.user_hash=? AND ma.restriction_type='account_suspended'
             ORDER BY ma.created_at DESC LIMIT 1) AS account_appeal_status,
           (SELECT admin_reply FROM moderation_appeals ma WHERE ma.user_hash=? AND ma.restriction_type='account_suspended'
             ORDER BY ma.created_at DESC LIMIT 1) AS account_appeal_reply
         FROM (SELECT 1) seed
         LEFT JOIN user_access_restrictions ph ON ph.user_hash=?
           AND ph.restriction_type='public_hidden' AND ph.revoked_at IS NULL
           AND (ph.expires_at IS NULL OR julianday(ph.expires_at)>julianday('now'))
         LEFT JOIN user_access_restrictions su ON su.user_hash=?
           AND su.restriction_type='account_suspended' AND su.revoked_at IS NULL
           AND (su.expires_at IS NULL OR julianday(su.expires_at)>julianday('now'))",
    )
    .bind(user_hash)
    .bind(user_hash)
    .bind(user_hash)
    .bind(user_hash)
    .bind(user_hash)
    .bind(user_hash)
    .fetch_one(pool)
    .await
    .map_err(db)?;
    Ok(UserModerationStatus {
        public_hidden: active_restriction(&row, "public").active,
        public_reason: row.try_get("public_reason").unwrap_or(None),
        public_expires_at: row.try_get("public_expires_at").unwrap_or(None),
        account_suspended: active_restriction(&row, "account").active,
        account_reason: row.try_get("account_reason").unwrap_or(None),
        account_expires_at: row.try_get("account_expires_at").unwrap_or(None),
        public_appeal_status: row.try_get("public_appeal_status").unwrap_or(None),
        public_appeal_reply: row.try_get("public_appeal_reply").unwrap_or(None),
        account_appeal_status: row.try_get("account_appeal_status").unwrap_or(None),
        account_appeal_reply: row.try_get("account_appeal_reply").unwrap_or(None),
    })
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SessionTokenRequest {
    session_token: String,
}

async fn user_status(
    State(state): State<AppState>,
    Json(request): Json<SessionTokenRequest>,
) -> Result<Json<UserModerationStatus>, AppError> {
    let user_hash = session_hash(&request.session_token)?;
    Ok(Json(query_user_status(pool(&state)?, &user_hash).await?))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct AppealRequest {
    session_token: String,
    restriction_type: String,
    body: String,
    #[serde(default)]
    images: Vec<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AppealCreated {
    id: String,
    status: String,
    created_at: String,
}

fn validate_appeal_text(value: &str) -> Result<String, AppError> {
    let value = value.trim();
    if value.is_empty() || value.chars().count() > 4000 {
        return Err(AppError::Validation("申诉说明需填写，最多 4000 字".into()));
    }
    if value.chars().any(|character| {
        character.is_control() && character != '\n' && character != '\r' && character != '\t'
    }) {
        return Err(AppError::Validation("申诉说明包含无效字符".into()));
    }
    Ok(value.to_owned())
}

fn validate_images(encoded_images: Vec<String>) -> Result<Vec<(&'static str, Vec<u8>)>, AppError> {
    if encoded_images.len() > 3 {
        return Err(AppError::Validation("最多添加 3 张图片".into()));
    }
    encoded_images
        .into_iter()
        .map(|encoded| {
            if encoded.len() > 1_400_000 {
                return Err(AppError::Validation("单张图片不能超过 1 MB".into()));
            }
            let bytes = STANDARD
                .decode(encoded)
                .map_err(|_| AppError::Validation("无效图片".into()))?;
            if bytes.len() > 1024 * 1024 {
                return Err(AppError::Validation("单张图片不能超过 1 MB".into()));
            }
            let format = image::guess_format(&bytes)
                .map_err(|_| AppError::Validation("无效图片".into()))?;
            let mime = match format {
                image::ImageFormat::Jpeg => "image/jpeg",
                image::ImageFormat::Png => "image/png",
                image::ImageFormat::WebP => "image/webp",
                _ => return Err(AppError::Validation("仅支持 JPG、PNG、WebP".into())),
            };
            let (width, height) = image::ImageReader::with_format(std::io::Cursor::new(&bytes), format)
                .into_dimensions()
                .map_err(|_| AppError::Validation("图片已损坏".into()))?;
            if width == 0 || height == 0 || u64::from(width) * u64::from(height) > 16_000_000 {
                return Err(AppError::Validation("图片尺寸过大".into()));
            }
            Ok((mime, bytes))
        })
        .collect()
}

async fn submit_appeal(
    State(state): State<AppState>,
    Json(request): Json<AppealRequest>,
) -> Result<Json<AppealCreated>, AppError> {
    let user_hash = session_hash(&request.session_token)?;
    let restriction = restriction_type(&request.restriction_type)?;
    let body = validate_appeal_text(&request.body)?;
    let images = validate_images(request.images)?;
    let pool = pool(&state)?;
    let mut transaction = pool.begin().await.map_err(db)?;
    let active: i64 = sqlx::query_scalar(
        "SELECT COUNT(1) FROM user_access_restrictions
         WHERE user_hash=? AND restriction_type=? AND revoked_at IS NULL
           AND (expires_at IS NULL OR julianday(expires_at)>julianday('now'))",
    )
    .bind(&user_hash)
    .bind(restriction)
    .fetch_one(&mut *transaction)
    .await
    .map_err(db)?;
    if active == 0 {
        return Err(AppError::Conflict("当前没有这项限制，可刷新页面查看状态".into()));
    }
    let pending: i64 = sqlx::query_scalar(
        "SELECT COUNT(1) FROM moderation_appeals
         WHERE user_hash=? AND restriction_type=? AND status IN ('pending','reviewing')",
    )
    .bind(&user_hash)
    .bind(restriction)
    .fetch_one(&mut *transaction)
    .await
    .map_err(db)?;
    if pending > 0 {
        return Err(AppError::Conflict("这项限制已有一份待处理申诉".into()));
    }

    let id = uuid::Uuid::new_v4().to_string();
    let now = chrono::Utc::now().to_rfc3339();
    sqlx::query(
        "INSERT INTO moderation_appeals(id,user_hash,restriction_type,body,status,admin_reply,created_at,updated_at)
         VALUES(?,?,?,?,'pending','',?,?)",
    )
    .bind(&id)
    .bind(&user_hash)
    .bind(restriction)
    .bind(body)
    .bind(&now)
    .bind(&now)
    .execute(&mut *transaction)
    .await
    .map_err(|error| {
        if error.as_database_error().is_some_and(|db| db.is_unique_violation()) {
            AppError::Conflict("这项限制已有一份待处理申诉".into())
        } else { db(error) }
    })?;
    for (position, (mime, bytes)) in images.into_iter().enumerate() {
        sqlx::query("INSERT INTO moderation_appeal_images(appeal_id,position,mime,data) VALUES(?,?,?,?)")
            .bind(&id)
            .bind(position as i64)
            .bind(mime)
            .bind(bytes)
            .execute(&mut *transaction)
            .await
            .map_err(db)?;
    }
    transaction.commit().await.map_err(db)?;
    Ok(Json(AppealCreated { id, status: "pending".into(), created_at: now }))
}

#[derive(Debug, Deserialize)]
struct SearchQuery {
    query: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AdminRestriction {
    active: bool,
    reason: Option<String>,
    expires_at: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AdminUser {
    user_hash: String,
    alias: Option<String>,
    nickname: Option<String>,
    avatar: Option<String>,
    rks: f64,
    challenge_mode_rank: Option<i64>,
    leaderboard_rank: Option<i64>,
    public_hidden: AdminRestriction,
    account_suspended: AdminRestriction,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct SearchResponse {
    items: Vec<AdminUser>,
}

fn admin_user_from_row(row: sqlx::sqlite::SqliteRow) -> AdminUser {
    AdminUser {
        user_hash: row.try_get("user_hash").unwrap_or_default(),
        alias: row.try_get("alias").unwrap_or(None),
        nickname: row.try_get("nickname").unwrap_or(None),
        avatar: avatar_is_official(row.try_get("avatar").unwrap_or(None)),
        rks: row.try_get("rks").unwrap_or(0.0),
        challenge_mode_rank: row.try_get("challenge_mode_rank").unwrap_or(None),
        leaderboard_rank: row.try_get("leaderboard_rank").unwrap_or(None),
        public_hidden: AdminRestriction {
            active: row.try_get::<i64, _>("public_active").unwrap_or(0) != 0,
            reason: row.try_get("public_reason").unwrap_or(None),
            expires_at: row.try_get("public_expires_at").unwrap_or(None),
        },
        account_suspended: AdminRestriction {
            active: row.try_get::<i64, _>("account_active").unwrap_or(0) != 0,
            reason: row.try_get("account_reason").unwrap_or(None),
            expires_at: row.try_get("account_expires_at").unwrap_or(None),
        },
    }
}

async fn admin_search_users(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(query): Query<SearchQuery>,
) -> Result<Json<SearchResponse>, AppError> {
    admin(&headers)?;
    let search = query.query.trim();
    if search.is_empty() || search.chars().count() > 40 {
        return Err(AppError::Validation("请输入 1–40 个字符的用户名".into()));
    }
    let escaped = search.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_");
    let pattern = format!("%{escaped}%");
    let rows = sqlx::query(
        "SELECT up.user_hash, up.alias, up.nickname, up.avatar, up.challenge_mode_rank,
           COALESCE(lr.total_rks,0.0) AS rks,
           CASE WHEN lr.user_hash IS NOT NULL AND up.is_public=1 AND lr.is_hidden=0
             AND NOT EXISTS (SELECT 1 FROM publicly_restricted_users px WHERE px.user_hash=lr.user_hash)
             THEN 1 + (SELECT COUNT(1) FROM leaderboard_rks ahead
               JOIN user_profile ap ON ap.user_hash=ahead.user_hash AND ap.is_public=1
               WHERE ahead.is_hidden=0 AND NOT EXISTS (SELECT 1 FROM publicly_restricted_users ax
                 WHERE ax.user_hash=ahead.user_hash)
                 AND (ahead.total_rks>lr.total_rks OR (ahead.total_rks=lr.total_rks AND
                   (ahead.updated_at<lr.updated_at OR (ahead.updated_at=lr.updated_at AND ahead.user_hash<lr.user_hash)))))
             ELSE NULL END AS leaderboard_rank,
           CASE WHEN ph.user_hash IS NOT NULL THEN 1 ELSE 0 END AS public_active,
           ph.reason AS public_reason, ph.expires_at AS public_expires_at,
           CASE WHEN su.user_hash IS NOT NULL THEN 1 ELSE 0 END AS account_active,
           su.reason AS account_reason, su.expires_at AS account_expires_at
         FROM user_profile up LEFT JOIN leaderboard_rks lr ON lr.user_hash=up.user_hash
         LEFT JOIN user_access_restrictions ph ON ph.user_hash=up.user_hash
           AND ph.restriction_type='public_hidden' AND ph.revoked_at IS NULL
           AND (ph.expires_at IS NULL OR julianday(ph.expires_at)>julianday('now'))
         LEFT JOIN user_access_restrictions su ON su.user_hash=up.user_hash
           AND su.restriction_type='account_suspended' AND su.revoked_at IS NULL
           AND (su.expires_at IS NULL OR julianday(su.expires_at)>julianday('now'))
         WHERE up.alias LIKE ? ESCAPE '\\' COLLATE NOCASE OR up.nickname LIKE ? ESCAPE '\\' COLLATE NOCASE
         ORDER BY rks DESC, up.alias COLLATE NOCASE LIMIT 40",
    )
    .bind(&pattern)
    .bind(&pattern)
    .fetch_all(pool(&state)?)
    .await
    .map_err(db)?;
    let items = rows
        .into_iter()
        .map(admin_user_from_row)
        .collect();
    Ok(Json(SearchResponse { items }))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RestrictedUsersQuery {
    restriction_type: Option<String>,
    query: Option<String>,
    page: Option<u32>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct RestrictionCounts {
    all: i64,
    public_hidden: i64,
    account_suspended: i64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct RestrictedUsersResponse {
    items: Vec<AdminUser>,
    total: i64,
    page: u32,
    page_size: u32,
    counts: RestrictionCounts,
}

// Both queries use one database snapshot and expiry cutoff. The primary key on
// (user_hash, restriction_type) keeps accounts with two restrictions in one row.
const RESTRICTED_USERS_FROM: &str =
    "FROM user_profile up
     LEFT JOIN user_access_restrictions ph ON ph.user_hash=up.user_hash
       AND ph.restriction_type='public_hidden' AND ph.revoked_at IS NULL
       AND (ph.expires_at IS NULL OR julianday(ph.expires_at)>julianday(?))
     LEFT JOIN user_access_restrictions su ON su.user_hash=up.user_hash
       AND su.restriction_type='account_suspended' AND su.revoked_at IS NULL
       AND (su.expires_at IS NULL OR julianday(su.expires_at)>julianday(?))
     LEFT JOIN leaderboard_rks lr ON lr.user_hash=up.user_hash
     WHERE (ph.user_hash IS NOT NULL OR su.user_hash IS NOT NULL)
       AND (?='' OR up.alias LIKE ? ESCAPE '\\' COLLATE NOCASE
         OR up.nickname LIKE ? ESCAPE '\\' COLLATE NOCASE)";

async fn admin_list_restricted_users(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(query): Query<RestrictedUsersQuery>,
) -> Result<Json<RestrictedUsersResponse>, AppError> {
    admin(&headers)?;
    let filter = query.restriction_type.as_deref().unwrap_or("all").trim();
    if filter != "all" {
        restriction_type(filter)?;
    }
    let search = query.query.as_deref().unwrap_or("").trim();
    if search.chars().count() > 40 {
        return Err(AppError::Validation("用户名最多 40 个字符".into()));
    }
    let escaped = search.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_");
    let pattern = format!("%{escaped}%");
    let now = chrono::Utc::now().to_rfc3339();
    let mut transaction = pool(&state)?.begin().await.map_err(db)?;
    let count_sql = format!(
        "SELECT COUNT(1) AS total,
           COALESCE(SUM(CASE WHEN ph.user_hash IS NOT NULL THEN 1 ELSE 0 END),0) AS public_hidden,
           COALESCE(SUM(CASE WHEN su.user_hash IS NOT NULL THEN 1 ELSE 0 END),0) AS account_suspended
         {RESTRICTED_USERS_FROM}"
    );
    let row = sqlx::query(&count_sql)
        .bind(&now)
        .bind(&now)
        .bind(search)
        .bind(&pattern)
        .bind(&pattern)
        .fetch_one(&mut *transaction)
        .await
        .map_err(db)?;
    let counts = RestrictionCounts {
        all: row.try_get("total").map_err(db)?,
        public_hidden: row.try_get("public_hidden").map_err(db)?,
        account_suspended: row.try_get("account_suspended").map_err(db)?,
    };
    let total = match filter {
        PUBLIC_HIDDEN => counts.public_hidden,
        ACCOUNT_SUSPENDED => counts.account_suspended,
        _ => counts.all,
    };
    let page_size = 20;
    let page_count = ((total + i64::from(page_size) - 1) / i64::from(page_size)).max(1);
    let page = query.page.unwrap_or(1).clamp(1, page_count.min(u32::MAX as i64) as u32);
    let list_sql = format!(
        "SELECT up.user_hash, up.alias, up.nickname, up.avatar, up.challenge_mode_rank,
           COALESCE(lr.total_rks,0.0) AS rks, NULL AS leaderboard_rank,
           CASE WHEN ph.user_hash IS NOT NULL THEN 1 ELSE 0 END AS public_active,
           ph.reason AS public_reason, ph.expires_at AS public_expires_at,
           CASE WHEN su.user_hash IS NOT NULL THEN 1 ELSE 0 END AS account_active,
           su.reason AS account_reason, su.expires_at AS account_expires_at
         {RESTRICTED_USERS_FROM}
           AND CASE ? WHEN 'public_hidden' THEN ph.user_hash IS NOT NULL
             WHEN 'account_suspended' THEN su.user_hash IS NOT NULL ELSE 1 END
         ORDER BY MAX(COALESCE(ph.updated_at,''),COALESCE(su.updated_at,'')) DESC, up.user_hash
         LIMIT ? OFFSET ?"
    );
    let items = sqlx::query(&list_sql)
        .bind(&now)
        .bind(&now)
        .bind(search)
        .bind(&pattern)
        .bind(&pattern)
        .bind(filter)
        .bind(i64::from(page_size))
        .bind(i64::from(page - 1) * i64::from(page_size))
        .fetch_all(&mut *transaction)
        .await
        .map_err(db)?
        .into_iter()
        .map(admin_user_from_row)
        .collect();
    transaction.commit().await.map_err(db)?;
    Ok(Json(RestrictedUsersResponse { items, total, page, page_size, counts }))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SetRestrictionRequest {
    user_hash: String,
    restriction_type: String,
    active: bool,
    reason: Option<String>,
    duration_minutes: Option<i64>,
}

async fn admin_set_restriction(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(request): Json<SetRestrictionRequest>,
) -> Result<Json<AdminRestriction>, AppError> {
    let admin_name = admin(&headers)?;
    let restriction = restriction_type(&request.restriction_type)?;
    let user_hash = request.user_hash.trim();
    if user_hash.is_empty() || user_hash.len() > 128 {
        return Err(AppError::Validation("userHash 无效".into()));
    }
    let reason = request
        .reason
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty());
    if request.active && reason.is_none() {
        return Err(AppError::Validation("请填写限制原因".into()));
    }
    if reason.is_some_and(|value| value.chars().count() > 1000) {
        return Err(AppError::Validation("限制原因最多 1000 字".into()));
    }
    if let Some(minutes) = request.duration_minutes
        && !(1..=129_600).contains(&minutes)
    {
        return Err(AppError::Validation("限制时长须在 1 分钟至 90 天之间，留空表示长期".into()));
    }
    let pool = pool(&state)?;
    let exists: i64 = sqlx::query_scalar("SELECT COUNT(1) FROM user_profile WHERE user_hash=?")
        .bind(user_hash)
        .fetch_one(pool)
        .await
        .map_err(db)?;
    if exists == 0 {
        return Err(AppError::NotFound("未找到该用户资料".into()));
    }
    let now = chrono::Utc::now();
    let expires_at = if request.active {
        request.duration_minutes.map(|minutes| {
            (now + chrono::Duration::minutes(minutes)).to_rfc3339_opts(chrono::SecondsFormat::Secs, true)
        })
    } else {
        None
    };
    let now_string = now.to_rfc3339_opts(chrono::SecondsFormat::Secs, true);
    let mut transaction = pool.begin().await.map_err(db)?;
    if request.active {
        sqlx::query(
            "INSERT INTO user_access_restrictions(user_hash,restriction_type,reason,updated_by,updated_at,expires_at,revoked_at)
             VALUES(?,?,?,?,?,?,NULL)
             ON CONFLICT(user_hash,restriction_type) DO UPDATE SET reason=excluded.reason,
               updated_by=excluded.updated_by,updated_at=excluded.updated_at,
               expires_at=excluded.expires_at,revoked_at=NULL",
        )
        .bind(user_hash)
        .bind(restriction)
        .bind(reason)
        .bind(&admin_name)
        .bind(&now_string)
        .bind(&expires_at)
        .execute(&mut *transaction)
        .await
        .map_err(db)?;
    } else {
        sqlx::query(
            "UPDATE user_access_restrictions SET revoked_at=?,updated_by=?,updated_at=?
             WHERE user_hash=? AND restriction_type=? AND revoked_at IS NULL",
        )
        .bind(&now_string)
        .bind(&admin_name)
        .bind(&now_string)
        .bind(user_hash)
        .bind(restriction)
        .execute(&mut *transaction)
        .await
        .map_err(db)?;
    }
    sqlx::query(
        "UPDATE moderation_appeals SET status=?,admin_reply=?,updated_at=?
         WHERE user_hash=? AND restriction_type=? AND status IN ('pending','reviewing')",
    )
    .bind(if request.active { "rejected" } else { "accepted" })
    .bind(if request.active { "限制状态已更新，请针对最新限制提交申诉。" } else { "管理员已解除此项限制。" })
    .bind(&now_string)
    .bind(user_hash)
    .bind(restriction)
    .execute(&mut *transaction)
    .await
    .map_err(db)?;
    transaction.commit().await.map_err(db)?;
    Ok(Json(AdminRestriction {
        active: request.active,
        reason: if request.active { reason.map(str::to_owned) } else { None },
        expires_at,
    }))
}

#[derive(Debug, Deserialize)]
struct AppealsQuery {
    status: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AdminAppeal {
    id: String,
    user_hash: String,
    alias: Option<String>,
    nickname: Option<String>,
    avatar: Option<String>,
    rks: f64,
    challenge_mode_rank: Option<i64>,
    restriction_type: String,
    body: String,
    status: String,
    admin_reply: String,
    created_at: String,
    updated_at: String,
    image_count: i64,
}

async fn admin_list_appeals(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(query): Query<AppealsQuery>,
) -> Result<Json<Vec<AdminAppeal>>, AppError> {
    admin(&headers)?;
    let status = query.status.as_deref().unwrap_or("pending");
    if !matches!(status, "pending" | "reviewing" | "accepted" | "rejected" | "all") {
        return Err(AppError::Validation("申诉状态无效".into()));
    }
    let sql = if status == "all" {
        "SELECT ma.*,up.alias,up.nickname,up.avatar,COALESCE(lr.total_rks,0.0) AS rks,
           up.challenge_mode_rank,(SELECT COUNT(*) FROM moderation_appeal_images i WHERE i.appeal_id=ma.id) AS image_count
         FROM moderation_appeals ma LEFT JOIN user_profile up ON up.user_hash=ma.user_hash
         LEFT JOIN leaderboard_rks lr ON lr.user_hash=ma.user_hash
         ORDER BY CASE ma.status WHEN 'pending' THEN 0 WHEN 'reviewing' THEN 1 ELSE 2 END,ma.created_at DESC LIMIT 100"
    } else {
        "SELECT ma.*,up.alias,up.nickname,up.avatar,COALESCE(lr.total_rks,0.0) AS rks,
           up.challenge_mode_rank,(SELECT COUNT(*) FROM moderation_appeal_images i WHERE i.appeal_id=ma.id) AS image_count
         FROM moderation_appeals ma LEFT JOIN user_profile up ON up.user_hash=ma.user_hash
         LEFT JOIN leaderboard_rks lr ON lr.user_hash=ma.user_hash
         WHERE ma.status=? ORDER BY ma.created_at DESC LIMIT 100"
    };
    let rows = if status == "all" {
        sqlx::query(sql).fetch_all(pool(&state)?).await.map_err(db)?
    } else {
        sqlx::query(sql).bind(status).fetch_all(pool(&state)?).await.map_err(db)?
    };
    let appeals = rows
        .into_iter()
        .map(|row| AdminAppeal {
            id: row.try_get("id").unwrap_or_default(),
            user_hash: row.try_get("user_hash").unwrap_or_default(),
            alias: row.try_get("alias").unwrap_or(None),
            nickname: row.try_get("nickname").unwrap_or(None),
            avatar: avatar_is_official(row.try_get("avatar").unwrap_or(None)),
            rks: row.try_get("rks").unwrap_or(0.0),
            challenge_mode_rank: row.try_get("challenge_mode_rank").unwrap_or(None),
            restriction_type: row.try_get("restriction_type").unwrap_or_default(),
            body: row.try_get("body").unwrap_or_default(),
            status: row.try_get("status").unwrap_or_default(),
            admin_reply: row.try_get("admin_reply").unwrap_or_default(),
            created_at: row.try_get("created_at").unwrap_or_default(),
            updated_at: row.try_get("updated_at").unwrap_or_default(),
            image_count: row.try_get("image_count").unwrap_or(0),
        })
        .collect();
    Ok(Json(appeals))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ReviewAppealRequest {
    status: String,
    reply: Option<String>,
}

async fn admin_review_appeal(
    State(state): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<String>,
    Json(request): Json<ReviewAppealRequest>,
) -> Result<Json<AdminAppeal>, AppError> {
    let admin_name = admin(&headers)?;
    if !matches!(request.status.as_str(), "reviewing" | "accepted" | "rejected") {
        return Err(AppError::Validation("申诉处理状态无效".into()));
    }
    let reply = request.reply.unwrap_or_default().trim().to_owned();
    if reply.chars().count() > 4000 {
        return Err(AppError::Validation("回复最多 4000 字".into()));
    }
    let pool = pool(&state)?;
    let now = chrono::Utc::now().to_rfc3339();
    let mut transaction = pool.begin().await.map_err(db)?;
    let row = sqlx::query("SELECT user_hash,restriction_type,status,created_at FROM moderation_appeals WHERE id=?")
        .bind(&id)
        .fetch_optional(&mut *transaction)
        .await
        .map_err(db)?
        .ok_or_else(|| AppError::NotFound("申诉不存在".into()))?;
    let user_hash: String = row.try_get("user_hash").unwrap_or_default();
    let restriction: String = row.try_get("restriction_type").unwrap_or_default();
    let previous_status: String = row.try_get("status").map_err(db)?;
    if !matches!(previous_status.as_str(), "pending" | "reviewing") {
        return Err(AppError::Conflict("此申诉已处理，请刷新列表查看结果".into()));
    }
    let created_at: String = row.try_get("created_at").map_err(db)?;
    sqlx::query("UPDATE moderation_appeals SET status=?,admin_reply=?,updated_at=? WHERE id=?")
        .bind(&request.status)
        .bind(&reply)
        .bind(&now)
        .bind(&id)
        .execute(&mut *transaction)
        .await
        .map_err(db)?;
    if request.status == "accepted" {
        sqlx::query(
            "UPDATE user_access_restrictions SET revoked_at=?,updated_by=?,updated_at=?
             WHERE user_hash=? AND restriction_type=? AND revoked_at IS NULL
               AND julianday(updated_at)<=julianday(?)",
        )
        .bind(&now)
        .bind(&admin_name)
        .bind(&now)
        .bind(&user_hash)
        .bind(&restriction)
        .bind(&created_at)
        .execute(&mut *transaction)
        .await
        .map_err(db)?;
    }
    transaction.commit().await.map_err(db)?;
    let row = sqlx::query(
        "SELECT ma.*,up.alias,up.nickname,up.avatar,COALESCE(lr.total_rks,0.0) AS rks,
           up.challenge_mode_rank,(SELECT COUNT(*) FROM moderation_appeal_images i WHERE i.appeal_id=ma.id) AS image_count
         FROM moderation_appeals ma LEFT JOIN user_profile up ON up.user_hash=ma.user_hash
         LEFT JOIN leaderboard_rks lr ON lr.user_hash=ma.user_hash WHERE ma.id=?",
    )
    .bind(&id)
    .fetch_one(pool)
    .await
    .map_err(db)?;
    Ok(Json(AdminAppeal {
        id: row.try_get("id").unwrap_or_default(),
        user_hash: row.try_get("user_hash").unwrap_or_default(),
        alias: row.try_get("alias").unwrap_or(None),
        nickname: row.try_get("nickname").unwrap_or(None),
        avatar: avatar_is_official(row.try_get("avatar").unwrap_or(None)),
        rks: row.try_get("rks").unwrap_or(0.0),
        challenge_mode_rank: row.try_get("challenge_mode_rank").unwrap_or(None),
        restriction_type: row.try_get("restriction_type").unwrap_or_default(),
        body: row.try_get("body").unwrap_or_default(),
        status: row.try_get("status").unwrap_or_default(),
        admin_reply: row.try_get("admin_reply").unwrap_or_default(),
        created_at: row.try_get("created_at").unwrap_or_default(),
        updated_at: row.try_get("updated_at").unwrap_or_default(),
        image_count: row.try_get("image_count").unwrap_or(0),
    }))
}

async fn admin_appeal_image(
    State(state): State<AppState>,
    headers: HeaderMap,
    Path((id, position)): Path<(String, i64)>,
) -> Result<Response, AppError> {
    admin(&headers)?;
    if !(0..3).contains(&position) {
        return Err(AppError::NotFound("申诉图片不存在".into()));
    }
    let row = sqlx::query("SELECT mime,data FROM moderation_appeal_images WHERE appeal_id=? AND position=?")
        .bind(id)
        .bind(position)
        .fetch_optional(pool(&state)?)
        .await
        .map_err(db)?
        .ok_or_else(|| AppError::NotFound("申诉图片不存在".into()))?;
    let mime: String = row.try_get("mime").unwrap_or_else(|_| "application/octet-stream".into());
    let bytes: Vec<u8> = row.try_get("data").map_err(db)?;
    let mut headers = HeaderMap::new();
    headers.insert(header::CONTENT_TYPE, HeaderValue::from_str(&mime).map_err(|e| AppError::Internal(format!("invalid appeal image mime: {e}")))?);
    headers.insert(header::CACHE_CONTROL, HeaderValue::from_static("private, no-store"));
    headers.insert(header::X_CONTENT_TYPE_OPTIONS, HeaderValue::from_static("nosniff"));
    Ok((headers, Body::from(bytes)).into_response())
}
