use std::num::NonZeroUsize;
use std::path::{Path, PathBuf};
use std::sync::{Arc, OnceLock};

use base64::{Engine as _, engine::general_purpose::STANDARD as base64_engine};
use image::ColorType;
use image::codecs::jpeg::JpegEncoder;
use image::imageops::FilterType;
use lru::LruCache;

use crate::config::AppConfig;

const SCALED_IMAGE_CACHE_SIZE: usize = 256;

// 预缩放图片 Data URI 缓存（键包含源路径与目标尺寸）
#[derive(Hash, Eq, PartialEq, Clone, Debug)]
struct ScaledImageKey {
    path: PathBuf,
    w: u32,
    h: u32,
    blur_milli: u32,
}

static SCALED_IMAGE_CACHE: OnceLock<std::sync::Mutex<LruCache<ScaledImageKey, Arc<str>>>> =
    OnceLock::new();

fn get_scaled_image_cache() -> &'static std::sync::Mutex<LruCache<ScaledImageKey, Arc<str>>> {
    SCALED_IMAGE_CACHE.get_or_init(|| {
        std::sync::Mutex::new(LruCache::new(
            NonZeroUsize::new(SCALED_IMAGE_CACHE_SIZE).unwrap(),
        ))
    })
}

/// 将磁盘图片按给定尺寸进行等比裁剪填充（相当于 xMidYMid slice），再编码为 JPEG 并返回 Data URI。
/// 结果加入 LRU 缓存以避免重复解码与缩放。
pub(super) fn get_scaled_image_data_uri(
    path: &Path,
    target_w: u32,
    target_h: u32,
) -> Option<String> {
    get_prepared_image_data_uri(path, target_w, target_h, 0.0)
}

/// Prepare a full-height background at a bounded resolution before SVG filters
/// can expand a landscape source across a portrait Best N canvas.
pub(super) fn get_blurred_background_data_uri(path: &Path, width: u32, height: u32, sigma: f32) -> Option<String> {
    let scale = (512.0 / width as f32).min(2048.0 / height as f32).min(1.0);
    let w = (width as f32 * scale).round().max(1.0) as u32;
    let h = (height as f32 * scale).round().max(1.0) as u32;
    get_prepared_image_data_uri(path, w, h, sigma * scale)
}

fn get_prepared_image_data_uri(path: &Path, target_w: u32, target_h: u32, blur: f32) -> Option<String> {
    if target_w == 0 || target_h == 0 {
        return None;
    }
    let key = ScaledImageKey {
        path: path.to_path_buf(),
        w: target_w,
        h: target_h,
        blur_milli: (blur * 1000.0).round() as u32,
    };
    // 缓存值用 Arc，命中时锁内只复制引用计数，避免持锁复制大 Data URI 字符串。
    let cached_uri = if let Ok(mut cache) = get_scaled_image_cache().lock() {
        cache.get(&key).cloned()
    } else {
        None
    };
    if let Some(uri) = cached_uri {
        return Some(uri.as_ref().to_owned());
    }

    let speed = AppConfig::global().image.optimize_speed;
    let filter = if speed {
        FilterType::Triangle
    } else {
        FilterType::Lanczos3
    };
    let img = image::open(path).ok()?;
    let resized = img.resize_to_fill(target_w, target_h, filter);
    let rgb = if blur > 0.0 { resized.blur(blur).to_rgb8() } else { resized.to_rgb8() };

    let mut out = Vec::new();
    let mut enc = JpegEncoder::new_with_quality(&mut out, 85);
    if enc
        .encode(&rgb, target_w, target_h, ColorType::Rgb8.into())
        .is_err()
    {
        return None;
    }
    let b64 = base64_engine.encode(out);
    let uri = format!("data:image/jpeg;base64,{b64}");
    let cached_uri = if let Ok(mut cache) = get_scaled_image_cache().lock() {
        let cached_uri = cache.get(&key).cloned();
        if cached_uri.is_none() {
            cache.put(key, Arc::<str>::from(uri.as_str()));
        }
        cached_uri
    } else {
        None
    };
    if let Some(cached_uri) = cached_uri {
        return Some(cached_uri.as_ref().to_owned());
    }

    Some(uri)
}
