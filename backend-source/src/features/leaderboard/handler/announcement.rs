use std::{
    cmp::Ordering,
    collections::HashSet,
    fs::{self, File},
    io::{self, Write},
    path::{Path, PathBuf},
    sync::{Mutex, MutexGuard},
};
#[cfg(windows)]
use std::{
    fs::OpenOptions,
    thread,
    time::{Duration, Instant},
};

use axum::{
    Json,
    extract::{Path as AxumPath, State},
    http::{HeaderMap, StatusCode},
};
use chrono::{DateTime, FixedOffset, Local};
use once_cell::sync::Lazy;
use serde::{Deserialize, Serialize, de::DeserializeOwned};
use uuid::Uuid;

use crate::{error::AppError, state::AppState};

use super::admin::require_admin;

const MAX_ID_CHARS: usize = 128;
const MAX_TITLE_CHARS: usize = 120;
const MAX_BODY_CHARS: usize = 8_000;

static ANNOUNCEMENT_IO_LOCK: Lazy<Mutex<()>> = Lazy::new(|| Mutex::new(()));

struct AnnouncementIoGuard {
    _process_guard: MutexGuard<'static, ()>,
    #[cfg(windows)]
    _file_guard: File,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct AdminAnnouncement {
    pub id: String,
    pub title: String,
    pub body: String,
    pub published_at: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize, utoipa::ToSchema, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct AnnouncementList {
    pub items: Vec<AdminAnnouncement>,
}

#[derive(Debug, Deserialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct PublishAnnouncementRequest {
    pub title: String,
    pub body: String,
}

fn announcement_root() -> PathBuf {
    std::env::var_os("APP_ANNOUNCEMENT_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("resources/app-announcement"))
}

fn lock_announcement_io(root: &Path) -> Result<AnnouncementIoGuard, AppError> {
    let process_guard = ANNOUNCEMENT_IO_LOCK
        .lock()
        .map_err(|_| AppError::Internal("公告文件锁已损坏".into()))?;

    #[cfg(windows)]
    let file_guard = {
        use std::os::windows::fs::OpenOptionsExt;

        fs::create_dir_all(root)
            .map_err(|error| AppError::Internal(format!("创建公告目录失败: {error}")))?;
        let lock_path = root.with_extension("lock");
        let deadline = Instant::now() + Duration::from_secs(10);
        loop {
            match OpenOptions::new()
                .read(true)
                .write(true)
                .create(true)
                .share_mode(0)
                .open(&lock_path)
            {
                Ok(file) => break file,
                Err(error)
                    if matches!(error.raw_os_error(), Some(32 | 33))
                        && Instant::now() < deadline =>
                {
                    thread::sleep(Duration::from_millis(25));
                }
                Err(error) => {
                    return Err(AppError::Internal(format!(
                        "获取公告跨进程文件锁失败: {error}"
                    )));
                }
            }
        }
    };

    Ok(AnnouncementIoGuard {
        _process_guard: process_guard,
        #[cfg(windows)]
        _file_guard: file_guard,
    })
}

fn validate_text(value: String, field: &str, max_chars: usize) -> Result<String, AppError> {
    let value = value.trim().to_string();
    let count = value.chars().count();
    if count == 0 || count > max_chars {
        return Err(AppError::Validation(format!(
            "{field} 长度必须为 1-{max_chars} 个字符"
        )));
    }
    Ok(value)
}

fn build_announcement(request: PublishAnnouncementRequest) -> Result<AdminAnnouncement, AppError> {
    let now = Local::now();
    let id = format!(
        "{}-{}",
        now.format("%Y%m%d-%H%M%S-%3f"),
        Uuid::now_v7().simple()
    );
    let id = validate_text(id, "id", MAX_ID_CHARS)?;
    let title = validate_text(request.title, "title", MAX_TITLE_CHARS)?;
    let body = validate_text(request.body, "body", MAX_BODY_CHARS)?;
    Ok(AdminAnnouncement {
        id,
        title,
        body,
        published_at: now.format("%Y-%m-%d %H:%M:%S %:z").to_string(),
    })
}

fn parse_published_at(value: &str) -> Option<DateTime<FixedOffset>> {
    DateTime::parse_from_str(value, "%Y-%m-%d %H:%M:%S %:z")
        .or_else(|_| DateTime::parse_from_rfc3339(value))
        .ok()
}

fn compare_newest_first(left: &AdminAnnouncement, right: &AdminAnnouncement) -> Ordering {
    match (
        parse_published_at(&left.published_at),
        parse_published_at(&right.published_at),
    ) {
        (Some(left_time), Some(right_time)) => right_time
            .cmp(&left_time)
            .then_with(|| right.id.cmp(&left.id)),
        (Some(_), None) => Ordering::Less,
        (None, Some(_)) => Ordering::Greater,
        (None, None) => right
            .published_at
            .cmp(&left.published_at)
            .then_with(|| right.id.cmp(&left.id)),
    }
}

fn sort_announcements(items: &mut [AdminAnnouncement]) {
    items.sort_by(compare_newest_first);
}

#[cfg(windows)]
fn replace_file_atomically(source: &Path, destination: &Path) -> io::Result<()> {
    use std::os::windows::ffi::OsStrExt;

    #[link(name = "kernel32")]
    unsafe extern "system" {
        fn MoveFileExW(existing: *const u16, new_name: *const u16, flags: u32) -> i32;
    }

    const MOVEFILE_REPLACE_EXISTING: u32 = 0x1;
    const MOVEFILE_WRITE_THROUGH: u32 = 0x8;

    let source = source
        .as_os_str()
        .encode_wide()
        .chain(std::iter::once(0))
        .collect::<Vec<_>>();
    let destination = destination
        .as_os_str()
        .encode_wide()
        .chain(std::iter::once(0))
        .collect::<Vec<_>>();
    let replaced = unsafe {
        MoveFileExW(
            source.as_ptr(),
            destination.as_ptr(),
            MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH,
        )
    };
    if replaced == 0 {
        Err(io::Error::last_os_error())
    } else {
        Ok(())
    }
}

#[cfg(not(windows))]
fn replace_file_atomically(source: &Path, destination: &Path) -> io::Result<()> {
    fs::rename(source, destination)
}

fn write_json_atomically<T: Serialize>(
    root: &Path,
    file_name: &str,
    value: &T,
) -> Result<(), AppError> {
    fs::create_dir_all(root)
        .map_err(|error| AppError::Internal(format!("创建公告目录失败: {error}")))?;
    let destination = root.join(file_name);
    let temporary = root.join(format!("{file_name}.{}.upload", Uuid::new_v4().simple()));
    let json = serde_json::to_vec_pretty(value)
        .map_err(|error| AppError::Internal(format!("序列化公告失败: {error}")))?;

    let result = (|| -> io::Result<()> {
        let mut file = File::create(&temporary)?;
        file.write_all(&json)?;
        file.sync_all()?;
        replace_file_atomically(&temporary, &destination)
    })();

    if let Err(error) = result {
        let _ = fs::remove_file(&temporary);
        return Err(AppError::Internal(format!(
            "写入 {file_name} 失败: {error}"
        )));
    }
    Ok(())
}

fn read_optional_json<T: DeserializeOwned>(
    path: &Path,
    label: &str,
) -> Result<Option<T>, AppError> {
    let bytes = match fs::read(path) {
        Ok(bytes) => bytes,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(AppError::Internal(format!("读取{label}失败: {error}"))),
    };
    serde_json::from_slice(&bytes)
        .map(Some)
        .map_err(|error| AppError::Internal(format!("解析{label}失败: {error}")))
}

fn load_announcements(root: &Path) -> Result<(AnnouncementList, bool), AppError> {
    let stored_index: Option<AnnouncementList> =
        read_optional_json(&root.join("index.json"), "公告历史清单")?;
    let stored_latest: Option<AdminAnnouncement> =
        read_optional_json(&root.join("latest.json"), "最新公告")?;
    let mut needs_reconcile = stored_index.is_none();
    // index.json 一旦存在就是公告历史的唯一权威来源。latest.json 只在首次迁移时导入；
    // 否则删除操作若在写完 index 后、删除 latest 前中断，旧公告会在重启后被错误复活。
    let mut list = stored_index.unwrap_or_else(|| AnnouncementList {
        items: stored_latest.iter().cloned().collect(),
    });

    let mut ids = HashSet::with_capacity(list.items.len());
    for item in &list.items {
        if !ids.insert(item.id.as_str()) {
            return Err(AppError::Internal(format!(
                "公告历史清单包含重复 ID: {}",
                item.id
            )));
        }
    }

    let previous_order = list.items.clone();
    sort_announcements(&mut list.items);
    if list.items != previous_order {
        needs_reconcile = true;
    }

    if stored_latest.as_ref() != list.items.first() {
        needs_reconcile = true;
    }

    Ok((list, needs_reconcile))
}

fn write_announcements(root: &Path, list: &AnnouncementList) -> Result<(), AppError> {
    let mut sorted = list.clone();
    sort_announcements(&mut sorted.items);

    // 先落完整历史，再切换 latest。若进程在两步之间退出，下次启动会自动修复 latest。
    write_json_atomically(root, "index.json", &sorted)?;
    if let Some(latest) = sorted.items.first() {
        write_json_atomically(root, "latest.json", latest)?;
    } else {
        match fs::remove_file(root.join("latest.json")) {
            Ok(()) => {}
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            Err(error) => {
                return Err(AppError::Internal(format!(
                    "删除空的最新公告清单失败: {error}"
                )));
            }
        }
    }
    Ok(())
}

fn load_and_reconcile(root: &Path) -> Result<AnnouncementList, AppError> {
    let (list, needs_reconcile) = load_announcements(root)?;
    if needs_reconcile {
        write_announcements(root, &list)?;
    }
    Ok(list)
}

fn publish_announcement(
    root: &Path,
    request: PublishAnnouncementRequest,
) -> Result<AdminAnnouncement, AppError> {
    let announcement = build_announcement(request)?;
    let (mut list, _) = load_announcements(root)?;
    if list.items.iter().any(|item| item.id == announcement.id) {
        return Err(AppError::Conflict("公告 ID 已存在，请重试".into()));
    }
    list.items.push(announcement.clone());
    write_announcements(root, &list)?;
    Ok(announcement)
}

fn update_announcement(
    root: &Path,
    id: String,
    request: PublishAnnouncementRequest,
) -> Result<AdminAnnouncement, AppError> {
    let id = validate_text(id, "id", MAX_ID_CHARS)?;
    let title = validate_text(request.title, "title", MAX_TITLE_CHARS)?;
    let body = validate_text(request.body, "body", MAX_BODY_CHARS)?;
    let (mut list, _) = load_announcements(root)?;
    let announcement = list
        .items
        .iter_mut()
        .find(|item| item.id == id)
        .ok_or_else(|| AppError::NotFound(format!("公告不存在: {id}")))?;
    announcement.title = title;
    announcement.body = body;
    let updated = announcement.clone();
    write_announcements(root, &list)?;
    Ok(updated)
}

fn delete_announcement(root: &Path, id: String) -> Result<(), AppError> {
    let id = validate_text(id, "id", MAX_ID_CHARS)?;
    let (mut list, _) = load_announcements(root)?;
    if list.items.first().is_some_and(|item| item.id == id) {
        return Err(AppError::Conflict(
            "最新公告不能删除，请先发布新公告或编辑现有内容".into(),
        ));
    }
    let Some(index) = list.items.iter().position(|item| item.id == id) else {
        return Err(AppError::NotFound(format!("公告不存在: {id}")));
    };
    list.items.remove(index);
    write_announcements(root, &list)
}

/// 在后端启动时将旧版仅有的 latest.json 迁入 index.json，并修复两份清单的一致性。
pub fn ensure_announcement_index() -> Result<(), AppError> {
    let root = announcement_root();
    let _guard = lock_announcement_io(&root)?;
    load_and_reconcile(&root).map(|_| ())
}

#[utoipa::path(
    get,
    path = "/admin/announcements",
    summary = "读取全部 APP 公告（按发布时间倒序）",
    responses((status = 200, body = AnnouncementList)),
    security(("AdminToken" = [])),
    tag = "Leaderboard"
)]
pub async fn get_admin_announcements(
    State(_state): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<AnnouncementList>, AppError> {
    require_admin(&headers)?;
    let root = announcement_root();
    let _guard = lock_announcement_io(&root)?;
    Ok(Json(load_and_reconcile(&root)?))
}

#[utoipa::path(
    post,
    path = "/admin/announcements",
    summary = "发布一条 APP 公告并保留历史",
    request_body = PublishAnnouncementRequest,
    responses((status = 200, body = AdminAnnouncement)),
    security(("AdminToken" = [])),
    tag = "Leaderboard"
)]
pub async fn post_admin_announcements(
    State(_state): State<AppState>,
    headers: HeaderMap,
    Json(request): Json<PublishAnnouncementRequest>,
) -> Result<Json<AdminAnnouncement>, AppError> {
    require_admin(&headers)?;
    let root = announcement_root();
    let _guard = lock_announcement_io(&root)?;
    Ok(Json(publish_announcement(&root, request)?))
}

