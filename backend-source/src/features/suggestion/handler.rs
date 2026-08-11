use std::path::PathBuf;

use axum::{
    Extension, Json, Router,
    extract::{DefaultBodyLimit, Multipart, Path as AxumPath, Query, State},
    http::StatusCode,
    routing::{delete, get, post},
};
use chrono::{DateTime, Utc};
use serde::Deserialize;
use uuid::Uuid;

use crate::{
    error::{AppError, SearchError},
    features::auth::bearer::BearerAuthState,
    state::AppState,
};

use super::models::{
    SuggestionAuthor, SuggestionComment, SuggestionCommentRecord, SuggestionNotificationResponse,
    SuggestionPost, SuggestionPostRecord,
};

const MAX_IMAGE_BYTES: usize = 8 * 1024 * 1024;
const MAX_MULTIPART_BYTES: usize = 10 * 1024 * 1024;
const POST_COOLDOWN_SECONDS: i64 = 30;
const COMMENT_COOLDOWN_SECONDS: i64 = 5;

#[derive(Debug, Deserialize)]
pub struct RandomQuery {
    exclude: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct NotificationQuery {
    after: String,
}

struct UploadedImage {
    bytes: Vec<u8>,
    extension: &'static str,
}

pub fn create_suggestion_router() -> Router<AppState> {
    Router::new()
        .route("/suggestions/posts", post(create_post))
        .route(
            "/suggestions/posts/:post_id",
            get(get_post).delete(delete_post),
        )
        .route("/suggestions/mine", get(own_posts))
        .route("/suggestions/commented", get(commented_posts))
        .route("/suggestions/random", get(random_post))
        .route("/suggestions/posts/:post_id/comments", post(create_comment))
        .route("/suggestions/comments/:comment_id", delete(delete_comment))
        .route("/suggestions/notifications", get(notifications))
        .layer(DefaultBodyLimit::max(MAX_MULTIPART_BYTES))
}

fn require_user_hash(bearer: &BearerAuthState) -> Result<&str, AppError> {
    match bearer {
        BearerAuthState::Valid(context) => Ok(context.claims.sub.as_str()),
        BearerAuthState::Invalid(message) => Err(AppError::Auth(message.clone())),
        BearerAuthState::Absent => Err(AppError::Auth("请先登录".into())),
    }
}

fn storage(state: &AppState) -> Result<&crate::features::stats::storage::StatsStorage, AppError> {
    state
        .stats_storage
        .as_deref()
        .ok_or_else(|| AppError::Internal("建议区存储未初始化".into()))
}

fn normalize_text(value: &str, max_chars: usize, fallback: &str) -> Result<String, AppError> {
    let normalized = value.replace("\r\n", "\n").replace('\r', "\n");
    let trimmed = normalized.trim();
    let value = if trimmed.is_empty() {
        fallback
    } else {
        trimmed
    };
    if value.chars().count() > max_chars {
        return Err(AppError::Validation(format!("文字最多 {max_chars} 个字符")));
    }
    if value.chars().any(|ch| ch.is_control() && ch != '\n') {
        return Err(AppError::Validation("文字包含无效控制字符".into()));
    }
    Ok(value.to_string())
}

fn image_extension(bytes: &[u8]) -> Option<&'static str> {
    if bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        Some("png")
    } else if bytes.len() >= 3 && bytes[0..3] == [0xff, 0xd8, 0xff] {
        Some("jpg")
    } else if bytes.len() >= 12 && &bytes[0..4] == b"RIFF" && &bytes[8..12] == b"WEBP" {
        Some("webp")
    } else {
        None
    }
}

fn parse_uploaded_image(bytes: Vec<u8>) -> Result<UploadedImage, AppError> {
    if bytes.is_empty() {
        return Err(AppError::Validation("成绩图为空".into()));
    }
    if bytes.len() > MAX_IMAGE_BYTES {
        return Err(AppError::Validation("成绩图不能超过 8 MB".into()));
    }
    let extension = image_extension(&bytes)
        .ok_or_else(|| AppError::Validation("仅支持 PNG、JPG 或 WebP 成绩图".into()))?;
    Ok(UploadedImage { bytes, extension })
}

