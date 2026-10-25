use std::fmt::Write;

use crate::error::AppError;

use super::super::svg_error::svg_fmt_error;
use super::{PHI_PLUGIN_FONT_NAME, RenderRecord};

// Catrong/phi-plugin, main 6449202 (2026-10-16): model/game/b30Analysis.js
// and resources/html/b19/b19.{art,css}. Use the full-width, histogram-only panel.
pub(super) const HEIGHT: f64 = 310.0;

struct Slot {
    label: String,
    rks: f64,
    is_phi: bool,
}

pub(super) struct RksHistogram {
    slots: Vec<Slot>,
    average: f64,
    domain_min: f64,
    domain_max: f64,
    step: f64,
}

impl RksHistogram {
    pub(super) fn new(phi: &[RenderRecord], best: &[RenderRecord]) -> Option<Self> {
        let mut slots = Vec::with_capacity(30);
        for (records, limit, prefix, is_phi) in [(phi, 3, 'P', true), (best, 27, 'B', false)] {
            for (index, record) in records.iter().take(limit).enumerate() {
                if record.song_id.is_empty()
                    || !matches!(
                        record.difficulty.to_ascii_uppercase().as_str(),
                        "EZ" | "HD" | "IN" | "AT" | "LEGACY"
                    )
                    || !record.rks.is_finite()
                {
                    continue;
                }
                // A chart appearing in both P3 and B27 contributes twice to RKS.
                slots.push(Slot {
                    label: format!("{prefix}{}", index + 1),
                    rks: record.rks,
                    is_phi,
                });
            }
        }
        if slots.is_empty() {
            return None;
        }

        let minimum = slots
            .iter()
            .map(|slot| slot.rks)
            .fold(f64::INFINITY, f64::min);
        let maximum = slots
            .iter()
            .map(|slot| slot.rks)
            .fold(f64::NEG_INFINITY, f64::max);
        let raw_step = (maximum - minimum).max(0.2) / 4.0;
        let step = [0.02, 0.05, 0.1, 0.2, 0.25, 0.5, 1.0]
            .into_iter()
            .find(|&candidate| candidate >= raw_step)
            .unwrap_or_else(|| raw_step.ceil());
        let domain_min = ((minimum - step * 0.1) / step).floor() * step;
        let domain_max = (((maximum + step * 0.1) / step).ceil() * step).max(domain_min + step);
        let average = slots.iter().map(|slot| slot.rks).sum::<f64>() / slots.len() as f64;
        Some(Self {
            slots,
            average,
            domain_min,
            domain_max,
            step,
        })
    }

    fn position(&self, rks: f64) -> f64 {
        ((rks - self.domain_min) / (self.domain_max - self.domain_min)).clamp(0.0, 1.0)
    }