#[utoipa::path(
    put,
    path = "/admin/announcements/{id}",
    summary = "编辑指定 APP 公告",
    params(("id" = String, Path, description = "公告 ID")),
    request_body = PublishAnnouncementRequest,
    responses(
        (status = 200, body = AdminAnnouncement),
        (status = 404, description = "公告不存在", body = crate::error::ProblemDetails, content_type = "application/problem+json")
    ),
    security(("AdminToken" = [])),
    tag = "Leaderboard"
)]
pub async fn put_admin_announcement(
    State(_state): State<AppState>,
    headers: HeaderMap,
    AxumPath(id): AxumPath<String>,
    Json(request): Json<PublishAnnouncementRequest>,
) -> Result<Json<AdminAnnouncement>, AppError> {
    require_admin(&headers)?;
    let root = announcement_root();
    let _guard = lock_announcement_io(&root)?;
    Ok(Json(update_announcement(&root, id, request)?))
}

#[utoipa::path(
    delete,
    path = "/admin/announcements/{id}",
    summary = "删除指定 APP 公告",
    params(("id" = String, Path, description = "公告 ID")),
    responses(
        (status = 204, description = "公告已删除"),
        (status = 409, description = "最新公告不可删除", body = crate::error::ProblemDetails, content_type = "application/problem+json"),
        (status = 404, description = "公告不存在", body = crate::error::ProblemDetails, content_type = "application/problem+json")
    ),
    security(("AdminToken" = [])),
    tag = "Leaderboard"
)]
pub async fn delete_admin_announcement(
    State(_state): State<AppState>,
    headers: HeaderMap,
    AxumPath(id): AxumPath<String>,
) -> Result<StatusCode, AppError> {
    require_admin(&headers)?;
    let root = announcement_root();
    let _guard = lock_announcement_io(&root)?;
    delete_announcement(&root, id)?;
    Ok(StatusCode::NO_CONTENT)
}