fn ensure_cooldown(last: Option<String>, seconds: i64, message: &str) -> Result<(), AppError> {
    let Some(last) = last else { return Ok(()) };
    let Ok(last) = DateTime::parse_from_rfc3339(&last) else {
        return Ok(());
    };
    if Utc::now()
        .signed_duration_since(last.with_timezone(&Utc))
        .num_seconds()
        < seconds
    {
        return Err(AppError::Conflict(message.into()));
    }
    Ok(())
}

fn media_root() -> PathBuf {
    std::env::var_os("APP_SUGGESTION_MEDIA_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("resources/suggestion-media"))
}

async fn persist_image(image: &UploadedImage) -> Result<String, AppError> {
    let root = media_root();
    tokio::fs::create_dir_all(&root)
        .await
        .map_err(|e| AppError::Internal(format!("create suggestion media directory: {e}")))?;
    let id = Uuid::new_v4();
    let name = format!("{id}.{}", image.extension);
    let temporary = root.join(format!(".{id}.upload"));
    let destination = root.join(&name);
    tokio::fs::write(&temporary, &image.bytes)
        .await
        .map_err(|e| AppError::Internal(format!("write suggestion image: {e}")))?;
    if let Err(error) = tokio::fs::rename(&temporary, &destination).await {
        let _ = tokio::fs::remove_file(&temporary).await;
        return Err(AppError::Internal(format!(
            "publish suggestion image: {error}"
        )));
    }
    Ok(name)
}

async fn remove_media_if_present(name: Option<&str>) {
    if let Some(name) = name.filter(|name| {
        !name.trim().is_empty()
            && std::path::Path::new(name)
                .file_name()
                .is_some_and(|file_name| file_name == *name)
    }) {
        let _ = tokio::fs::remove_file(media_root().join(name)).await;
    }
}

fn media_url(name: &str) -> String {
    format!("/suggestion-media/{name}")
}

fn comment_from_record(
    record: SuggestionCommentRecord,
    viewer_user_hash: &str,
) -> SuggestionComment {
    let can_delete = record.user_hash == viewer_user_hash;
    SuggestionComment {
        id: record.id,
        text: record.text,
        image_url: record
            .image_name
            .as_deref()
            .filter(|name| !name.trim().is_empty())
            .map(media_url),
        author: SuggestionAuthor {
            nickname: record.nickname,
            avatar: record.avatar,
            challenge_mode_rank: record.challenge_mode_rank,
            rks: record.rks,
        },
        created_at: record.created_at,
        can_delete,
    }
}

async fn post_from_record(
    storage: &crate::features::stats::storage::StatsStorage,
    record: SuggestionPostRecord,
    viewer_user_hash: &str,
) -> Result<SuggestionPost, AppError> {
    let can_delete = record.user_hash == viewer_user_hash;
    let comments = storage
        .suggestion_comments(&record.id)
        .await?
        .into_iter()
        .map(|comment| comment_from_record(comment, viewer_user_hash))
        .collect();
    Ok(SuggestionPost {
        id: record.id,
        description: record.description,
        image_url: media_url(&record.image_name),
        author: SuggestionAuthor {
            nickname: record.nickname,
            avatar: record.avatar,
            challenge_mode_rank: record.challenge_mode_rank,
            rks: record.rks,
        },
        created_at: record.created_at,
        comments,
        can_delete,
    })
}

async fn parse_post_form(mut multipart: Multipart) -> Result<(String, UploadedImage), AppError> {
    let mut description = None;
    let mut image = None;
    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| AppError::Validation(format!("上传表单无效: {e}")))?
    {
        match field.name() {
            Some("description") => {
                description = Some(
                    field
                        .text()
                        .await
                        .map_err(|e| AppError::Validation(format!("读取描述失败: {e}")))?,
                );
            }
            Some("image") => {
                let bytes = field
                    .bytes()
                    .await
                    .map_err(|e| AppError::Validation(format!("读取成绩图失败: {e}")))?;
                image = Some(parse_uploaded_image(bytes.to_vec())?);
            }
            _ => {}
        }
    }
    Ok((
        normalize_text(description.as_deref().unwrap_or_default(), 120, "求建议！")?,
        image.ok_or_else(|| AppError::Validation("请选择 B30/P30 成绩图".into()))?,
    ))
}

