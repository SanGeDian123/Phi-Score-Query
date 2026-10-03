//! 运行时曲库同步。
//!
//! 后端启动时会加载一次曲库，但服务器上的 `info` 文件可能在进程仍运行时
//! 被替换。这个模块定期检查本地文件和可选的远端 info 源，并在一套数据
//! 完整解析成功后原子切换共享状态。

use std::{
    path::{Path, PathBuf},
    time::Duration,
};

use sha2::{Digest, Sha256};

use crate::{
    error::AppError,
    features::song::models::SongCatalog,
    startup::{
        chart_loader::{ChartConstantsMap, load_chart_constants},
        remote_info::try_load_remote_info,
        song_loader::load_song_catalog,
    },
    state::Reloadable,
};

const SYNC_INTERVAL: Duration = Duration::from_secs(5);
const INFO_FILES: [&str; 3] = ["difficulty.csv", "info.csv", "nicklist.yaml"];

fn info_fingerprint(info_dir: &Path) -> Result<[u8; 32], AppError> {
    let mut hasher = Sha256::new();
    for file in INFO_FILES {
        let path = info_dir.join(file);
        let bytes = std::fs::read(&path).map_err(|error| {
            AppError::Internal(format!("读取曲库文件 {} 失败: {error}", path.display()))
        })?;
        hasher.update(file.as_bytes());
        hasher.update((bytes.len() as u64).to_le_bytes());
        hasher.update(bytes);
    }
    Ok(hasher.finalize().into())
}

fn load_local_snapshot(
    info_dir: &Path,
) -> Result<(ChartConstantsMap, SongCatalog), AppError> {
    let chart_constants = load_chart_constants(&info_dir.join("difficulty.csv"))?;
    let song_catalog = load_song_catalog(info_dir)?;
    Ok((chart_constants, song_catalog))
}

/// 启动后台曲库同步任务。
///
/// `try_load_remote_info` 负责远端 ETag 检查；即使远端不可达，也会继续
/// 检查本地文件，因此手工替换服务器上的 info 文件也能在不重启进程的
/// 情况下生效。
pub fn spawn(
    info_dir: PathBuf,
    info_base_url: Option<String>,
    chart_constants: Reloadable<ChartConstantsMap>,
    song_catalog: Reloadable<SongCatalog>,
) {
    tokio::spawn(async move {
        let mut last_fingerprint = info_fingerprint(&info_dir).ok();
        let mut ticker = tokio::time::interval(SYNC_INTERVAL);
        ticker.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);

        loop {
            ticker.tick().await;

            // 先尝试远端源。成功下载后 remote_info 会把三份文件持久化到
            // info 目录，进程重启也不会回退到旧文件。
            let mut remote_applied = false;
            if let Some(base_url) = info_base_url.as_deref() {
                match try_load_remote_info(base_url, &info_dir).await {
                    Ok(Some(remote)) => {
                        let remote_song_count = remote.song_catalog.by_id.len();
                        chart_constants.replace(remote.chart_constants);
                        song_catalog.replace(remote.song_catalog);
                        last_fingerprint = info_fingerprint(&info_dir).ok();
                        remote_applied = true;
                        tracing::info!(
                            song_count = remote_song_count,
                            "运行时远端曲库已同步"
                        );
                    }
                    Ok(None) => {}
                    Err(error) => {
                        tracing::warn!("运行时远端曲库同步失败，保留当前快照: {error}");
                    }
                }
            }

            if remote_applied {
                continue;
            }

            let fingerprint = match info_fingerprint(&info_dir) {
                Ok(value) => value,
                Err(error) => {
                    tracing::warn!("检查本地曲库变更失败，保留当前快照: {error}");
                    continue;
                }
            };
            if last_fingerprint == Some(fingerprint) {
                continue;
            }

            match load_local_snapshot(&info_dir) {
                Ok((new_chart_constants, new_song_catalog)) => {
                    let song_count = new_song_catalog.by_id.len();
                    chart_constants.replace(new_chart_constants);
                    song_catalog.replace(new_song_catalog);
                    last_fingerprint = Some(fingerprint);
                    tracing::info!(song_count, "检测到本地曲库变更，已热加载");
                }
                Err(error) => {
                    // 部署工具可能正在逐个复制文件；解析失败时不替换旧
                    // 快照，下一轮会重试，避免把半套数据暴露给请求。
                    tracing::warn!("本地曲库变更尚未完整，保留当前快照: {error}");
                }
            }
        }
    });
}
