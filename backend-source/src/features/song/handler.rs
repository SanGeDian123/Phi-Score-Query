use axum::{
    Router,
    extract::{Query, State},
    http::{HeaderMap, HeaderValue, StatusCode, header},
    response::{IntoResponse, Json},
    routing::get,
};
use sha2::{Digest, Sha256};

use crate::error::AppError;
use crate::features::song::models::{SearchMode, SongCandidatePreview, SongInfo};
use crate::state::AppState;

const DEFAULT_LIMIT: u32 = 20;
const MAX_LIMIT: u32 = 100;
const MAX_QUERY_CHARS: usize = 128;

/// 分页响应（用于非 unique 查询）。
#[derive(serde::Serialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
#[schema(example = json!({
  "items": [
    {"id": "97f9466b2e77", "name": "Arcahv", "composer": "Feryquitous", "illustrator": "Catrong", "chartConstants": {"ez": 4.5, "hd": 7.9, "in": 9.6, "at": 12.3}}
  ],
  "total": 123,
  "limit": 20,
  "offset": 0,
  "hasMore": true,
  "nextOffset": 20
}))]
pub struct SongSearchPage {
    pub items: Vec<SongInfo>,
    pub total: u32,
    pub limit: u32,
    pub offset: u32,
    pub has_more: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub next_offset: Option<u32>,
}

/// oneOf 响应：单个 SongInfo 或 SongSearchPage
#[derive(serde::Serialize, utoipa::ToSchema)]
#[serde(untagged)]
pub enum SongSearchResult {
    Single(SongInfo),
    Page(SongSearchPage),
}

#[derive(serde::Serialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct SongCatalogResponse {
    pub version: String,
    pub items: Vec<SongInfo>,
}

#[derive(serde::Deserialize)]
pub struct SongSearchQuery {
    /// 查询字符串
    q: String,
    /// 是否强制唯一匹配（可选，支持 1/true/yes/on）
    unique: Option<String>,
    /// 多关键词模式（可选：and/or）。仅显式传入时启用多关键词搜索
    mode: Option<String>,
    /// 最大返回条数（可选，默认 20，上限 100，最小 1）
    limit: Option<u32>,
    /// 结果偏移（可选，默认 0）
    offset: Option<u32>,
}

fn parse_bool(s: &str) -> bool {
    s == "1"
        || s.eq_ignore_ascii_case("true")
        || s.eq_ignore_ascii_case("yes")
        || s.eq_ignore_ascii_case("on")
}

fn parse_search_mode(input: &str) -> Option<SearchMode> {
    if input.eq_ignore_ascii_case("and") {
        Some(SearchMode::And)
    } else if input.eq_ignore_ascii_case("or") {
        Some(SearchMode::Or)
    } else {
        None
    }
}

fn build_song_page(items: Vec<SongInfo>, total: usize, limit: u32, offset: u32) -> SongSearchPage {
    let total_u32 = u32::try_from(total).unwrap_or(u32::MAX);
    let end = (offset as usize).saturating_add(items.len());
    let has_more = end < total;
    let next_offset = if has_more {
        u32::try_from(end).ok()
    } else {
        None
    };

    SongSearchPage {
        items,
        total: total_u32,
        limit,
        offset,
        has_more,
        next_offset,
    }
}

