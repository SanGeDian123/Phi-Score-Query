use axum::body::Bytes;
use moka::future::Cache;
use std::sync::{Arc, RwLock};
use tokio::sync::Semaphore;

use crate::features::auth::{client::TapTapClient, qrcode_service::QrCodeService};
use crate::features::song::models::SongCatalog;
use crate::features::stats::StatsHandle;
use crate::startup::chart_loader::ChartConstantsMap;

/// 可在服务运行期间原子替换的数据快照。
///
/// 请求只持有当前 `Arc` 的只读快照，因此曲库更新不会让正在处理的请求
/// 看到半套数据；下一次请求会自动拿到新快照。
#[derive(Debug)]
pub struct Reloadable<T> {
    value: Arc<RwLock<Arc<T>>>,
}

impl<T> Clone for Reloadable<T> {
    fn clone(&self) -> Self {
        Self {
            value: Arc::clone(&self.value),
        }
    }
}

impl<T> Reloadable<T> {
    #[must_use]
    pub fn new(value: T) -> Self {
        Self::from_arc(Arc::new(value))
    }

    #[must_use]
    pub fn from_arc(value: Arc<T>) -> Self {
        Self {
            value: Arc::new(RwLock::new(value)),
        }
    }

    /// 获取一个独立的、在本次请求期间稳定的快照。
    #[must_use]
    pub fn snapshot(&self) -> Arc<T> {
        self.value
            .read()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .clone()
    }

    /// 原子切换后续请求使用的数据。
    pub fn replace(&self, value: T) {
        self.replace_arc(Arc::new(value));
    }

    pub fn replace_arc(&self, value: Arc<T>) {
        *self
            .value
            .write()
            .unwrap_or_else(std::sync::PoisonError::into_inner) = value;
    }
}

/// 聚合的应用共享状态
#[derive(Clone)]
pub struct AppState {
    pub chart_constants: Reloadable<ChartConstantsMap>,
    pub song_catalog: Reloadable<SongCatalog>,
    pub taptap_client: Arc<TapTapClient>,
    pub qrcode_service: Arc<QrCodeService>,
    pub stats: Option<StatsHandle>,
    pub stats_storage: Option<Arc<crate::features::stats::storage::StatsStorage>>,
    /// 控制并发渲染的信号量（限制 CPU 密集型任务数量）
    pub render_semaphore: Arc<Semaphore>,
    /// BN 图片缓存（按图片字节大小加权）
    pub bn_image_cache: Cache<String, Bytes>,
    /// 单曲图片缓存（按图片字节大小加权）
    pub song_image_cache: Cache<String, Bytes>,
}
