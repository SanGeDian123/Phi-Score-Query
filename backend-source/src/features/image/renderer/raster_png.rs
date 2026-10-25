use crate::config::AppConfig;
use crate::error::AppError;

use std::io::Write;
use super::raster_surface::{RasterScene, STRIP_HEIGHT};

/// 按目标宽度下采样后编码为 PNG（未提供则使用 SVG 原始宽度）
pub(super) fn render_svg_to_png_scaled(
    svg_data: &str,
    is_user_generated: bool,
    target_width: Option<u32>,
) -> Result<Vec<u8>, AppError> {
    let scene = RasterScene::parse(svg_data, target_width)?;
    // Encoded PNG is compressed. Never reserve a second full RGBA canvas.
    let mut out = Vec::new();
    {
        let mut encoder = png::Encoder::new(&mut out, scene.width, scene.height);
        encoder.set_color(png::ColorType::Rgba);
        encoder.set_depth(png::BitDepth::Eight);
        if AppConfig::global().image.optimize_speed {
            encoder.set_compression(png::Compression::Fast);
            encoder.set_filter(png::FilterType::NoFilter);
        } else {
            encoder.set_compression(png::Compression::Default);
            encoder.set_filter(png::FilterType::Paeth);
        }
        let mut writer = encoder
            .write_header()
            .map_err(|e| AppError::ImageRendererError(format!("PNG write_header error: {e}")))?;
        if scene.height <= 2048 {
            let mut pixmap = scene.render_full()?;
            if is_user_generated { pixmap.data_mut()[..4].copy_from_slice(&[0x01, 0x02, 0x03, 0xFF]); }
            writer.write_image_data(pixmap.data()).map_err(|e| AppError::ImageRendererError(format!("PNG write_image_data error: {e}")))?;
        } else {
            let stride = scene.width as usize * 4;
            let mut stream = writer.stream_writer().map_err(|e| AppError::ImageRendererError(format!("PNG stream error: {e}")))?;
            for top in (0..scene.height).step_by(STRIP_HEIGHT as usize) {
                let rows = STRIP_HEIGHT.min(scene.height - top);
                let (mut strip, offset) = scene.render_strip(top, rows)?;
                if is_user_generated && top == 0 { strip.data_mut()[offset..offset + 4].copy_from_slice(&[0x01, 0x02, 0x03, 0xFF]); }
                stream.write_all(&strip.data()[offset..offset + rows as usize * stride])
                    .map_err(|e| AppError::ImageRendererError(format!("PNG strip error: {e}")))?;
            }
            stream.finish().map_err(|e| AppError::ImageRendererError(format!("PNG stream finish error: {e}")))?;
        }
        writer
            .finish()
            .map_err(|e| AppError::ImageRendererError(format!("PNG finish error: {e}")))?;
    }
    Ok(out)
}
