use crate::error::AppError;
use resvg::usvg;
use resvg::{
    render,
    tiny_skia::{Pixmap, Transform},
};

use super::math::scaled_dimensions;

pub(super) const STRIP_HEIGHT: u32 = 512;
const MAX_WIDTH: u32 = 2048;
const MAX_HEIGHT: u32 = 16383;
const MAX_PIXELS: u64 = 32 * 1024 * 1024;

/// Keep the SVG tree (including decoded covers) once; bound filter buffers to a
/// strip instead of letting a full-height blur allocate several giant layers.
pub(super) struct RasterScene {
    tree: usvg::Tree,
    pub(super) width: u32,
    pub(super) height: u32,
    scale: f32,
}

impl RasterScene {
    pub(super) fn parse(svg_data: &str, target_width: Option<u32>) -> Result<Self, AppError> {
        if target_width.is_some_and(|width| width == 0 || width > MAX_WIDTH) {
            return Err(AppError::Validation(format!("图片宽度须在 1–{MAX_WIDTH} 之间")));
        }
        let opts = super::raster_options::build_usvg_options()?;
        let tree = usvg::Tree::from_data(svg_data.as_bytes(), &opts)
            .map_err(|e| AppError::ImageRendererError(format!("Failed to parse SVG: {e}")))?;
        let src_size = tree.size().to_int_size();
        let (width, height, scale) = scaled_dimensions(src_size.width(), src_size.height(), target_width);
        if width > MAX_WIDTH || height > MAX_HEIGHT || u64::from(width) * u64::from(height) > MAX_PIXELS {
            return Err(AppError::Validation("图片尺寸过大，请降低图片宽度".into()));
        }
        Ok(Self { tree, width, height, scale })
    }

    pub(super) fn render_strip(&self, top: u32, rows: u32) -> Result<(Pixmap, usize), AppError> {
        // At least six times the largest built-in blur radius, scaled with the
        // output. Overlap keeps blur/shadow edges continuous across strips.
        let overlap = (128.0 * self.scale.max(1.0)).ceil() as u32;
        let start = top.saturating_sub(overlap);
        let end = top.saturating_add(rows).saturating_add(overlap).min(self.height);
        let mut pixmap = Pixmap::new(self.width, end - start)
            .ok_or_else(|| AppError::ImageRendererError("Failed to create strip".into()))?;
        render(&self.tree, Transform::from_row(self.scale, 0.0, 0.0, self.scale, 0.0, -(start as f32)), &mut pixmap.as_mut());
        Ok((pixmap, ((top - start) as usize) * (self.width as usize) * 4))
    }

    pub(super) fn render_full(&self) -> Result<Pixmap, AppError> {
        let mut pixmap = Pixmap::new(self.width, self.height)
            .ok_or_else(|| AppError::ImageRendererError("Failed to create pixmap".into()))?;
        if self.height <= 2048 {
            render(&self.tree, Transform::from_scale(self.scale, self.scale), &mut pixmap.as_mut());
        } else {
            let stride = self.width as usize * 4;
            for top in (0..self.height).step_by(STRIP_HEIGHT as usize) {
                let rows = STRIP_HEIGHT.min(self.height - top);
                let (strip, offset) = self.render_strip(top, rows)?;
                let count = rows as usize * stride;
                let dst = top as usize * stride;
                pixmap.data_mut()[dst..dst + count].copy_from_slice(&strip.data()[offset..offset + count]);
            }
        }
        Ok(pixmap)
    }
}

pub(super) struct RasterSurface {
    pub(super) pixmap: Pixmap,
    pub(super) width: u32,
    pub(super) height: u32,
}

/// 将 SVG 解析并按目标宽度栅格化，供不同图片编码器复用。
pub(super) fn render_scaled_pixmap(
    svg_data: &str,
    is_user_generated: bool,
    target_width: Option<u32>,
) -> Result<RasterSurface, AppError> {
    let scene = RasterScene::parse(svg_data, target_width)?;
    let (width, height) = (scene.width, scene.height);
    let mut pixmap = scene.render_full()?;

    // 用户数据添加隐式水印：直接修改未编码像素，避免解/编码开销。
    if is_user_generated && let Some(px) = pixmap.data_mut().get_mut(0..4) {
        px.copy_from_slice(&[0x01, 0x02, 0x03, 0xFF]);
    }

    Ok(RasterSurface {
        pixmap,
        width,
        height,
    })
}