async fn parse_comment_form(
    mut multipart: Multipart,
) -> Result<(String, Option<UploadedImage>), AppError> {
    let mut text = None;
    let mut image = None;
    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| AppError::Validation(format!("上传表单无效: {e}")))?
    {
        match field.name() {
            Some("text") => {
                text = Some(
                    field
                        .text()
                        .await
                        .map_err(|e| AppError::Validation(format!("读取评论失败: {e}")))?,
                );
            }
            Some("image") => {
                let bytes = field
                    .bytes()
                    .await
                    .map_err(|e| AppError::Validation(format!("读取成绩图失败: {e}")))?;
                image = Some(parse_uploaded_image(bytes.to_vec())?);
            }
            _ => {}
        }
    }
    let text = normalize_text(text.as_deref().unwrap_or_default(), 240, "")?;
    if text.is_empty() && image.is_none() {
        return Err(AppError::Validation("请填写建议或选择成绩图".into()));
    }
    Ok((text, image))
}

pub async fn create_post(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    multipart: Multipart,
) -> Result<Json<SuggestionPost>, AppError> {
    let user_hash = require_user_hash(&bearer)?;
    let storage = storage(&state)?;
    ensure_cooldown(
        storage.latest_suggestion_post_at(user_hash).await?,
        POST_COOLDOWN_SECONDS,
        "发送太快，请稍后再试",
    )?;
    let (description, image) = parse_post_form(multipart).await?;
    let author = storage.suggestion_author(user_hash).await?;
    let image_name = persist_image(&image).await?;
    let id = Uuid::new_v4().to_string();
    let created_at = Utc::now().to_rfc3339();
    if let Err(error) = storage
        .insert_suggestion_post(
            &id,
            user_hash,
            &description,
            &image_name,
            &author,
            &created_at,
        )
        .await
    {
        remove_media_if_present(Some(&image_name)).await;
        return Err(error);
    }
    if let Some(stats) = &state.stats {
        stats.track_feature("suggestion", "post", Some(user_hash.to_string()), None);
    }
    Ok(Json(SuggestionPost {
        id,
        description,
        image_url: media_url(&image_name),
        author,
        created_at,
        comments: Vec::new(),
        can_delete: true,
    }))
}

pub async fn random_post(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    Query(query): Query<RandomQuery>,
) -> Result<Json<SuggestionPost>, AppError> {
    let user_hash = require_user_hash(&bearer)?;
    let storage = storage(&state)?;
    let record = storage
        .random_suggestion_post(query.exclude.as_deref())
        .await?
        .ok_or_else(|| AppError::Search(SearchError::NotFound))?;
    let post = post_from_record(storage, record, user_hash).await?;
    if let Some(stats) = &state.stats {
        stats.track_feature("suggestion", "random", Some(user_hash.to_string()), None);
    }
    Ok(Json(post))
}

pub async fn get_post(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    AxumPath(post_id): AxumPath<String>,
) -> Result<Json<SuggestionPost>, AppError> {
    let user_hash = require_user_hash(&bearer)?;
    let storage = storage(&state)?;
    let record = storage
        .suggestion_post(&post_id)
        .await?
        .ok_or_else(|| AppError::Search(SearchError::NotFound))?;
    Ok(Json(post_from_record(storage, record, user_hash).await?))
}

pub async fn own_posts(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
) -> Result<Json<Vec<SuggestionPost>>, AppError> {
    let user_hash = require_user_hash(&bearer)?;
    let storage = storage(&state)?;
    let records = storage.own_suggestion_posts(user_hash).await?;
    let mut posts = Vec::with_capacity(records.len());
    for record in records {
        posts.push(post_from_record(storage, record, user_hash).await?);
    }
    Ok(Json(posts))
}

pub async fn commented_posts(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
) -> Result<Json<Vec<SuggestionPost>>, AppError> {
    let user_hash = require_user_hash(&bearer)?;
    let storage = storage(&state)?;
    let records = storage.commented_suggestion_posts(user_hash).await?;
    let mut posts = Vec::with_capacity(records.len());
    for record in records {
        posts.push(post_from_record(storage, record, user_hash).await?);
    }
    Ok(Json(posts))
}

