//! Private feedback: attachments are served only after owner/admin authorization.
use crate::{
    error::AppError,
    features::{auth::bearer::BearerAuthState, leaderboard::handler::admin::require_admin},
    state::AppState,
};
use axum::{
    Extension, Json, Router,
    extract::{DefaultBodyLimit, Path, Query, State},
    http::{HeaderMap, header},
    routing::{get, post},
};
use base64::{Engine, engine::general_purpose::STANDARD};
use serde::{Deserialize, Serialize};
use sqlx::Row;
use std::hash::{Hash, Hasher};

// Subscribers register before reading SQLite so a save between read and wait is not lost.
static CHANGES: once_cell::sync::Lazy<tokio::sync::watch::Sender<()>> =
    once_cell::sync::Lazy::new(|| tokio::sync::watch::channel(()).0);

pub const SCHEMA: &str = r#"
CREATE TABLE IF NOT EXISTS feedback (
 id TEXT PRIMARY KEY, owner TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'pending', reply TEXT NOT NULL DEFAULT '',
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 0,
 read_revision INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS feedback_owner_date ON feedback(owner, created_at DESC);
CREATE TABLE IF NOT EXISTS feedback_images (
 feedback_id TEXT NOT NULL REFERENCES feedback(id) ON DELETE CASCADE,
 position INTEGER NOT NULL, mime TEXT NOT NULL, data BLOB NOT NULL,
 PRIMARY KEY(feedback_id, position)
);
"#;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/feedback", post(create))
        .route("/feedback/mine", get(mine))
        .route("/feedback/notifications", get(notifications))
        .route("/feedback/updates", get(updates))
        .route("/feedback/:id/read", post(mark_read))
        .route("/feedback/:id/images/:position", get(owner_image))
        .route("/admin/feedback", get(admin_list))
        .route("/admin/feedback/:id", post(update))
        .route("/admin/feedback/:id/images/:position", get(admin_image))
        .layer(DefaultBodyLimit::max(5 * 1024 * 1024))
}

