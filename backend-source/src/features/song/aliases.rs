//! Administrator-maintained aliases are an overlay on the upstream nicklist.
//! Saving persists the overlay before publishing a new search snapshot.
use std::{
    collections::{BTreeMap, HashSet},
    fs::{self, File},
    io::{self, Write},
    path::Path,
    sync::Arc,
};

use axum::{
    Extension, Json, Router,
    extract::State,
    http::{HeaderMap, header},
    response::{IntoResponse, Response},
    routing::get,
};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use tokio::sync::Mutex;

use super::models::{SongCatalog, SongInfo};
use crate::{
    config::AppConfig,
    error::AppError,
    state::{AppState, Reloadable},
};

pub(crate) const OVERRIDES_FILE: &str = "song-alias-overrides.json";
// Share this lock with runtime/remote reloads so a save cannot publish an older snapshot.
static CATALOG_WRITE_LOCK: Mutex<()> = Mutex::const_new(());
pub(crate) fn catalog_write_lock() -> &'static Mutex<()> {
    &CATALOG_WRITE_LOCK
}
type AliasOverrides = BTreeMap<String, Vec<String>>;

#[derive(Debug, Clone, Serialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct AdminSongAliases {
    pub song_id: String,
    pub name: String,
    pub composer: String,
    pub aliases: Vec<String>,
    pub revision: String,
}

#[derive(Serialize, utoipa::ToSchema)]
pub struct AdminSongAliasList {
    pub items: Vec<AdminSongAliases>,
}

#[derive(Debug, Deserialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct UpdateSongAliasesRequest {
    pub song_id: String,
    pub aliases: Vec<String>,
    pub revision: String,
}

fn validate_aliases(aliases: &[String]) -> Result<Vec<String>, AppError> {
    if aliases.len() > 200 {
        return Err(AppError::Validation("每首曲目最多设置 200 个别名".into()));
    }
    let mut seen = HashSet::new();
    let mut cleaned = Vec::with_capacity(aliases.len());
    for alias in aliases {
        let value = alias.trim();
        if value.is_empty() || value.chars().count() > 128 || alias.chars().any(char::is_control) {
            return Err(AppError::Validation(
                "别名需为 1～128 个字符，且不能包含控制字符".into(),
            ));
        }
        if !seen.insert(value.to_string()) {
            return Err(AppError::Validation(format!("别名重复：{value}")));
        }
        cleaned.push(value.to_string());
    }
    cleaned.sort();
    Ok(cleaned)
}

fn read_overrides(info_dir: &Path) -> Result<AliasOverrides, AppError> {
    let path = info_dir.join(OVERRIDES_FILE);
    match File::open(&path) {
        Ok(file) => serde_json::from_reader(file)
            .map_err(|error| AppError::Internal(format!("读取曲目别名配置失败: {error}"))),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(BTreeMap::new()),
        Err(error) => Err(AppError::Internal(format!("读取曲目别名配置失败: {error}"))),
    }
}

fn apply_overrides(catalog: &mut SongCatalog, overrides: &AliasOverrides) -> Result<(), AppError> {
    for (song_id, aliases) in overrides {
        let aliases = validate_aliases(aliases)?;
        let Some(song) = catalog.by_id.get(song_id).cloned() else {
            continue;
        };
        for songs in catalog.by_nickname.values_mut() {
            songs.retain(|item| item.id != *song_id);
        }
        for alias in aliases {
            catalog
                .by_nickname
                .entry(alias)
                .or_default()
                .push(Arc::clone(&song));
        }
    }
    catalog.by_nickname.retain(|_, songs| !songs.is_empty());
    catalog.rebuild_search_cache();
    Ok(())
}

pub(crate) fn apply_saved_aliases(
    catalog: &mut SongCatalog,
    info_dir: &Path,
) -> Result<(), AppError> {
    apply_overrides(catalog, &read_overrides(info_dir)?)
}

pub fn describe_song(catalog: &SongCatalog, song: &SongInfo) -> AdminSongAliases {
    let mut aliases: Vec<String> = catalog
        .by_nickname
        .iter()
        .filter(|(_, songs)| songs.iter().any(|item| item.id == song.id))
        .map(|(alias, _)| alias.clone())
        .collect();
    aliases.sort();
    aliases.dedup();
    let revision = hex::encode(Sha256::digest(
        serde_json::to_vec(&(&song.id, &aliases)).expect("serializing aliases"),
    ));
    AdminSongAliases {
        song_id: song.id.clone(),
        name: song.name.clone(),
        composer: song.composer.clone(),
        aliases,
        revision,
    }
}

