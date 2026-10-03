//! On-demand, bounded previews. Originals remain available at the same URL without ?preview=1.
use std::{io::Cursor, path::{Path, PathBuf}, sync::{Arc, OnceLock}, time::Duration};
use axum::{body::{Body, Bytes}, extract::{Request, State}, http::{header, StatusCode}, middleware::Next, response::{IntoResponse, Response}};
use image::{ImageReader, imageops::FilterType};
use moka::future::Cache;
use tokio::sync::Semaphore;
use sha2::{Digest, Sha256};

#[derive(Clone, Copy, Debug)]
enum Profile { Artwork, Thumbnail, Suggestion }

#[derive(Clone, Debug, PartialEq, Eq)]
struct Preview { bytes: Bytes, etag: String }
impl Preview {
    fn new(bytes: Vec<u8>) -> Self {
        let etag = format!("\"{}\"", hex::encode(Sha256::digest(&bytes)));
        Self { bytes: Bytes::from(bytes), etag }
    }
}

#[derive(Clone)]
pub struct PreviewRoots { pub illustrations: PathBuf, pub suggestions: PathBuf }

fn cache() -> &'static Cache<String, Preview> {
    static CACHE: OnceLock<Cache<String, Preview>> = OnceLock::new();
    CACHE.get_or_init(|| Cache::builder().max_capacity(32 * 1024 * 1024)
        .weigher(|_: &String, value: &Preview| value.bytes.len().min(u32::MAX as usize) as u32)
        .time_to_idle(Duration::from_secs(600)).build())
}

fn workers() -> &'static Arc<Semaphore> {
    static WORKERS: OnceLock<Arc<Semaphore>> = OnceLock::new();
    WORKERS.get_or_init(|| Arc::new(Semaphore::new(2)))
}

fn safe_name(name: &str) -> bool {
    !name.is_empty() && !name.starts_with('.') && !name.contains(['/', '\\', ':', '\0'])
        && Path::new(name).extension().and_then(|s| s.to_str())
            .is_some_and(|s| matches!(s.to_ascii_lowercase().as_str(), "png" | "jpg" | "jpeg" | "webp"))
}

fn encode_preview(raw: &[u8], profile: Profile) -> Result<Vec<u8>, String> {
    let mut reader = ImageReader::new(Cursor::new(raw)).with_guessed_format().map_err(|e| e.to_string())?;
    let mut limits = image::Limits::default();
    limits.max_image_width = Some(16384);
    limits.max_image_height = Some(16384);
    limits.max_alloc = Some(192 * 1024 * 1024);
    reader.limits(limits);
    let decoded = reader.decode().map_err(|e| e.to_string())?;
    let (width, height) = match profile {
        Profile::Artwork => (1280, 720), Profile::Thumbnail => (720, 405), Profile::Suggestion => (1440, 8192),
    };
    let scaled = if decoded.width() > width || decoded.height() > height {
        decoded.resize(width, height, FilterType::Triangle)
    } else { decoded };
    // High-quality WebP retains score text; do not discard the full-resolution source.
    let rgba = scaled.to_rgba8();
    Ok(webp::Encoder::from_rgba(rgba.as_raw(), rgba.width(), rgba.height()).encode(90.0).to_vec())
}

async fn preview(path: PathBuf, profile: Profile) -> Result<Preview, String> {
    // Check existence even on cache hits, so deleted suggestion media stops being served.
    let meta = tokio::fs::metadata(&path).await.map_err(|e| e.to_string())?;
    if !meta.is_file() || meta.len() > 32 * 1024 * 1024 { return Err("invalid media size".into()); }
    let modified = meta.modified().map_err(|e| e.to_string())?.duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos();
    let key = format!("{}:{}:{modified}:{profile:?}", path.display(), meta.len());
    cache().try_get_with(key, async move {
        let sidecar_dir = path.parent().ok_or("missing parent")?.join(".previews-v2");
        let name = path.file_name().ok_or("missing name")?.to_string_lossy();
        // Stable filename bounds disk usage to one preview per original, even after artwork changes.
        let sidecar = sidecar_dir.join(format!("{name}.webp"));
        let stamp = sidecar_dir.join(format!("{name}.stamp"));
        let version = format!("{}:{modified}:{profile:?}", meta.len());
        if tokio::fs::read_to_string(&stamp).await.ok().as_deref() == Some(version.as_str()) {
            if let Ok(bytes) = tokio::fs::read(&sidecar).await { if !bytes.is_empty() { return Ok(Preview::new(bytes)); } }
        }
        let permit = tokio::time::timeout(Duration::from_secs(3), workers().clone().acquire_owned())
            .await.map_err(|_| "preview busy")?.map_err(|_| "preview unavailable")?;
        let raw = tokio::fs::read(&path).await.map_err(|e| e.to_string())?;
        let bytes = tokio::task::spawn_blocking(move || { let _permit = permit; encode_preview(&raw, profile) })
            .await.map_err(|e| e.to_string())??;
        // A read-only resource directory must not prevent returning the generated image.
        if tokio::fs::create_dir_all(&sidecar_dir).await.is_ok() {
            let temporary = sidecar_dir.join(format!("{}.tmp", uuid::Uuid::new_v4()));
            if tokio::fs::write(&temporary, &bytes).await.is_ok() {
                if tokio::fs::rename(&temporary, &sidecar).await.is_ok() { let _ = tokio::fs::write(&stamp, &version).await; }
                else { let _ = tokio::fs::remove_file(&temporary).await; }
            }
        }
        Ok::<Preview, String>(Preview::new(bytes))
    }).await.map_err(|e| e.to_string())
}