#[utoipa::path(
    get,
    path = "/admin/announcement",
    summary = "读取当前 APP 公告（兼容旧管理端）",
    security(("AdminToken" = [])),
    tag = "Leaderboard"
)]
pub async fn get_admin_announcement(
    State(_state): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<Option<AdminAnnouncement>>, AppError> {
    require_admin(&headers)?;
    let root = announcement_root();
    let _guard = lock_announcement_io(&root)?;
    let list = load_and_reconcile(&root)?;
    Ok(Json(list.items.into_iter().next()))
}

#[utoipa::path(
    post,
    path = "/admin/announcement",
    summary = "发布 APP 公告（兼容旧管理端）",
    request_body = PublishAnnouncementRequest,
    security(("AdminToken" = [])),
    tag = "Leaderboard"
)]
pub async fn post_admin_announcement(
    State(_state): State<AppState>,
    headers: HeaderMap,
    Json(request): Json<PublishAnnouncementRequest>,
) -> Result<Json<AdminAnnouncement>, AppError> {
    require_admin(&headers)?;
    let root = announcement_root();
    let _guard = lock_announcement_io(&root)?;
    Ok(Json(publish_announcement(&root, request)?))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn temp_root(label: &str) -> PathBuf {
        std::env::temp_dir().join(format!("psq-admin-announcement-{label}-{}", Uuid::new_v4()))
    }

    fn item(id: &str, title: &str, published_at: &str) -> AdminAnnouncement {
        AdminAnnouncement {
            id: id.to_string(),
            title: title.to_string(),
            body: format!("{title}正文"),
            published_at: published_at.to_string(),
        }
    }

    #[test]
    fn validates_and_trims_announcement_content() {
        let announcement = build_announcement(PublishAnnouncementRequest {
            title: "  公告标题  ".to_string(),
            body: "  公告正文  ".to_string(),
        })
        .expect("valid announcement");
        assert_eq!(announcement.title, "公告标题");
        assert_eq!(announcement.body, "公告正文");

        let error = build_announcement(PublishAnnouncementRequest {
            title: " ".to_string(),
            body: "正文".to_string(),
        })
        .expect_err("blank title must fail");
        assert!(matches!(error, AppError::Validation(_)));
    }

    #[test]
    fn migrates_legacy_latest_into_public_index() {
        let root = temp_root("legacy");
        let legacy = item("legacy", "旧公告", "2026-08-28 10:00:00 +08:00");
        write_json_atomically(&root, "latest.json", &legacy).expect("write legacy latest");

        let list = load_and_reconcile(&root).expect("migrate legacy latest");

        assert_eq!(list.items, vec![legacy.clone()]);
        let index: AnnouncementList =
            serde_json::from_slice(&fs::read(root.join("index.json")).expect("read index"))
                .expect("parse index");
        assert_eq!(index.items, vec![legacy]);
        fs::remove_dir_all(root).expect("clean temp root");
    }

    #[test]
    fn existing_index_does_not_resurrect_stale_latest() {
        let root = temp_root("stale-latest");
        let retained = item("retained", "保留公告", "2026-08-28 10:00:00 +08:00");
        let deleted = item("deleted", "已删公告", "2026-08-30 10:00:00 +08:00");
        write_json_atomically(
            &root,
            "index.json",
            &AnnouncementList {
                items: vec![retained.clone()],
            },
        )
        .expect("write canonical index");
        write_json_atomically(&root, "latest.json", &deleted).expect("write stale latest");

        let list = load_and_reconcile(&root).expect("reconcile stale latest");

        assert_eq!(list.items, vec![retained.clone()]);
        let latest: AdminAnnouncement =
            serde_json::from_slice(&fs::read(root.join("latest.json")).expect("read latest"))
                .expect("parse latest");
        assert_eq!(latest, retained);
        fs::remove_dir_all(root).expect("clean temp root");
    }

    #[test]
    fn retains_all_announcements_and_sorts_newest_first() {
        let root = temp_root("history");
        let oldest = item("20260828-100000", "第一条", "2026-08-28 10:00:00 +08:00");
        let newest = item("20260830-100000", "第三条", "2026-08-30 10:00:00 +08:00");
        let middle = item("20260829-100000", "第二条", "2026-08-29 10:00:00 +08:00");
        let list = AnnouncementList {
            items: vec![oldest.clone(), newest.clone(), middle.clone()],
        };

        write_announcements(&root, &list).expect("write history");
        let stored = load_and_reconcile(&root).expect("read history");
        assert_eq!(stored.items, vec![newest.clone(), middle, oldest]);
        let latest: AdminAnnouncement =
            serde_json::from_slice(&fs::read(root.join("latest.json")).expect("read latest"))
                .expect("parse latest");
        assert_eq!(latest, newest);
        fs::remove_dir_all(root).expect("clean temp root");
    }

    #[test]
    fn edit_and_delete_keep_latest_compatible() {
        let root = temp_root("crud");
        let older = item("older", "旧公告", "2026-08-28 10:00:00 +08:00");
        let newest = item("newest", "新公告", "2026-08-30 10:00:00 +08:00");
        write_announcements(
            &root,
            &AnnouncementList {
                items: vec![older.clone(), newest.clone()],
            },
        )
        .expect("write initial history");

        let edited = update_announcement(
            &root,
            newest.id.clone(),
            PublishAnnouncementRequest {
                title: "  已编辑  ".into(),
                body: "  新正文  ".into(),
            },
        )
        .expect("edit newest");
        assert_eq!(edited.title, "已编辑");
        let latest_after_edit: AdminAnnouncement = serde_json::from_slice(
            &fs::read(root.join("latest.json")).expect("read latest after edit"),
        )
        .expect("parse latest after edit");
        assert_eq!(latest_after_edit, edited);

        let delete_latest_error =
            delete_announcement(&root, newest.id.clone()).expect_err("latest must not be deleted");
        assert!(matches!(delete_latest_error, AppError::Conflict(_)));
        let latest_after_rejected_delete: AdminAnnouncement = serde_json::from_slice(
            &fs::read(root.join("latest.json")).expect("read latest after rejected delete"),
        )
        .expect("parse latest after rejected delete");
        assert_eq!(latest_after_rejected_delete, edited);

        delete_announcement(&root, older.id).expect("delete historical announcement");
        let remaining: AnnouncementList = serde_json::from_slice(
            &fs::read(root.join("index.json")).expect("read remaining index"),
        )
        .expect("parse remaining index");
        assert_eq!(remaining.items, vec![edited]);
        let latest_after_history_delete: AdminAnnouncement = serde_json::from_slice(
            &fs::read(root.join("latest.json")).expect("read latest after history delete"),
        )
        .expect("parse latest after history delete");
        assert_eq!(latest_after_history_delete, remaining.items[0]);
        fs::remove_dir_all(root).expect("clean temp root");
    }
}
