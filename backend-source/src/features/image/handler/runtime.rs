use std::{sync::Arc, time::{Duration, Instant}};

use crate::{error::AppError, state::AppState};

pub(super) fn duration_ms_i64(duration: std::time::Duration) -> i64 {
    i64::try_from(duration.as_millis()).unwrap_or(i64::MAX)
}

fn i64_from_usize(value: usize) -> i64 {
    i64::try_from(value).unwrap_or(i64::MAX)
}

pub(super) fn blocking_join_error(error: tokio::task::JoinError) -> AppError {
    let error_text = error.to_string();
    if let Ok(panic) = error.try_into_panic() {
        std::panic::resume_unwind(panic);
    }
    AppError::Internal(format!("spawn_blocking cancelled: {error_text}"))
}

pub(super) fn track_image_event(
    stats: &crate::stats_contract::StatsHandle,
    route: &'static str,
    feature: &'static str,
    action: &'static str,
    duration_ms: Option<i64>,
    user_hash: Option<String>,
    extra_json: serde_json::Value,
) {
    stats.track(crate::stats_contract::EventInsert {
        ts_utc: chrono::Utc::now(),
        route: Some(route.into()),
        feature: Some(feature.into()),
        action: Some(action.into()),
        method: Some("POST".into()),
        status: None,
        duration_ms,
        user_hash,
        client_ip_hash: None,
        instance: None,
        extra_json: Some(extra_json),
    });
}

pub(super) struct RenderPermitTiming {
    pub(super) _permit: Arc<tokio::sync::OwnedSemaphorePermit>,
    pub(super) permits_avail: i64,
    pub(super) wait_ms: i64,
    pub(super) wait_elapsed: std::time::Duration,
}

pub(super) async fn acquire_render_permit(
    state: &AppState,
) -> Result<RenderPermitTiming, AppError> {
    let sem = state.render_semaphore.clone();
    acquire_permit_timing(sem, Duration::from_secs(10)).await
}

async fn acquire_permit_timing(sem: Arc<tokio::sync::Semaphore>, wait: Duration) -> Result<RenderPermitTiming, AppError> {
    let permits_avail = i64_from_usize(sem.available_permits());
    let started_at = Instant::now();
    let permit = tokio::time::timeout(wait, sem.acquire_owned())
        .await.map_err(|_| AppError::RenderBusy("图片生成繁忙，请稍后重试".into()))?
        .map_err(|e| AppError::Internal(format!("获取渲染信号量失败: {e}")))?;
    let wait_elapsed = started_at.elapsed();

    Ok(RenderPermitTiming {
        _permit: Arc::new(permit),
        permits_avail,
        wait_ms: duration_ms_i64(wait_elapsed),
        wait_elapsed,
    })
}

pub(super) async fn spawn_blocking_with_permit<T, F>(permit: &RenderPermitTiming, generate: F) -> Result<T, AppError>
where
    T: Send + 'static,
    F: FnOnce() -> Result<T, AppError> + Send + 'static,
{
    let permit = permit._permit.clone();
    tokio::task::spawn_blocking(move || {
        // Dropping the HTTP request cannot cancel an already-running blocking
        // task. Retain its slot until it really stops using CPU and memory.
        let _permit = permit;
        generate()
    })
        .await
        .map_err(|e| AppError::Internal(format!("阻塞图片任务执行失败: {e}")))?
}

pub(super) use spawn_blocking_with_permit as spawn_blocking_svg_generation;

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn full_render_queue_returns_503_and_keeps_existing_permit() {
        use axum::response::IntoResponse;
        let sem = Arc::new(tokio::sync::Semaphore::new(1));
        let active = sem.clone().acquire_owned().await.unwrap();
        let result = acquire_permit_timing(sem.clone(), Duration::from_millis(10)).await;
        let error = match result { Err(error) => error, Ok(_) => panic!("queue must be full") };
        let response = error.into_response();
        assert_eq!(response.status(), axum::http::StatusCode::SERVICE_UNAVAILABLE);
        assert_eq!(response.headers()[axum::http::header::RETRY_AFTER], "3");
        assert_eq!(sem.available_permits(), 0);
        drop(active);
        assert_eq!(sem.available_permits(), 1);
    }

    #[tokio::test]
    async fn cancelled_request_keeps_slot_until_blocking_render_finishes() {
        let sem = Arc::new(tokio::sync::Semaphore::new(1));
        let permit = RenderPermitTiming { _permit: Arc::new(sem.clone().acquire_owned().await.unwrap()),
            permits_avail: 1, wait_ms: 0, wait_elapsed: Duration::ZERO };
        let (started_tx, started_rx) = tokio::sync::oneshot::channel();
        let (finish_tx, finish_rx) = std::sync::mpsc::channel();
        let request = tokio::spawn(async move {
            spawn_blocking_with_permit(&permit, move || {
                let _ = started_tx.send(());
                finish_rx.recv().unwrap();
                Ok(())
            }).await
        });
        started_rx.await.unwrap();
        request.abort();
        assert!(request.await.unwrap_err().is_cancelled());
        assert_eq!(sem.available_permits(), 0, "cancelled HTTP must not admit another render");
        finish_tx.send(()).unwrap();
        let _next = tokio::time::timeout(Duration::from_secs(2), sem.acquire_owned()).await.unwrap().unwrap();
    }

    #[test]
    fn render_parallelism_is_memory_bounded_including_old_auto_configs() {
        let mut cfg = crate::config::ImageRenderConfig::default();
        for (value, expected) in [(0,1), (1,1), (2,2), (64,2)] {
            cfg.max_parallel = value;
            assert_eq!(cfg.render_parallelism(), expected);
        }
    }
}