pub async fn delete_post(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    AxumPath(post_id): AxumPath<String>,
) -> Result<StatusCode, AppError> {
    let user_hash = require_user_hash(&bearer)?;
    let storage = storage(&state)?;
    let record = storage
        .suggestion_post(&post_id)
        .await?
        .ok_or_else(|| AppError::Search(SearchError::NotFound))?;
    if record.user_hash != user_hash {
        return Err(AppError::Forbidden("只能删除自己的求建议帖子".into()));
    }
    let images = storage
        .delete_suggestion_post(&post_id, user_hash)
        .await?
        .ok_or_else(|| AppError::Forbidden("只能删除自己的求建议帖子".into()))?;
    for image_name in images {
        remove_media_if_present(Some(&image_name)).await;
    }
    Ok(StatusCode::NO_CONTENT)
}

pub async fn delete_comment(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    AxumPath(comment_id): AxumPath<String>,
) -> Result<StatusCode, AppError> {
    let user_hash = require_user_hash(&bearer)?;
    let storage = storage(&state)?;
    let record = storage
        .suggestion_comment(&comment_id)
        .await?
        .ok_or_else(|| AppError::Search(SearchError::NotFound))?;
    if record.user_hash != user_hash {
        return Err(AppError::Forbidden("只能删除自己的建议评论".into()));
    }
    let image_name = storage
        .delete_suggestion_comment(&comment_id, user_hash)
        .await?
        .ok_or_else(|| AppError::Forbidden("只能删除自己的建议评论".into()))?;
    remove_media_if_present(image_name.as_deref()).await;
    Ok(StatusCode::NO_CONTENT)
}

pub async fn notifications(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    Query(query): Query<NotificationQuery>,
) -> Result<Json<SuggestionNotificationResponse>, AppError> {
    let user_hash = require_user_hash(&bearer)?;
    let after = DateTime::parse_from_rfc3339(query.after.trim())
        .map_err(|_| AppError::Validation("通知检查时间无效".into()))?
        .with_timezone(&Utc);
    let checked_at = Utc::now();
    let items = storage(&state)?
        .suggestion_notifications(user_hash, &after.to_rfc3339())
        .await?;
    Ok(Json(SuggestionNotificationResponse {
        checked_at: checked_at.to_rfc3339(),
        items,
    }))
}

pub async fn create_comment(
    State(state): State<AppState>,
    Extension(bearer): Extension<BearerAuthState>,
    AxumPath(post_id): AxumPath<String>,
    multipart: Multipart,
) -> Result<Json<SuggestionComment>, AppError> {
    let user_hash = require_user_hash(&bearer)?;
    let storage = storage(&state)?;
    if !storage.suggestion_post_exists(&post_id).await? {
        return Err(AppError::Search(SearchError::NotFound));
    }
    ensure_cooldown(
        storage.latest_suggestion_comment_at(user_hash).await?,
        COMMENT_COOLDOWN_SECONDS,
        "评论太快，请稍后再试",
    )?;
    let (text, image) = parse_comment_form(multipart).await?;
    let author = storage.suggestion_author(user_hash).await?;
    let image_name = match image.as_ref() {
        Some(image) => Some(persist_image(image).await?),
        None => None,
    };
    let id = Uuid::new_v4().to_string();
    let created_at = Utc::now().to_rfc3339();
    if let Err(error) = storage
        .insert_suggestion_comment(
            &id,
            &post_id,
            user_hash,
            &text,
            image_name.as_deref(),
            &author,
            &created_at,
        )
        .await
    {
        remove_media_if_present(image_name.as_deref()).await;
        return Err(error);
    }
    if let Some(stats) = &state.stats {
        stats.track_feature("suggestion", "comment", Some(user_hash.to_string()), None);
    }
    Ok(Json(SuggestionComment {
        id,
        text,
        image_url: image_name.as_deref().map(media_url),
        author,
        created_at,
        can_delete: true,
    }))
}

#[cfg(test)]
mod tests {
    use super::{image_extension, normalize_text};

    #[test]
    fn image_magic_is_limited_to_supported_formats() {
        assert_eq!(image_extension(b"\x89PNG\r\n\x1a\nrest"), Some("png"));
        assert_eq!(image_extension(b"\xff\xd8\xffrest"), Some("jpg"));
        assert_eq!(image_extension(b"RIFF0000WEBPrest"), Some("webp"));
        assert_eq!(image_extension(b"GIF89a"), None);
    }

    #[test]
    fn post_text_is_short_and_uses_direct_fallback() {
        assert_eq!(normalize_text("  ", 120, "求建议！").unwrap(), "求建议！");
        assert!(normalize_text(&"a".repeat(121), 120, "").is_err());
    }
}