#[utoipa::path(
    get,
    path = "/songs/search",
    summary = "歌曲检索（支持别名与模糊匹配）",
    description = "默认按 ID/官方名/别名进行搜索。显式传 `mode=and|or` 时启用多关键词搜索，支持空格分词、双引号短语与前缀 `-` 排除。`unique=true` 时期望唯一命中，未命中返回 404，多命中返回 409。",
    params(
        ("q" = String, Query, description = "查询字符串"),
        ("unique" = Option<bool>, Query, description = "是否强制唯一匹配（可选）"),
        ("mode" = Option<String>, Query, description = "多关键词模式（可选：and/or）。仅显式传入时启用多关键词搜索"),
        ("limit" = Option<u32>, Query, description = "最大返回条数（可选，默认 20，上限 100，最小 1）"),
        ("offset" = Option<u32>, Query, description = "结果偏移（可选，默认 0）")
    ),
    responses(
        (status = 200, description = "查询成功（unique=true 时返回单个对象，否则为分页对象）", body = SongSearchResult),
        (
            status = 400,
            description = "请求参数错误（缺少 q 等）",
            body = crate::error::ProblemDetails,
            content_type = "application/problem+json"
        ),
        (
            status = 422,
            description = "参数校验错误（q 过长 / limit 无效等）",
            body = crate::error::ProblemDetails,
            content_type = "application/problem+json"
        ),
        (
            status = 404,
            description = "未找到匹配项",
            body = crate::error::ProblemDetails,
            content_type = "application/problem+json"
        ),
        (
            status = 409,
            description = "结果不唯一",
            body = crate::error::ProblemDetails,
            content_type = "application/problem+json"
        ),
        (
            status = 500,
            description = "服务器内部错误",
            body = crate::error::ProblemDetails,
            content_type = "application/problem+json"
        )
    ),
    tag = "Song"
)]
pub async fn search_songs(
    State(state): State<AppState>,
    Query(params): Query<SongSearchQuery>,
) -> Result<axum::response::Response, AppError> {
    let q = params.q.trim();
    if q.is_empty() {
        return Err(AppError::Json("缺少查询参数 q".into()));
    }
    let q_len = q.chars().count();
    if q_len > MAX_QUERY_CHARS {
        return Err(AppError::Validation(format!(
            "查询参数 q 过长（最大 {MAX_QUERY_CHARS} 字符）"
        )));
    }

    let unique = params.unique.as_deref().is_some_and(parse_bool);
    let multi_mode = match params.mode.as_deref() {
        None => None,
        Some(raw) => Some(
            parse_search_mode(raw)
                .ok_or_else(|| AppError::Validation("mode 仅支持 and 或 or".into()))?,
        ),
    };

    let limit = match params.limit {
        None => DEFAULT_LIMIT,
        Some(0) => return Err(AppError::Validation("limit 必须 >= 1".into())),
        Some(v) => v.min(MAX_LIMIT),
    };
    let offset = params.offset.unwrap_or(0);

    // 统计：歌曲搜索（不记录原始查询词，避免敏感信息）
    if let Some(stats_handle) = state.stats.as_ref() {
        let extra = serde_json::json!({
            "unique": unique,
            "multi_mode": params.mode.as_deref(),
            "q_len": q_len,
            "limit": limit,
            "offset": offset
        });
        stats_handle.track_feature("song_search", "search", None, Some(extra));
    }

    if let Some(mode) = multi_mode {
        let results = state.song_catalog.search_multi(
            q,
            mode,
            crate::features::song::models::SearchOptions::default(),
        );

        if unique {
            match results.as_slice() {
                [] => Err(crate::error::SearchError::NotFound.into()),
                [item] => Ok(Json::<SongInfo>(item.as_ref().clone()).into_response()),
                many => {
                    let candidates: Vec<SongCandidatePreview> = many
                        .iter()
                        .take(10)
                        .map(|item| SongCandidatePreview {
                            id: item.id.clone(),
                            name: item.name.clone(),
                        })
                        .collect();
                    let total = u32::try_from(many.len()).unwrap_or(u32::MAX);
                    Err(crate::error::SearchError::NotUnique { total, candidates }.into())
                }
            }
        } else {
            let total = results.len();
            let page_items: Vec<SongInfo> = results
                .into_iter()
                .skip(offset as usize)
                .take(limit as usize)
                .map(|item| item.as_ref().clone())
                .collect();
            Ok(Json(build_song_page(page_items, total, limit, offset)).into_response())
        }
    } else if unique {
        let item = state.song_catalog.search_unique(q)?;
        Ok(Json::<SongInfo>(item.as_ref().clone()).into_response())
    } else {
        let (items, total) = state.song_catalog.search_page(q, offset, limit);
        let page_items: Vec<SongInfo> = items.iter().map(|a| a.as_ref().clone()).collect();
        Ok(Json(build_song_page(page_items, total, limit, offset)).into_response())
    }
}