#[cfg(windows)]
fn replace_file(source: &Path, destination: &Path) -> io::Result<()> {
    use std::os::windows::ffi::OsStrExt;
    #[link(name = "kernel32")]
    unsafe extern "system" {
        fn MoveFileExW(existing: *const u16, new_name: *const u16, flags: u32) -> i32;
    }
    let source: Vec<u16> = source.as_os_str().encode_wide().chain(Some(0)).collect();
    let destination: Vec<u16> = destination
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect();
    // Replace the destination atomically; never delete the old file first.
    if unsafe { MoveFileExW(source.as_ptr(), destination.as_ptr(), 0x1 | 0x8) } == 0 {
        Err(io::Error::last_os_error())
    } else {
        Ok(())
    }
}

#[cfg(not(windows))]
fn replace_file(source: &Path, destination: &Path) -> io::Result<()> {
    fs::rename(source, destination)
}

fn write_overrides(info_dir: &Path, overrides: &AliasOverrides) -> Result<(), AppError> {
    let destination = info_dir.join(OVERRIDES_FILE);
    let temporary = info_dir.join(format!(
        "{OVERRIDES_FILE}.{}.tmp",
        uuid::Uuid::new_v4().simple()
    ));
    let bytes = serde_json::to_vec_pretty(overrides)
        .map_err(|error| AppError::Internal(format!("序列化曲目别名失败: {error}")))?;
    let result = (|| -> io::Result<()> {
        let mut file = File::create(&temporary)?;
        file.write_all(&bytes)?;
        file.sync_all()?;
        drop(file);
        replace_file(&temporary, &destination)
    })();
    if let Err(error) = result {
        let _ = fs::remove_file(&temporary);
        return Err(AppError::Internal(format!("保存曲目别名失败: {error}")));
    }
    Ok(())
}

pub async fn update_song_aliases(
    info_dir: &Path,
    target: &Reloadable<SongCatalog>,
    request: UpdateSongAliasesRequest,
) -> Result<AdminSongAliases, AppError> {
    let aliases = validate_aliases(&request.aliases)?;
    let _guard = catalog_write_lock().lock().await;
    // Read the latest on-disk base and overrides, including changes made since the GET.
    let mut catalog = crate::startup::song_loader::load_song_catalog(info_dir)?;
    let song = catalog
        .by_id
        .get(&request.song_id)
        .cloned()
        .ok_or_else(|| AppError::NotFound("曲目不存在".into()))?;
    let current = describe_song(&catalog, &song);
    if current.revision != request.revision {
        return Err(AppError::Conflict(
            "曲目别名已被修改，请刷新后核对再保存".into(),
        ));
    }
    let mut overrides = read_overrides(info_dir)?;
    overrides.insert(request.song_id, aliases);
    apply_overrides(&mut catalog, &overrides)?;
    let response = describe_song(&catalog, &song);
    write_overrides(info_dir, &overrides)?;
    target.replace(catalog);
    Ok(response)
}

fn no_store(value: impl Serialize) -> Response {
    ([(header::CACHE_CONTROL, "no-store")], Json(value)).into_response()
}

#[utoipa::path(get, path = "/admin/songs/aliases", tag = "Song",
    security(("AdminToken" = [])),
    responses((status = 200, description = "曲目及其当前别名", body = AdminSongAliasList),
              (status = 401, description = "需要管理员令牌")))]
pub async fn get_admin_song_aliases(
    State(state): State<AppState>,
    Extension(config): Extension<Arc<AppConfig>>,
    headers: HeaderMap,
) -> Result<Response, AppError> {
    crate::features::leaderboard::handler::admin::require_admin_with_cfg(&config, &headers)?;
    let catalog = state.song_catalog.snapshot();
    let mut items: Vec<_> = catalog
        .by_id
        .values()
        .map(|song| describe_song(&catalog, song))
        .collect();
    items.sort_by(|left, right| {
        left.name
            .cmp(&right.name)
            .then(left.song_id.cmp(&right.song_id))
    });
    Ok(no_store(AdminSongAliasList { items }))
}

#[utoipa::path(put, path = "/admin/songs/aliases", tag = "Song",
    security(("AdminToken" = [])), request_body = UpdateSongAliasesRequest,
    responses((status = 200, description = "已保存并同步搜索", body = AdminSongAliases),
              (status = 401, description = "需要管理员令牌"),
              (status = 404, description = "曲目不存在"),
              (status = 409, description = "别名版本冲突"),
              (status = 422, description = "别名无效")))]
pub async fn put_admin_song_aliases(
    State(state): State<AppState>,
    Extension(config): Extension<Arc<AppConfig>>,
    headers: HeaderMap,
    Json(request): Json<UpdateSongAliasesRequest>,
) -> Result<Response, AppError> {
    crate::features::leaderboard::handler::admin::require_admin_with_cfg(&config, &headers)?;
    let updated = update_song_aliases(&config.info_path(), &state.song_catalog, request).await?;
    Ok(no_store(updated))
}

pub fn router(config: AppConfig) -> Router<AppState> {
    Router::new()
        .route(
            "/admin/songs/aliases",
            get(get_admin_song_aliases).put(put_admin_song_aliases),
        )
        .layer(Extension(Arc::new(config)))
}