    pub(super) fn write_svg(&self, svg: &mut String, y: f64) -> Result<(), AppError> {
        // Mirror the 1200 px HTML canvas: 95% panel width, 100 px padding,
        // 310 px height and a 0.3 slope on both clipped edges.
        writeln!(svg, r##"<g id="phi-rks-histogram" transform="translate(0 {y:.1})">
<defs>
  <clipPath id="phi-histogram-clip"><path d="M123 0 H1170 L1077 310 H30 Z"/></clipPath>
  <linearGradient id="phi-histogram-best"><stop stop-color="#007ea8"/><stop offset="1" stop-color="#00b7f0"/></linearGradient>
  <linearGradient id="phi-histogram-phi"><stop stop-color="#c6c6c6"/><stop offset="1" stop-color="#fff"/></linearGradient>
  <filter id="phi-histogram-bar-glow" x="-60%" y="-20%" width="220%" height="140%"><feDropShadow dx="0" dy="0" stdDeviation="5" flood-color="#00b7f0" flood-opacity=".2"/></filter>
  <filter id="phi-histogram-average-glow" x="-2%" y="-1000%" width="104%" height="2100%"><feDropShadow dx="0" dy="0" stdDeviation="4" flood-color="#00b7f0"/></filter>
  <style>
    #phi-rks-histogram text{{font-family:'{PHI_PLUGIN_FONT_NAME}',sans-serif}}
    #phi-rks-histogram .histogram-number{{font-family:'Aldrich','{PHI_PLUGIN_FONT_NAME}',sans-serif}}
  </style>
</defs>
<g clip-path="url(#phi-histogram-clip)">
  <rect x="30" width="1140" height="310" fill="#000" fill-opacity=".62"/>
  <path d="M30 1 H1170" stroke="#fff" stroke-opacity=".9" stroke-width="2"/>
  <path d="M30 309 H1170" stroke="#00b7f0" stroke-opacity=".75" stroke-width="2"/>
  <text x="130" y="37" font-size="13" fill="#00b7f0" style="fill:#00b7f0">RKS DISTRIBUTION</text>
  <g class="histogram-title" aria-label="等效 RKS 直方图">
    <text x="130" y="68" font-size="27" style="font-family:{PHI_PLUGIN_FONT_NAME}">等效</text>
    <text x="192.095" y="68" font-size="27" style="font-family:Aldrich">RKS</text>
    <text x="257.472" y="68" font-size="27" style="font-family:{PHI_PLUGIN_FONT_NAME}">直方图</text>
  </g>
  <text x="1120" y="42" text-anchor="end" font-size="12" opacity=".62">平均 RKS</text>
  <text x="1120" y="71" text-anchor="end" font-size="24" class="histogram-number">{average:.4}</text>
  <path d="M130 87 H1120" stroke="#fff" stroke-opacity=".25"/>
  <text x="99" y="163" text-anchor="middle" font-size="10" opacity=".52"><tspan x="99">等</tspan><tspan x="99" dy="10">效</tspan><tspan x="99" dy="10">单</tspan><tspan x="99" dy="10">曲</tspan></text>
  <text transform="translate(96 197) rotate(90)" font-size="10" opacity=".52">RKS</text>
  <path d="M108.5 99 V259.5 H1080" fill="none" stroke="#fff" stroke-opacity=".32"/>
"##, average = self.average).map_err(svg_fmt_error)?;

        const PLOT_TOP: f64 = 99.0;
        const PLOT_HEIGHT: f64 = 136.0;
        const BASELINE: f64 = PLOT_TOP + PLOT_HEIGHT;
        const BARS_LEFT: f64 = 138.0;
        const BARS_RIGHT: f64 = 1077.0;
        let slot_width =
            (BARS_RIGHT - BARS_LEFT - (self.slots.len() - 1) as f64) / self.slots.len() as f64;
        let bar_width = slot_width * 0.72;
        for (index, slot) in self.slots.iter().enumerate() {
            let height = (self.position(slot.rks) * PLOT_HEIGHT).max(2.0);
            let center_x = BARS_LEFT + index as f64 * (slot_width + 1.0) + slot_width / 2.0;
            let gradient = if slot.is_phi { "phi" } else { "best" };
            writeln!(svg, r##"<g class="histogram-slot" data-slot="{}" data-rks="{:.8}">
  <rect x="{:.3}" y="{:.3}" width="{bar_width:.3}" height="{height:.3}" fill="url(#phi-histogram-{gradient})" filter="url(#phi-histogram-bar-glow)"/>
  <text transform="translate({:.3} 240) rotate(45)" font-size="7" opacity=".56">{}</text>
</g>"##, slot.label, slot.rks, center_x - bar_width / 2.0, BASELINE - height, center_x, slot.label)
                .map_err(svg_fmt_error)?;
        }

        // Grid and labels sit above the bars, as in the HTML's histogram-scale.
        let tick_count = ((self.domain_max - self.domain_min) / self.step).round() as usize;
        for index in 0..=tick_count {
            let value = self.domain_min + index as f64 * self.step;
            let tick_y = BASELINE - index as f64 / tick_count as f64 * PLOT_HEIGHT;
            writeln!(svg, r##"<path d="M109 {tick_y:.3} H1080" stroke="#fff" stroke-opacity=".12" stroke-dasharray="3 3"/>
<rect x="110" y="{:.3}" width="27" height="12" fill="#000" fill-opacity=".7"/>
<text x="111" y="{:.3}" font-size="8" opacity=".5">{value:.2}</text>"##, tick_y - 7.0, tick_y + 2.0)
                .map_err(svg_fmt_error)?;
        }

        let average_y = BASELINE - self.position(self.average) * PLOT_HEIGHT;
        // A horizontal path has a zero-height bounding box, so an object-bounding-box
        // filter can hide it entirely. A 2 px rect keeps the glow and line renderable.
        writeln!(svg, r##"<rect class="histogram-average" x="138" y="{:.3}" width="942" height="2" fill="#fff" filter="url(#phi-histogram-average-glow)"/>
<text x="1078" y="{:.3}" text-anchor="end" font-size="8" stroke="#000" stroke-opacity=".6" stroke-width="2" paint-order="stroke">AVG {:.4}</text>
<g font-size="11" opacity=".62">
  <rect x="864" y="283" width="8" height="8" fill="#fff"/><text x="877" y="291">P1-P3</text>
  <rect x="928" y="283" width="8" height="8" fill="#00b7f0"/><text x="941" y="291">B1-B27</text>
  <text x="1070" y="291" text-anchor="end">{} 个有效槽位</text>
</g></g></g>"##, average_y - 1.0, average_y - 4.0, self.average, self.slots.len()).map_err(svg_fmt_error)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn record(id: &str, rks: f64) -> RenderRecord {
        RenderRecord {
            song_id: id.into(),
            song_name: id.into(),
            difficulty: "IN".into(),
            score: Some(1_000_000.0),
            acc: 100.0,
            rks,
            difficulty_value: rks,
            is_fc: true,
        }
    }

    #[test]
    fn uses_p3_and_b27_slots_without_deduplicating_or_counting_overflow() {
        let phi = vec![record("shared", 16.0); 4];
        let mut best = vec![record("shared", 15.0); 27];
        best.extend(vec![record("overflow", 18.0); 6]);
        let histogram = RksHistogram::new(&phi, &best).unwrap();
        assert_eq!(histogram.slots.len(), 30);
        assert_eq!(histogram.average, 15.1);
        assert_eq!(histogram.slots[2].label, "P3");
        assert_eq!(histogram.slots[3].label, "B1");
        assert_eq!(histogram.slots[29].label, "B27");
    }

    #[test]
    fn axis_and_average_match_upstream_fixtures() {
        let histogram = RksHistogram::new(
            &[record("a", 15.0)],
            &[record("b", 15.5), record("c", 16.0)],
        )
        .unwrap();
        assert_eq!(histogram.average, 15.5);
        assert_eq!(histogram.step, 0.25);
        assert_eq!(histogram.domain_min, 14.75);
        assert_eq!(histogram.domain_max, 16.25);
        assert_eq!(histogram.position(histogram.average), 0.5);

        let single = RksHistogram::new(&[], &[record("only", 15.5)]).unwrap();
        assert!((single.position(single.average) - 0.5).abs() < 1e-9);
        let zero = RksHistogram::new(&[], &[record("zero", 0.0)]).unwrap();
        assert!((zero.position(0.0) - 0.5).abs() < 1e-9);
    }

    #[test]
    fn invalid_and_missing_slots_do_not_break_axis_or_renumber_valid_slots() {
        assert!(RksHistogram::new(&[], &[]).is_none());
        assert!(RksHistogram::new(&[record("nan", f64::NAN)], &[]).is_none());
        let mut invalid_difficulty = record("special", 18.0);
        invalid_difficulty.difficulty = "SP".into();
        let histogram = RksHistogram::new(
            &[],
            &[
                record("nan", f64::NAN),
                record("", 16.0),
                record("valid", 15.5),
                invalid_difficulty,
                record("inf", f64::INFINITY),
            ],
        )
        .unwrap();
        assert_eq!(histogram.slots.len(), 1);
        assert_eq!(histogram.slots[0].label, "B3");
        assert_eq!(histogram.average, 15.5);
    }

    #[test]
    fn average_line_is_visible_after_svg_rasterization() {
        let histogram = RksHistogram::new(
            &[record("a", 15.0)],
            &[record("b", 15.5), record("c", 16.0)],
        )
        .unwrap();
        let mut svg =
            String::from(r#"<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="310">"#);
        histogram.write_svg(&mut svg, 0.0).unwrap();
        svg.push_str("</svg>");
        let tree = resvg::usvg::Tree::from_str(&svg, &Default::default()).unwrap();
        let mut pixmap = resvg::tiny_skia::Pixmap::new(1200, 310).unwrap();
        resvg::render(
            &tree,
            resvg::tiny_skia::Transform::identity(),
            &mut pixmap.as_mut(),
        );
        // The upstream fixture's average is centered on the 136 px plot at y=167.
        let pixel = pixmap.pixel(1000, 167).unwrap();
        assert!(pixel.red() > 240 && pixel.green() > 240 && pixel.blue() > 240);
    }
}