fn build_catalog_response(state: &AppState) -> SongCatalogResponse {
    let items: Vec<SongInfo> = state
        .song_catalog
        .by_id
        .values()
        .map(|song| song.as_ref().clone())
        .collect();
    build_catalog_response_from_items(items)
}

fn build_catalog_response_from_items(mut items: Vec<SongInfo>) -> SongCatalogResponse {
    items.sort_by(|left, right| left.id.cmp(&right.id));
    let payload = serde_json::to_vec(&items).expect("serializing SongInfo cannot fail");
    let version = hex::encode(Sha256::digest(payload));
    SongCatalogResponse { version, items }
}

#[utoipa::path(
    get,
    path = "/songs/catalog",
    summary = "获取完整曲库",
    description = "返回服务器当前完整曲库及稳定内容版本。支持 If-None-Match，曲库未变化时返回 304。",
    responses(
        (status = 200, description = "完整曲库", body = SongCatalogResponse),
        (status = 304, description = "曲库内容未变化")
    ),
    tag = "Song"
)]
pub async fn get_song_catalog(
    State(state): State<AppState>,
    request_headers: HeaderMap,
) -> axum::response::Response {
    let catalog = build_catalog_response(&state);
    let etag = format!("\"{}\"", catalog.version);
    if request_headers
        .get(header::IF_NONE_MATCH)
        .and_then(|value| value.to_str().ok())
        .is_some_and(|value| value.split(',').any(|candidate| candidate.trim() == etag))
    {
        return (
            StatusCode::NOT_MODIFIED,
            [(
                header::ETAG,
                HeaderValue::from_str(&etag).expect("valid ETag"),
            )],
        )
            .into_response();
    }

    (
        StatusCode::OK,
        [
            (
                header::ETAG,
                HeaderValue::from_str(&etag).expect("valid ETag"),
            ),
            (header::CACHE_CONTROL, HeaderValue::from_static("no-cache")),
        ],
        Json(catalog),
    )
        .into_response()
}

pub fn create_song_router() -> Router<AppState> {
    Router::new()
        .route("/songs/search", get(search_songs))
        .route("/songs/catalog", get(get_song_catalog))
}

#[cfg(test)]
mod catalog_tests {
    use super::build_catalog_response_from_items;
    use crate::{features::song::models::SongInfo, startup::chart_loader::ChartConstants};

    fn song(id: &str, name: &str) -> SongInfo {
        SongInfo {
            id: id.to_string(),
            name: name.to_string(),
            composer: "Composer".to_string(),
            illustrator: "Illustrator".to_string(),
            chart_constants: ChartConstants {
                ez: Some(1.0),
                hd: Some(5.0),
                in_level: Some(10.0),
                at: None,
            },
        }
    }

    #[test]
    fn catalog_version_and_order_are_stable() {
        let left = build_catalog_response_from_items(vec![song("b", "B"), song("a", "A")]);
        let right = build_catalog_response_from_items(vec![song("a", "A"), song("b", "B")]);
        assert_eq!(left.version, right.version);
        assert_eq!(
            left.items
                .iter()
                .map(|item| item.id.as_str())
                .collect::<Vec<_>>(),
            vec!["a", "b"]
        );
    }

    #[test]
    fn catalog_version_changes_with_content() {
        let before = build_catalog_response_from_items(vec![song("a", "A")]);
        let after = build_catalog_response_from_items(vec![song("a", "Changed")]);
        assert_ne!(before.version, after.version);
    }
}