pub async fn middleware(State(roots): State<PreviewRoots>, req: Request, next: Next) -> Response {
    let requested = req.uri().query().is_some_and(|q| q.split('&').any(|p| p == "preview=1"));
    let path = req.uri().path();
    let target = path.strip_prefix("/_ill/ill/").map(|n| (roots.illustrations.join("ill"), n, Profile::Artwork))
        .or_else(|| path.strip_prefix("/_ill/illLow/").map(|n| (roots.illustrations.join("illLow"), n, Profile::Thumbnail)))
        .or_else(|| path.strip_prefix("/suggestion-media/").map(|n| (roots.suggestions.clone(), n, Profile::Suggestion)));
    if !requested || target.is_none() || !matches!(*req.method(), axum::http::Method::GET | axum::http::Method::HEAD) { return next.run(req).await; }
    let head = *req.method() == axum::http::Method::HEAD;
    let (root, name, profile) = target.unwrap();
    let name = match percent_encoding::percent_decode_str(name).decode_utf8() { Ok(n) if safe_name(&n) => n.into_owned(), _ => return (StatusCode::BAD_REQUEST, [(header::CACHE_CONTROL, "no-store")]).into_response() };
    let started = std::time::Instant::now();
    match preview(root.join(name), profile).await {
        Ok(value) => {
            let not_modified = req.headers().get(header::IF_NONE_MATCH).and_then(|h| h.to_str().ok())
                .is_some_and(|h| h.split(',').any(|v| v.trim().trim_start_matches("W/") == value.etag || v.trim() == "*"));
            tracing::debug!(target: "phi_backend::media::performance", elapsed_ms = started.elapsed().as_millis(), bytes = value.bytes.len(), ?profile, "preview ready");
            let response = Response::builder().header(header::ETAG, &value.etag)
                .header(header::CACHE_CONTROL, "public, max-age=86400")
                .header(header::CONTENT_TYPE, "image/webp");
            if not_modified { return response.status(StatusCode::NOT_MODIFIED).body(Body::empty()).unwrap(); }
            response.header(header::CONTENT_LENGTH, value.bytes.len())
                .body(if head { Body::empty() } else { Body::from(value.bytes) }).unwrap()
        }
        Err(error) => {
            tracing::warn!(target: "phi_backend::media::performance", %error, ?profile, "preview unavailable; serving original");
            // Unsupported or temporarily busy previews retain the original serving behavior.
            let mut response = next.run(req).await;
            response.headers_mut().insert(header::CACHE_CONTROL, "no-store".parse().unwrap());
            response
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_paths_outside_media_directory() {
        for name in ["../secret.png", "..\\secret.png", "C:secret.png", ".hidden.png", "foo/bar.png", "a.txt"] { assert!(!safe_name(name)); }
        assert!(safe_name("Glaciaxion.SunsetRay.png"));
    }
    #[test]
    fn artwork_preview_reduces_dimensions_and_bytes() {
        let img = image::RgbImage::from_fn(1920, 1080, |x,y| image::Rgb([(x % 256) as u8, (y % 256) as u8, ((x+y) % 256) as u8]));
        let mut raw = Cursor::new(Vec::new());
        img.write_to(&mut raw, image::ImageFormat::Png).unwrap();
        let encoded = encode_preview(raw.get_ref(), Profile::Artwork).unwrap();
        let result = image::load_from_memory(&encoded).unwrap();
        assert_eq!((result.width(), result.height()), (1280, 720));
        assert!(encoded.len() < raw.get_ref().len());
    }
    #[tokio::test]
    async fn cache_does_not_serve_deleted_media() {
        let dir = std::env::temp_dir().join(format!("preview-test-{}", uuid::Uuid::new_v4()));
        tokio::fs::create_dir(&dir).await.unwrap();
        let path = dir.join("test.png");
        image::RgbImage::new(20, 10).save(&path).unwrap();
        let first = preview(path.clone(), Profile::Suggestion).await.unwrap();
        assert_eq!(first, preview(path.clone(), Profile::Suggestion).await.unwrap());
        tokio::fs::remove_file(&path).await.unwrap();
        assert!(preview(path, Profile::Suggestion).await.is_err());
        tokio::fs::remove_dir_all(dir).await.unwrap();
    }

    #[tokio::test]
    async fn thumbnail_head_and_conditional_get() {
        use tower::ServiceExt;
        let dir = std::env::temp_dir().join(format!("thumbnail-test-{}", uuid::Uuid::new_v4()));
        tokio::fs::create_dir_all(dir.join("illLow")).await.unwrap();
        image::RgbImage::new(1440, 810).save(dir.join("illLow/test.png")).unwrap();
        let app = axum::Router::new()
            .nest_service("/_ill/illLow", tower_http::services::ServeDir::new(dir.join("illLow")))
            .layer(axum::middleware::from_fn_with_state(PreviewRoots { illustrations: dir.clone(), suggestions: dir.clone() }, middleware));
        let uri = "/_ill/illLow/test.png?preview=1";
        let response = app.clone().oneshot(Request::builder().uri(uri).body(Body::empty()).unwrap()).await.unwrap();
        let etag = response.headers()[header::ETAG].clone();
        let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024).await.unwrap();
        let decoded = image::load_from_memory(&bytes).unwrap();
        assert_eq!((decoded.width(), decoded.height()), (720, 405));
        let head = app.clone().oneshot(Request::builder().method("HEAD").uri(uri).body(Body::empty()).unwrap()).await.unwrap();
        assert_eq!(head.headers()[header::CONTENT_LENGTH], bytes.len().to_string());
        assert!(axum::body::to_bytes(head.into_body(), 1024).await.unwrap().is_empty());
        let cached = app.oneshot(Request::builder().uri(uri).header(header::IF_NONE_MATCH, etag).body(Body::empty()).unwrap()).await.unwrap();
        assert_eq!(cached.status(), StatusCode::NOT_MODIFIED);
        assert!(axum::body::to_bytes(cached.into_body(), 1024).await.unwrap().is_empty());
        tokio::fs::remove_dir_all(dir).await.unwrap();
    }

    #[tokio::test]
    async fn preview_http_preserves_original_and_recovers_after_missing_file() {
        use tower::ServiceExt;
        let dir = std::env::temp_dir().join(format!("preview-http-{}", uuid::Uuid::new_v4()));
        tokio::fs::create_dir(&dir).await.unwrap();
        let app = axum::Router::new()
            .nest_service("/suggestion-media", tower_http::services::ServeDir::new(&dir))
            .layer(axum::middleware::from_fn_with_state(PreviewRoots { illustrations: dir.clone(), suggestions: dir.clone() }, middleware));
        let request = || Request::builder().uri("/suggestion-media/test.png?preview=1").body(Body::empty()).unwrap();
        let missing = app.clone().oneshot(request()).await.unwrap();
        assert_eq!(missing.status(), StatusCode::NOT_FOUND);
        assert_eq!(missing.headers()[header::CACHE_CONTROL], "no-store");
        image::RgbImage::new(40, 20).save(dir.join("test.png")).unwrap();
        let preview = app.clone().oneshot(request()).await.unwrap();
        assert_eq!(preview.status(), StatusCode::OK);
        assert_eq!(preview.headers()[header::CONTENT_TYPE], "image/webp");
        let original = app.oneshot(Request::builder().uri("/suggestion-media/test.png").body(Body::empty()).unwrap()).await.unwrap();
        assert_eq!(original.headers()[header::CONTENT_TYPE], "image/png");
        tokio::fs::remove_dir_all(dir).await.unwrap();
    }

    #[tokio::test]
    #[ignore = "Set PHI_BENCH_IMAGE to a local artwork to measure cold/disk/memory preview performance"]
    async fn benchmark_local_media() {
        let source = PathBuf::from(std::env::var("PHI_BENCH_IMAGE").expect("PHI_BENCH_IMAGE"));
        let dir = std::env::temp_dir().join(format!("preview-bench-{}", uuid::Uuid::new_v4()));
        tokio::fs::create_dir(&dir).await.unwrap();
        let path = dir.join("benchmark.png");
        tokio::fs::copy(source, &path).await.unwrap();
        let original = tokio::fs::metadata(&path).await.unwrap().len();
        let start = std::time::Instant::now();
        let first = preview(path.clone(), Profile::Artwork).await.unwrap();
        let cold_ms = start.elapsed().as_secs_f64() * 1000.;
        cache().invalidate_all();
        let start = std::time::Instant::now();
        let disk = preview(path.clone(), Profile::Artwork).await.unwrap();
        let disk_ms = start.elapsed().as_secs_f64() * 1000.;
        let start = std::time::Instant::now();
        let warm = preview(path, Profile::Artwork).await.unwrap();
        let warm_ms = start.elapsed().as_secs_f64() * 1000.;
        assert_eq!(first, disk); assert_eq!(disk, warm);
        println!("original_bytes={original} preview_bytes={} cold_ms={cold_ms:.2} disk_ms={disk_ms:.2} memory_ms={warm_ms:.2}", first.bytes.len());
        tokio::fs::remove_dir_all(dir).await.unwrap();
    }
}