fn owner(auth: &BearerAuthState) -> Result<&str, AppError> {
    match auth {
        BearerAuthState::Valid(c) => Ok(&c.claims.sub),
        _ => Err(AppError::Auth("请先登录".into())),
    }
}
fn pool(state: &AppState) -> Result<&sqlx::SqlitePool, AppError> {
    state
        .stats_storage
        .as_ref()
        .map(|s| &s.pool)
        .ok_or_else(|| AppError::Internal("反馈存储未初始化".into()))
}
fn db(e: sqlx::Error) -> AppError {
    AppError::Internal(format!("feedback storage: {e}"))
}
fn text(value: &str, max: usize) -> Result<String, AppError> {
    let value = value.trim();
    if value.is_empty()
        || value.chars().count() > max
        || value
            .chars()
            .any(|c| c.is_control() && c != '\n' && c != '\r' && c != '\t')
    {
        return Err(AppError::Validation(format!("请输入 1–{max} 字的有效文字")));
    }
    Ok(value.into())
}
#[derive(Deserialize)]
pub struct Create {
    id: String,
    title: String,
    body: String,
    #[serde(default)]
    images: Vec<String>,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Feedback {
    id: String,
    title: String,
    body: String,
    status: String,
    reply: String,
    created_at: String,
    updated_at: String,
    revision: i64,
    read_revision: i64,
    image_count: i64,
}
fn record(row: sqlx::sqlite::SqliteRow) -> Feedback {
    Feedback {
        id: row.get("id"),
        title: row.get("title"),
        body: row.get("body"),
        status: row.get("status"),
        reply: row.get("reply"),
        created_at: row.get("created_at"),
        updated_at: row.get("updated_at"),
        revision: row.get("revision"),
        read_revision: row.get("read_revision"),
        image_count: row.get("image_count"),
    }
}
const SELECT: &str = "SELECT f.*, (SELECT COUNT(*) FROM feedback_images i WHERE i.feedback_id=f.id) image_count FROM feedback f";

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FeedbackSubmitter {
    nickname: Option<String>,
    rks: Option<f64>,
    challenge_mode_rank: Option<i64>,
    leaderboard_rank: Option<i64>,
}

#[derive(Serialize)]
pub struct AdminFeedback {
    #[serde(flatten)]
    feedback: Feedback,
    submitter: FeedbackSubmitter,
}

// Use the public RKS board's visibility rules and stable tie ordering. Keep the
// submitter's profile available to admins even when the player is not ranked.
const ADMIN_SELECT: &str = r#"
WITH ranked_users AS (
    SELECT lr.user_hash,
           ROW_NUMBER() OVER (
               ORDER BY lr.total_rks DESC, lr.updated_at ASC, lr.user_hash ASC
           ) AS leaderboard_rank
    FROM leaderboard_rks lr
    JOIN user_profile up ON up.user_hash=lr.user_hash AND up.is_public=1
    WHERE lr.is_hidden=0
      AND NOT EXISTS (
          SELECT 1 FROM publicly_restricted_users blocked
          WHERE blocked.user_hash=lr.user_hash
      )
)
SELECT f.*, (SELECT COUNT(*) FROM feedback_images i WHERE i.feedback_id=f.id) image_count,
       COALESCE(NULLIF(TRIM(up.nickname),''), NULLIF(TRIM(up.alias),'')) AS submitter_nickname,
       lr.total_rks AS submitter_rks,
       up.challenge_mode_rank AS submitter_challenge_mode_rank,
       ranked.leaderboard_rank AS submitter_leaderboard_rank
FROM feedback f
LEFT JOIN user_profile up ON up.user_hash=f.owner
LEFT JOIN leaderboard_rks lr ON lr.user_hash=f.owner
LEFT JOIN ranked_users ranked ON ranked.user_hash=f.owner
ORDER BY f.created_at DESC, f.id DESC
LIMIT 30 OFFSET ?
"#;

fn admin_record(row: sqlx::sqlite::SqliteRow) -> AdminFeedback {
    let submitter = FeedbackSubmitter {
        nickname: row.get("submitter_nickname"),
        rks: row.get("submitter_rks"),
        challenge_mode_rank: row.get("submitter_challenge_mode_rank"),
        leaderboard_rank: row.get("submitter_leaderboard_rank"),
    };
    AdminFeedback {
        feedback: record(row),
        submitter,
    }
}

async fn create(
    State(state): State<AppState>,
    Extension(auth): Extension<BearerAuthState>,
    Json(input): Json<Create>,
) -> Result<Json<Feedback>, AppError> {
    let user = owner(&auth)?;
    uuid::Uuid::parse_str(&input.id).map_err(|_| AppError::Validation("无效的反馈编号".into()))?;
    let title = text(&input.title, 80)?;
    let body = text(&input.body, 4000)?;
    if input.images.len() > 3 {
        return Err(AppError::Validation("最多添加 3 张图片".into()));
    }
    let mut images = Vec::new();
    for encoded in input.images {
        if encoded.len() > 1_400_000 {
            return Err(AppError::Validation("单张图片不能超过 1 MB".into()));
        }
        let bytes = STANDARD
            .decode(encoded)
            .map_err(|_| AppError::Validation("无效图片".into()))?;
        if bytes.len() > 1024 * 1024 {
            return Err(AppError::Validation("单张图片不能超过 1 MB".into()));
        }
        let format =
            image::guess_format(&bytes).map_err(|_| AppError::Validation("无效图片".into()))?;
        let mime = match format {
            image::ImageFormat::Jpeg => "image/jpeg",
            image::ImageFormat::Png => "image/png",
            image::ImageFormat::WebP => "image/webp",
            _ => return Err(AppError::Validation("仅支持 JPG、PNG、WebP".into())),
        };
        let reader = image::ImageReader::with_format(std::io::Cursor::new(&bytes), format);
        let (w, h) = reader
            .into_dimensions()
            .map_err(|_| AppError::Validation("图片已损坏".into()))?;
        if w == 0 || h == 0 || u64::from(w) * u64::from(h) > 16_000_000 {
            return Err(AppError::Validation("图片尺寸过大".into()));
        }
        images.push((mime, bytes));
    }
    let pool = pool(&state)?;
    // Acquire the SQLite writer before checking quota/idempotency.
    let mut tx = pool.begin().await.map_err(db)?;
    sqlx::query("UPDATE feedback SET revision=revision WHERE id=?")
        .bind(&input.id)
        .execute(&mut *tx)
        .await
        .map_err(db)?;
    if let Some(row) = sqlx::query(&format!("{SELECT} WHERE id=? AND owner=?"))
        .bind(&input.id)
        .bind(user)
        .fetch_optional(&mut *tx)
        .await
        .map_err(db)?
    {
        return Ok(Json(record(row)));
    }
    let count: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM feedback WHERE owner=? AND created_at>=?")
            .bind(user)
            .bind((chrono::Utc::now() - chrono::Duration::days(1)).to_rfc3339())
            .fetch_one(&mut *tx)
            .await
            .map_err(db)?;
    if count >= 10 {
        return Err(AppError::Validation(
            "每天最多提交 10 条反馈，请稍后再试".into(),
        ));
    }
    let now = chrono::Utc::now().to_rfc3339();
    sqlx::query(
        "INSERT INTO feedback(id,owner,title,body,created_at,updated_at) VALUES(?,?,?,?,?,?)",
    )
    .bind(&input.id)
    .bind(user)
    .bind(title)
    .bind(body)
    .bind(&now)
    .bind(&now)
    .execute(&mut *tx)
    .await
    .map_err(db)?;
    for (position, (mime, bytes)) in images.into_iter().enumerate() {
        sqlx::query("INSERT INTO feedback_images VALUES(?,?,?,?)")
            .bind(&input.id)
            .bind(position as i64)
            .bind(mime)
            .bind(bytes)
            .execute(&mut *tx)
            .await
            .map_err(db)?;
    }
    let row = sqlx::query(&format!("{SELECT} WHERE id=?"))
        .bind(&input.id)
        .fetch_one(&mut *tx)
        .await
        .map_err(db)?;
    tx.commit().await.map_err(db)?;
    Ok(Json(record(row)))
}
#[derive(Deserialize)]
pub struct Page {
    #[serde(default)]
    offset: u32,
}
async fn mine(
    State(state): State<AppState>,
    Extension(auth): Extension<BearerAuthState>,
    Query(page): Query<Page>,
) -> Result<Json<Vec<Feedback>>, AppError> {
    let rows = sqlx::query(&format!(
        "{SELECT} WHERE owner=? ORDER BY created_at DESC,id DESC LIMIT 30 OFFSET ?"
    ))
    .bind(owner(&auth)?)
    .bind(page.offset)
    .fetch_all(pool(&state)?)
    .await
    .map_err(db)?;
    Ok(Json(rows.into_iter().map(record).collect()))
}
async fn notifications(
    State(state): State<AppState>,
    Extension(auth): Extension<BearerAuthState>,
) -> Result<Json<Vec<Feedback>>, AppError> {
    Ok(Json(unread(&state, owner(&auth)?).await?))
}

async fn unread(state: &AppState, user: &str) -> Result<Vec<Feedback>, AppError> {
    let rows = sqlx::query(&format!(
        "{SELECT} WHERE owner=? AND revision>read_revision ORDER BY updated_at DESC LIMIT 100"
    ))
    .bind(user)
    .fetch_all(pool(state)?)
    .await
    .map_err(db)?;
    Ok(rows.into_iter().map(record).collect())
}

#[derive(Deserialize)]
pub struct UpdatesQuery {
    cursor: Option<String>,
}
#[derive(Serialize)]
pub struct UpdatesResponse {
    cursor: String,
    items: Vec<Feedback>,
}

async fn updates(
    State(state): State<AppState>,
    Extension(auth): Extension<BearerAuthState>,
    Query(query): Query<UpdatesQuery>,
) -> Result<Json<UpdatesResponse>, AppError> {
    let user = owner(&auth)?;
    if query.cursor.as_ref().is_some_and(|value| value.len() > 32) {
        return Err(AppError::Validation("无效的更新游标".into()));
    }
    let mut changes = CHANGES.subscribe();
    let deadline = tokio::time::Instant::now() + std::time::Duration::from_secs(25);
    loop {
        let items = unread(&state, user).await?;
        let mut versions: Vec<_> = items.iter().map(|item| (&item.id, item.revision)).collect();
        versions.sort_unstable();
        let mut hash = std::collections::hash_map::DefaultHasher::new();
        versions.hash(&mut hash);
        let cursor = format!("{:016x}", hash.finish());
        if query.cursor.as_deref() != Some(cursor.as_str())
            || tokio::time::Instant::now() >= deadline
        {
            return Ok(Json(UpdatesResponse { cursor, items }));
        }
        // Also observe writes made by another server process sharing this database.
        let check_at =
            deadline.min(tokio::time::Instant::now() + std::time::Duration::from_secs(5));
        tokio::select! {
            _ = changes.changed() => {},
            _ = tokio::time::sleep_until(check_at) => {},
        }
    }
}
#[derive(Deserialize)]
pub struct Read {
    revision: i64,
}
async fn mark_read(
    State(state): State<AppState>,
    Extension(auth): Extension<BearerAuthState>,
    Path(id): Path<String>,
    Json(input): Json<Read>,
) -> Result<Json<bool>, AppError> {
    let result = sqlx::query("UPDATE feedback SET read_revision=MAX(read_revision,MIN(revision,?)) WHERE id=? AND owner=?")
        .bind(input.revision.max(0)).bind(id).bind(owner(&auth)?).execute(pool(&state)?).await.map_err(db)?;
    if result.rows_affected() > 0 {
        CHANGES.send_replace(());
    }
    Ok(Json(result.rows_affected() > 0))
}
async fn admin_list(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(page): Query<Page>,
) -> Result<Json<Vec<AdminFeedback>>, AppError> {
    require_admin(&headers)?;
    let rows = sqlx::query(ADMIN_SELECT)
        .bind(page.offset)
        .fetch_all(pool(&state)?)
        .await
        .map_err(db)?;
    Ok(Json(rows.into_iter().map(admin_record).collect()))
}
#[derive(Deserialize)]
pub struct Update {
    status: String,
    reply: String,
    revision: i64,
}
async fn update(
    State(state): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<String>,
    Json(input): Json<Update>,
) -> Result<Json<bool>, AppError> {
    require_admin(&headers)?;
    if !["pending", "processing", "resolved"].contains(&input.status.as_str()) {
        return Err(AppError::Validation("无效的反馈状态".into()));
    }
    let reply = if input.reply.trim().is_empty() && input.status != "resolved" {
        String::new()
    } else {
        text(&input.reply, 4000)?
    };
    let result = sqlx::query("UPDATE feedback SET status=?,reply=?,updated_at=?,revision=revision+1 WHERE id=? AND revision=?")
        .bind(input.status).bind(reply).bind(chrono::Utc::now().to_rfc3339()).bind(id).bind(input.revision).execute(pool(&state)?).await.map_err(db)?;
    if result.rows_affected() == 0 {
        return Err(AppError::Conflict(
            "反馈已更新或不存在，请刷新后重试".into(),
        ));
    }
    CHANGES.send_replace(());
    Ok(Json(true))
}
async fn image_data(
    state: &AppState,
    id: &str,
    position: i64,
    user: Option<&str>,
) -> Result<(HeaderMap, Vec<u8>), AppError> {
    let row = sqlx::query("SELECT i.mime,i.data FROM feedback_images i JOIN feedback f ON f.id=i.feedback_id WHERE f.id=? AND i.position=? AND (? IS NULL OR f.owner=?)")
        .bind(id).bind(position).bind(user).bind(user).fetch_optional(pool(state)?).await.map_err(db)?
        .ok_or_else(|| AppError::Validation("图片不存在或无权访问".into()))?;
    let mut headers = HeaderMap::new();
    headers.insert(
        header::CONTENT_TYPE,
        row.get::<String, _>("mime")
            .parse()
            .map_err(|_| AppError::Internal("invalid image mime".into()))?,
    );
    headers.insert(header::CACHE_CONTROL, "private, no-store".parse().unwrap());
    headers.insert("x-content-type-options", "nosniff".parse().unwrap());
    Ok((headers, row.get("data")))
}
async fn owner_image(
    State(state): State<AppState>,
    Extension(auth): Extension<BearerAuthState>,
    Path((id, position)): Path<(String, i64)>,
) -> Result<(HeaderMap, Vec<u8>), AppError> {
    image_data(&state, &id, position, Some(owner(&auth)?)).await
}
async fn admin_image(
    State(state): State<AppState>,
    headers: HeaderMap,
    Path((id, position)): Path<(String, i64)>,
) -> Result<(HeaderMap, Vec<u8>), AppError> {
    require_admin(&headers)?;
    image_data(&state, &id, position, None).await
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn validate_text() {
        assert!(text("  ", 80).is_err());
        assert!(text(&"字".repeat(81), 80).is_err());
        assert_eq!(text(" 标题 ", 80).unwrap(), "标题");
    }
    #[tokio::test]
    async fn schema_and_owner_isolation() {
        let p = sqlx::SqlitePool::connect("sqlite::memory:").await.unwrap();
        sqlx::raw_sql(SCHEMA).execute(&p).await.unwrap();
        sqlx::query("INSERT INTO feedback(id,owner,title,body,created_at,updated_at,revision) VALUES('a','alice','title','body','now','now',2)").execute(&p).await.unwrap();
        let other: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM feedback WHERE owner='bob'")
            .fetch_one(&p)
            .await
            .unwrap();
        assert_eq!(other, 0);
        sqlx::query("UPDATE feedback SET read_revision=MAX(read_revision,MIN(revision,1)) WHERE id='a' AND owner='alice'").execute(&p).await.unwrap();
        let unread: i64 =
            sqlx::query_scalar("SELECT COUNT(*) FROM feedback WHERE revision>read_revision")
                .fetch_one(&p)
                .await
                .unwrap();
        assert_eq!(unread, 1);
    }
}
