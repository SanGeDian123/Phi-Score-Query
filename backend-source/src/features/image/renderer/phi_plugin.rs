use std::collections::HashMap;
use std::fmt::Write;
use std::fs;
use std::path::PathBuf;
use std::sync::OnceLock;

use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use chrono::Duration;
use sha2::{Digest as _, Sha256};

use crate::config::AppConfig;
use crate::error::AppError;
use crate::features::image::Theme;
use crate::rks_contract::engine;

use super::bn_background::select_random_background;
use super::bn_card_acc::{pre_calculated_push_acc_for_score, resolve_push_acc_hint};
use super::bn_card_cover::resolve_card_cover_href;
use super::resources::get_scaled_image_data_uri;
use super::score::to_engine_record;
use super::svg_error::svg_fmt_error;
use super::template_shared::truncate_with_ellipsis;
use super::text::escape_xml;
use super::{PlayerStats, RenderRecord, MAIN_FONT_NAME};

// The upstream Phi-Plugin HTML is authored on a 1200 px canvas. These values
// intentionally mirror b19.css instead of sharing the dimensions of the other
// score-image templates.
const WIDTH: u32 = 1200;
const CARD_GAP_X: f64 = 35.0;
const CARD_STEP_X: f64 = 395.0;
const CARD_STEP_Y: f64 = 125.0;

#[derive(Clone, Copy)]
struct RankPalette {
    primary: &'static str,
}

pub(super) fn generate_phi_plugin_svg<S>(
    scores: &[RenderRecord],
    stats: &PlayerStats,
    push_acc_map: Option<&HashMap<String, engine::PushAccHint, S>>,
    theme: Theme,
    embed_images: bool,
    public_illustration_base_url: Option<&str>,
) -> Result<String, AppError>
where
    S: std::hash::BuildHasher,
{
    let is_p30 = stats.image_title.eq_ignore_ascii_case("P30");
    let has_ap_section = if is_p30 {
        !scores.is_empty()
    } else {
        !stats.ap_top_3_scores.is_empty()
    };
    let main_y = if has_ap_section { 300.0 } else { 175.0 };
    let core_count = scores.len().min(27);
    let overflow_count = scores.len().saturating_sub(core_count);
    let core_rows = core_count.div_ceil(3);
    let overflow_rows = overflow_count.div_ceil(3);
    let divider_y = main_y + core_rows as f64 * CARD_STEP_Y + 5.0;
    let overflow_y = divider_y + 65.0;
    let content_bottom = if overflow_count > 0 {
        overflow_y + overflow_rows as f64 * CARD_STEP_Y
    } else {
        main_y + core_rows as f64 * CARD_STEP_Y
    };
    let total_height = (content_bottom + 45.0).ceil().max(520.0) as u32;
    let palette = rank_palette(
        stats
            .challenge_rank
            .as_ref()
            .map(|(color, _)| color.as_str()),
    );
    let engine_records: Vec<engine::RksRecord> =
        scores.iter().filter_map(to_engine_record).collect();
    let background = select_random_background(
        &theme,
        embed_images,
        public_illustration_base_url,
        WIDTH,
        total_height,
        "#ffffff".to_string(),
    );

    let mut svg = String::with_capacity(260_000);
    writeln!(
        svg,
        r##"<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{total_height}" viewBox="0 0 {WIDTH} {total_height}">"##,
    )
    .map_err(svg_fmt_error)?;
    write_defs(&mut svg)?;
    write_background(&mut svg, background.image_href.as_deref(), total_height)?;
    write_header(&mut svg, stats, palette)?;

    if has_ap_section {
        let perfect_top = if is_p30 {
            scores
        } else {
            stats.ap_top_3_scores.as_slice()
        };
        for (index, score) in perfect_top.iter().take(3).enumerate() {
            write_card(
                &mut svg,
                score,
                index,
                CARD_GAP_X + index as f64 * CARD_STEP_X,
                175.0,
                true,
                push_acc_map,
                &engine_records,
                embed_images,
                public_illustration_base_url,
            )?;
        }
    }

    for (index, score) in scores.iter().take(core_count).enumerate() {
        let col = index % 3;
        let row = index / 3;
        write_card(
            &mut svg,
            score,
            index,
            CARD_GAP_X + col as f64 * CARD_STEP_X,
            main_y + row as f64 * CARD_STEP_Y,
            false,
            push_acc_map,
            &engine_records,
            embed_images,
            public_illustration_base_url,
        )?;
    }

    if overflow_count > 0 {
        write_overflow_divider(&mut svg, divider_y)?;
        for (offset, score) in scores.iter().skip(core_count).enumerate() {
            let col = offset % 3;
            let row = offset / 3;
            write_card(
                &mut svg,
                score,
                core_count + offset,
                CARD_GAP_X + col as f64 * CARD_STEP_X,
                overflow_y + row as f64 * CARD_STEP_Y,
                false,
                push_acc_map,
                &engine_records,
                embed_images,
                public_illustration_base_url,
            )?;
        }
    }

    let footer = stats
        .custom_footer_text
        .as_deref()
        .unwrap_or("Phi Score Query");
    writeln!(
        svg,
        r##"<text x="{x}" y="{y}" class="footer">{}</text></svg>"##,
        escape_xml(footer),
        x = WIDTH - 26,
        y = total_height - 15,
    )
    .map_err(svg_fmt_error)?;
    Ok(svg)
}

fn write_defs(svg: &mut String) -> Result<(), AppError> {
    writeln!(
        svg,
        r##"<defs>
<linearGradient id="phi-bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#111822"/><stop offset=".52" stop-color="#202a34"/><stop offset="1" stop-color="#0a1118"/></linearGradient>
<linearGradient id="image-veil" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#030609" stop-opacity=".38"/><stop offset=".5" stop-color="#071017" stop-opacity=".16"/><stop offset="1" stop-color="#020407" stop-opacity=".34"/></linearGradient>
<linearGradient id="cover-placeholder" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#39464d"/><stop offset=".5" stop-color="#12191d"/><stop offset="1" stop-color="#050708"/></linearGradient>
<filter id="background-blur" x="-15%" y="-10%" width="130%" height="120%"><feGaussianBlur stdDeviation="24"/></filter>
<filter id="card-shadow" x="-30%" y="-35%" width="165%" height="180%"><feDropShadow dx="2" dy="4" stdDeviation="5" flood-color="#000" flood-opacity=".5"/></filter>
<filter id="card-edge-halo" x="-40%" y="-85%" width="190%" height="270%" color-interpolation-filters="sRGB">
  <feMorphology in="SourceAlpha" operator="dilate" radius=".8" result="halo-edge"/>
  <feGaussianBlur in="halo-edge" stdDeviation="5.5" result="halo-near-blur"/>
  <feGaussianBlur in="halo-edge" stdDeviation="14" result="halo-wide-blur"/>
  <feFlood flood-color="#fff" flood-opacity=".55" result="halo-near-color"/>
  <feFlood flood-color="#fff" flood-opacity=".34" result="halo-wide-color"/>
  <feComposite in="halo-near-color" in2="halo-near-blur" operator="in" result="halo-near"/>
  <feComposite in="halo-wide-color" in2="halo-wide-blur" operator="in" result="halo-wide"/>
  <feMerge result="halo-merged"><feMergeNode in="halo-wide"/><feMergeNode in="halo-near"/></feMerge>
  <feComposite in="halo-merged" in2="SourceAlpha" operator="out"/>
</filter>
<filter id="card-edge-halo-gold" x="-40%" y="-85%" width="190%" height="270%" color-interpolation-filters="sRGB">
  <feMorphology in="SourceAlpha" operator="dilate" radius=".8" result="halo-edge-gold"/>
  <feGaussianBlur in="halo-edge-gold" stdDeviation="5.5" result="halo-near-blur-gold"/>
  <feGaussianBlur in="halo-edge-gold" stdDeviation="14" result="halo-wide-blur-gold"/>
  <feFlood flood-color="#ffd700" flood-opacity=".55" result="halo-near-color-gold"/>
  <feFlood flood-color="#ffd700" flood-opacity=".34" result="halo-wide-color-gold"/>
  <feComposite in="halo-near-color-gold" in2="halo-near-blur-gold" operator="in" result="halo-near-gold"/>
  <feComposite in="halo-wide-color-gold" in2="halo-wide-blur-gold" operator="in" result="halo-wide-gold"/>
  <feMerge result="halo-merged-gold"><feMergeNode in="halo-wide-gold"/><feMergeNode in="halo-near-gold"/></feMerge>
  <feComposite in="halo-merged-gold" in2="SourceAlpha" operator="out"/>
</filter>
<filter id="icon-glow" x="-70%" y="-70%" width="240%" height="240%"><feDropShadow dx="0" dy="0" stdDeviation="4" flood-color="#fff" flood-opacity=".78"/></filter>
<filter id="ap-icon-glow" x="-70%" y="-70%" width="240%" height="240%"><feDropShadow dx="0" dy="0" stdDeviation="4" flood-color="#fff700" flood-opacity=".72"/></filter>
<style>
text{{font-family:'{}','Microsoft YaHei',sans-serif;fill:#fff}} .player-name{{font-size:30px}} .meta{{font-size:14px}} .table{{font-size:15px;text-anchor:middle}} .challenge-level{{font-size:27px;text-anchor:middle}} .header-rks{{font-size:20px;fill:#111;text-anchor:middle}} .data-text{{font-size:13px}} .card-title{{font-size:15px;text-anchor:middle}} .score{{font-size:24px;text-anchor:middle}} .acc{{font-size:16px;text-anchor:middle}} .suggest{{font-family:'{}','Microsoft YaHei',sans-serif;font-size:12.5px;font-weight:400;text-anchor:middle}} .rank-tag{{font-size:12px;fill:#111;text-anchor:middle}} .diff-org{{font-size:12px;text-anchor:middle}} .diff-rks{{font-size:13px;text-anchor:middle}} .footer{{font-size:12px;fill:#c8ced1;text-anchor:end}} .overflow{{font-family:'Aldrich','{}',sans-serif;font-size:25px;font-style:italic;letter-spacing:2px;text-anchor:middle}}
</style></defs>"##,
        MAIN_FONT_NAME,
        MAIN_FONT_NAME,
        MAIN_FONT_NAME,
    )
    .map_err(svg_fmt_error)
}

fn write_background(
    svg: &mut String,
    image_href: Option<&str>,
    total_height: u32,
) -> Result<(), AppError> {
    writeln!(
        svg,
        r##"<rect width="100%" height="100%" fill="url(#phi-bg)"/>"##,
    )
    .map_err(svg_fmt_error)?;
    if let Some(href) = image_href {
        writeln!(
            svg,
            r##"<image href="{}" x="-60" y="-60" width="1320" height="{}" preserveAspectRatio="xMidYMid slice" filter="url(#background-blur)" opacity=".72"/>"##,
            escape_xml(href),
            total_height + 120,
        )
        .map_err(svg_fmt_error)?;
    }
    writeln!(
        svg,
        r##"<rect width="100%" height="100%" fill="#030609" opacity=".34"/><rect width="100%" height="100%" fill="url(#image-veil)"/>"##,
    )
    .map_err(svg_fmt_error)
}

fn write_header(
    svg: &mut String,
    stats: &PlayerStats,
    palette: RankPalette,
) -> Result<(), AppError> {
    let player_name =
        truncate_with_ellipsis(stats.player_name.as_deref().unwrap_or("Phigros Player"), 24);
    let update_text = (stats.update_time + Duration::hours(8))
        .format("%Y %b.%d %H:%M:%S")
        .to_string();
    let level = stats
        .challenge_rank
        .as_ref()
        .map_or("--", |(_, level)| level.as_str());
    let data = stats
        .data_string
        .as_deref()
        .unwrap_or("Data --")
        .trim_start_matches("Data:")
        .trim()
        .replace("GiB", "GB")
        .replace("MiB", "MB")
        .replace("KiB", "KB");
    let data = truncate_with_ellipsis(&data, 28);
    let data_width = (44.0 + data.chars().count() as f64 * 7.2).clamp(112.0, 270.0);
    let counts = stats.grade_counts;
    let rank_color = stats
        .challenge_rank
        .as_ref()
        .map(|(color, _)| color.as_str());

    // Header geometry follows b19.css: 7% player block, 22% record block,
    // 12% right margin, and the shared height*0.3 double-slant clipping rule.
    writeln!(
        svg,
        r##"<path d="M117.84 51.6 H684 L662.16 124.4 H96 Z" fill="#050708" fill-opacity=".68"/><path d="M789.6 32 H1056 L1022.4 144 H756 Z" fill="#050708" fill-opacity=".68"/><path d="M789.6 32 L756 144 L760 144 L793.6 32 Z" fill="#fff"/>"##,
    )
    .map_err(svg_fmt_error)?;

    if let Some(avatar_href) = player_avatar_data_uri(stats.player_avatar.as_deref()) {
        writeln!(
            svg,
            r##"<clipPath id="phi-avatar-cut"><path d="M108 48 H244 L220 128 H84 Z"/></clipPath><image href="{}" x="84" y="48" width="160" height="80" preserveAspectRatio="xMidYMid slice" clip-path="url(#phi-avatar-cut)"/>"##,
            escape_xml(&avatar_href),
        )
        .map_err(svg_fmt_error)?;
    }

    if let Some(plate_href) = challenge_plate_data_uri(rank_color) {
        writeln!(
            svg,
            r##"<image href="{}" x="246" y="61" width="64" height="31.5" preserveAspectRatio="none"/><text x="278" y="80" class="challenge-level">{}</text>"##,
            escape_xml(plate_href),
            escape_xml(level),
        )
        .map_err(svg_fmt_error)?;
    } else {
        writeln!(
            svg,
            r##"<path d="M255.45 61 H310 L300.55 92.5 H246 Z" fill="{}"/><text x="278" y="80" class="challenge-level">{}</text>"##,
            palette.primary,
            escape_xml(level),
        )
        .map_err(svg_fmt_error)?;
    }

    writeln!(
        svg,
        r##"<path d="M230.2 94 H331 L323.8 118 H223 Z" fill="#fff"/><text x="277" y="113" class="header-rks">{:.4}</text><text x="684" y="45" class="meta" text-anchor="end">{}</text><text x="505" y="98" class="player-name" text-anchor="middle">{}</text>"##,
        stats.real_rks.unwrap_or(0.0),
        escape_xml(&update_text),
        escape_xml(&player_name),
    )
    .map_err(svg_fmt_error)?;

    let data_right = 77.0 + data_width;
    writeln!(
        svg,
        r##"<path d="M83 133 H{data_right:.1} L{data_end:.1} 153 H77 Z" fill="#050708" fill-opacity=".7"/>"##,
        data_end = data_right - 6.0,
    )
    .map_err(svg_fmt_error)?;
    if let Some(icon) = phi_plugin_asset_data_uri("data.png") {
        writeln!(
            svg,
            r##"<image href="{}" x="86" y="136" width="25" height="15" preserveAspectRatio="xMidYMid meet"/>"##,
            escape_xml(icon),
        )
        .map_err(svg_fmt_error)?;
    }
    writeln!(
        svg,
        r##"<text x="115" y="148" class="data-text">{}</text>"##,
        escape_xml(&data),
    )
    .map_err(svg_fmt_error)?;

    write_record_row(svg, 53.0, &["\\", "AT", "IN", "HD", "EZ"], 802.5)?;
    write_record_row(
        svg,
        81.0,
        &[
            "C".to_string(),
            counts.at.c.to_string(),
            counts.in_.c.to_string(),
            counts.hd.c.to_string(),
            counts.ez.c.to_string(),
        ],
        799.0,
    )?;
    write_record_row(
        svg,
        109.0,
        &[
            "FC".to_string(),
            counts.at.fc.to_string(),
            counts.in_.fc.to_string(),
            counts.hd.fc.to_string(),
            counts.ez.fc.to_string(),
        ],
        795.5,
    )?;
    write_record_row(
        svg,
        137.0,
        &[
            "Phi".to_string(),
            counts.at.p.to_string(),
            counts.in_.p.to_string(),
            counts.hd.p.to_string(),
            counts.ez.p.to_string(),
        ],
        792.0,
    )
}

fn write_record_row<T: AsRef<str>>(
    svg: &mut String,
    y: f64,
    values: &[T; 5],
    start_x: f64,
) -> Result<(), AppError> {
    for (index, value) in values.iter().enumerate() {
        writeln!(
            svg,
            r##"<text x="{:.1}" y="{y:.1}" class="table">{}</text>"##,
            start_x + index as f64 * 48.0,
            escape_xml(value.as_ref()),
        )
        .map_err(svg_fmt_error)?;
    }
    Ok(())
}

#[allow(clippy::too_many_arguments)]
fn write_card<S>(
    svg: &mut String,
    score: &RenderRecord,
    index: usize,
    x: f64,
    y: f64,
    is_ap_card: bool,
    push_acc_map: Option<&HashMap<String, engine::PushAccHint, S>>,
    engine_records: &[engine::RksRecord],
    embed_images: bool,
    public_illustration_base_url: Option<&str>,
) -> Result<(), AppError>
where
    S: std::hash::BuildHasher,
{
    let difficulty = score.difficulty.to_ascii_uppercase();
    let difficulty_fill = match difficulty.as_str() {
        "AT" => "#6e6e6e",
        "HD" => "#00b0f0",
        "EZ" => "#92d050",
        _ => "#ff0000",
    };
    let (info_fill, info_border) = if difficulty == "AT" {
        ("#ff6670", "#ff3347")
    } else {
        ("#00b7f0", "#00b7f0")
    };
    let rank_label = if is_ap_card {
        format!("P{}", index + 1)
    } else {
        format!("#{}", index + 1)
    };
    let (title_lines, title_font_size) = layout_song_title(&score.song_name);
    let score_text = score
        .score
        .map_or_else(|| "N/A".to_string(), |value| format!("{:.0}", value));
    let grade = grade_for(score);
    let push_hint = if score.acc >= 100.0 {
        Some(engine::PushAccHint::AlreadyPhi)
    } else {
        resolve_push_acc_hint(
            score,
            pre_calculated_push_acc_for_score(score, push_acc_map),
            engine_records,
        )
    };
    let push_text = push_acc_text(push_hint);
    let push_fill = push_acc_fill(push_hint);
    let cover_href = resolve_card_cover_href(
        &score.song_id,
        embed_images,
        public_illustration_base_url,
        180.0,
        95.0,
    );
    let clip_id = format!("phi-cover-{}-{}", if is_ap_card { "p" } else { "b" }, index);
    let icon_filter = if grade == "AP" {
        "ap-icon-glow"
    } else {
        "icon-glow"
    };
    let card_halo_filter = if is_ap_card {
        "card-edge-halo-gold"
    } else {
        "card-edge-halo"
    };

    writeln!(svg, r##"<g transform="translate({x:.1} {y:.1})">"##).map_err(svg_fmt_error)?;

    // Render the edge glow from a dedicated card silhouette. P1-P3 use the
    // gold variant; both filters share the same bounds, blur and opacity.
    // SourceAlpha is removed so no glow is painted inside the score panel or
    // over its text and icons.
    writeln!(
        svg,
        r##"<g filter="url(#{card_halo_filter})"><path d="M28.476 2.54 H180 L151.524 97.46 H0 Z" fill="#fff"/><rect x="141" y="5" width="204.6" height="90" fill="#fff"/></g>"##
    )
    .map_err(svg_fmt_error)?;

    // The score panel is the translucent rectangular layer from b19.css. It
    // deliberately overlaps the illustration and has only a right accent line.
    writeln!(
        svg,
        r##"<g filter="url(#card-shadow)"><rect x="141" y="5" width="204.6" height="90" fill="{info_fill}" fill-opacity=".30"/><path d="M345.6 5 V95" stroke="{info_border}" stroke-width="3"/>"##,
    )
    .map_err(svg_fmt_error)?;
    let title_line_gap = title_font_size + 1.0;
    let title_start_y = if title_lines.len() > 1 {
        25.0 - title_line_gap / 2.0
    } else {
        25.0
    };
    for (line_index, line) in title_lines.iter().enumerate() {
        writeln!(
            svg,
            r##"<text x="259" y="{:.1}" class="card-title" style="font-size:{title_font_size:.1}px">{}</text>"##,
            title_start_y + line_index as f64 * title_line_gap,
            escape_xml(line),
        )
        .map_err(svg_fmt_error)?;
    }
    if let Some(icon) = grade_icon_data_uri(grade) {
        writeln!(
            svg,
            r##"<image href="{}" x="166" y="35" width="40" height="44" preserveAspectRatio="xMidYMid meet" filter="url(#{icon_filter})"/>"##,
            escape_xml(icon),
        )
        .map_err(svg_fmt_error)?;
    } else {
        writeln!(
            svg,
            r##"<text x="186" y="68" font-size="30" text-anchor="middle">{}</text>"##,
            escape_xml(grade),
        )
        .map_err(svg_fmt_error)?;
    }
    writeln!(
        svg,
        r##"<text x="268.5" y="58" class="score">{}</text><path d="M206 65 H339" stroke="#fff" stroke-width="2"/><text x="233.2" y="84" class="acc">{:.2}%</text><rect x="268.5" y="71" width="70.5" height="16" rx="8" fill="{push_fill}" fill-opacity=".94"/><circle cx="279" cy="79" r="2.8" fill="none" stroke="#fff" stroke-width="1.6"/><text x="308" y="82.6" class="suggest">{}</text></g>"##,
        escape_xml(&score_text),
        score.acc,
        escape_xml(&push_text),
    )
    .map_err(svg_fmt_error)?;

    // The illustration uses the same full parallelogram clipping formula as
    // .clip-box: both the left and right edges slope by height*0.3.
    writeln!(
        svg,
        r##"<path d="M28.476 2.54 H180 L151.524 97.46 H0 Z" fill="url(#cover-placeholder)" filter="url(#card-shadow)"/><clipPath id="{clip_id}"><path d="M28.476 2.54 H180 L151.524 97.46 H0 Z"/></clipPath>"##,
    )
    .map_err(svg_fmt_error)?;
    if let Some(href) = cover_href {
        writeln!(
            svg,
            r##"<image href="{}" x="0" y="2.54" width="180" height="94.92" preserveAspectRatio="xMidYMid slice" clip-path="url(#{clip_id})"/>"##,
            escape_xml(&href),
        )
        .map_err(svg_fmt_error)?;
    }

    // Number and difficulty badges are complete double-slanted
    // parallelograms; their negative margin mirrors the HTML positioning.
    writeln!(
        svg,
        r##"<path d="M1.08 0 H28.8 L24.12 15.6 H-3.6 Z" fill="#fff"/><text x="12.6" y="12" class="rank-tag">{}</text><path d="M-14.004 68.68 H36 L26.604 100 H-23.4 Z" fill="{difficulty_fill}"/><text x="6.3" y="82.5" class="diff-org">{} {:.1}</text><text x="6.3" y="97" class="diff-rks">{:.2}</text></g>"##,
        escape_xml(&rank_label),
        escape_xml(&difficulty),
        score.difficulty_value,
        score.rks,
    )
    .map_err(svg_fmt_error)
}

fn write_overflow_divider(svg: &mut String, y: f64) -> Result<(), AppError> {
    writeln!(svg, r##"<g transform="translate(0 {y:.1})">"##).map_err(svg_fmt_error)?;
    let widths = [228.0, 114.0, 57.0, 28.5, 14.25, 7.125];
    let mut left_x = 36.0;
    for width in widths {
        writeln!(
            svg,
            r##"<path d="M{:.3} 22 H{:.3} L{:.3} 37 H{:.3} Z" fill="#fff"/>"##,
            left_x + 4.5,
            left_x + width,
            left_x + width - 4.5,
            left_x,
        )
        .map_err(svg_fmt_error)?;
        left_x += width;
    }
    let mut right_x = 714.875;
    for width in widths.into_iter().rev() {
        writeln!(
            svg,
            r##"<path d="M{:.3} 22 H{:.3} L{:.3} 37 H{:.3} Z" fill="#fff"/>"##,
            right_x + 4.5,
            right_x + width,
            right_x + width - 4.5,
            right_x,
        )
        .map_err(svg_fmt_error)?;
        right_x += width;
    }
    writeln!(
        svg,
        r##"<text x="600" y="42" class="overflow">OVER FLOW</text></g>"##,
    )
    .map_err(svg_fmt_error)
}

fn push_acc_text(hint: Option<engine::PushAccHint>) -> String {
    match hint {
        Some(engine::PushAccHint::TargetAcc { acc }) => format!("{acc:.2}%"),
        Some(engine::PushAccHint::PhiOnly) => "100.00%".to_string(),
        Some(engine::PushAccHint::AlreadyPhi | engine::PushAccHint::Unreachable) => {
            "无法推分".to_string()
        }
        None => "--".to_string(),
    }
}

fn layout_song_title(title: &str) -> (Vec<String>, f64) {
    const TITLE_WIDTH: f64 = 180.0;
    const NORMAL_FONT_SIZE: f64 = 15.0;
    const WRAPPED_MAX_FONT_SIZE: f64 = 13.0;
    const MIN_FONT_SIZE: f64 = 8.5;

    let title = title.trim();
    if title_visual_width(title) * NORMAL_FONT_SIZE <= TITLE_WIDTH {
        return (vec![title.to_string()], NORMAL_FONT_SIZE);
    }

    let chars: Vec<char> = title.chars().collect();
    let mut best: Option<(f64, String, String)> = None;
    for split in 1..chars.len() {
        let left: String = chars[..split]
            .iter()
            .collect::<String>()
            .trim_end()
            .to_string();
        let right: String = chars[split..]
            .iter()
            .collect::<String>()
            .trim_start()
            .to_string();
        if left.is_empty() || right.is_empty() {
            continue;
        }
        let left_width = title_visual_width(&left);
        let right_width = title_visual_width(&right);
        let boundary_penalty = if chars[split - 1].is_whitespace()
            || chars[split].is_whitespace()
            || matches!(chars[split - 1], '-' | '/' | ':' | '：' | '·')
        {
            0.0
        } else {
            100.0
        };
        let score = (left_width - right_width).abs() + boundary_penalty;
        if best
            .as_ref()
            .is_none_or(|(best_score, _, _)| score < *best_score)
        {
            best = Some((score, left, right));
        }
    }

    let (_, left, right) = best.unwrap_or_else(|| (0.0, title.to_string(), String::new()));
    let max_width = title_visual_width(&left).max(title_visual_width(&right));
    let font_size = (TITLE_WIDTH / max_width).clamp(MIN_FONT_SIZE, WRAPPED_MAX_FONT_SIZE);
    let lines = if right.is_empty() {
        vec![left]
    } else {
        vec![left, right]
    };
    (lines, font_size)
}

fn title_visual_width(text: &str) -> f64 {
    text.chars()
        .map(|ch| {
            if ch.is_whitespace() {
                0.35
            } else if ch.is_ascii_punctuation() {
                0.45
            } else if ch.is_ascii() {
                0.60
            } else {
                1.0
            }
        })
        .sum()
}

fn push_acc_fill(hint: Option<engine::PushAccHint>) -> &'static str {
    let target_acc = match hint {
        Some(engine::PushAccHint::TargetAcc { acc }) => Some(acc),
        Some(engine::PushAccHint::PhiOnly) => Some(100.0),
        Some(engine::PushAccHint::AlreadyPhi | engine::PushAccHint::Unreachable) | None => None,
    };
    match target_acc {
        None => "#384349",
        Some(acc) if acc >= 99.7 => "#8D5ABF",
        Some(acc) if acc >= 99.5 => "#4FC5C5",
        Some(acc) if acc >= 99.0 => "#56C67A",
        Some(acc) if acc >= 98.5 => "#C99C65",
        Some(_) => "#CD5257",
    }
}

fn grade_for(score: &RenderRecord) -> &'static str {
    let value = score.score.unwrap_or_default();
    if value >= 1_000_000.0 || score.acc >= 100.0 {
        "AP"
    } else if score.is_fc {
        "FC"
    } else if value >= 960_000.0 {
        "V"
    } else if value >= 920_000.0 {
        "S"
    } else if value >= 880_000.0 {
        "A"
    } else if value >= 820_000.0 {
        "B"
    } else if value >= 700_000.0 {
        "C"
    } else {
        "F"
    }
}

fn rank_palette(rank_color: Option<&str>) -> RankPalette {
    match rank_color {
        Some("Green") => RankPalette { primary: "#17b85c" },
        Some("Blue") => RankPalette { primary: "#168ee4" },
        Some("Gold") => RankPalette { primary: "#d29a20" },
        Some("Rainbow") => RankPalette { primary: "#a75cff" },
        _ => RankPalette { primary: "#d22436" },
    }
}

fn player_avatar_data_uri(avatar_name: Option<&str>) -> Option<String> {
    let avatar_name = avatar_name?.trim();
    if avatar_name.is_empty() {
        return None;
    }
    let avatar_dir = std::env::var_os("APP_AVATAR_DIR")
        .map(PathBuf::from)
        .or_else(|| {
            std::env::current_exe()
                .ok()?
                .parent()?
                .parent()?
                .parent()
                .map(|root| root.join("avatar"))
        })?;
    let hash = hex::encode(Sha256::digest(avatar_name.as_bytes()));
    get_scaled_image_data_uri(&avatar_dir.join(format!("{hash}.png")), 320, 160)
}

fn phi_plugin_asset_data_uri(file: &str) -> Option<&'static str> {
    static ASSETS: OnceLock<HashMap<&'static str, String>> = OnceLock::new();
    ASSETS
        .get_or_init(|| {
            let base = AppConfig::global()
                .resources_path()
                .join("templates")
                .join("image")
                .join("bn")
                .join("phi_plugin_assets");
            [
                "grade_ap.png",
                "grade_v.png",
                "grade_s.png",
                "grade_a.png",
                "grade_b.png",
                "grade_c.png",
                "grade_f.png",
                "grade_fc.png",
                "challenge_green.png",
                "challenge_blue.png",
                "challenge_red.png",
                "challenge_gold.png",
                "challenge_rainbow.png",
                "data.png",
            ]
            .into_iter()
            .filter_map(|file| {
                fs::read(base.join(file)).ok().map(|bytes| {
                    (
                        file,
                        format!("data:image/png;base64,{}", BASE64.encode(bytes)),
                    )
                })
            })
            .collect()
        })
        .get(file)
        .map(String::as_str)
}

fn grade_icon_data_uri(grade: &str) -> Option<&'static str> {
    let file = match grade {
        "AP" => "grade_ap.png",
        "FC" => "grade_fc.png",
        "V" => "grade_v.png",
        "S" => "grade_s.png",
        "A" => "grade_a.png",
        "B" => "grade_b.png",
        "C" => "grade_c.png",
        "F" => "grade_f.png",
        _ => return None,
    };
    phi_plugin_asset_data_uri(file)
}

fn challenge_plate_data_uri(rank_color: Option<&str>) -> Option<&'static str> {
    let file = match rank_color? {
        "Green" => "challenge_green.png",
        "Blue" => "challenge_blue.png",
        "Red" => "challenge_red.png",
        "Gold" => "challenge_gold.png",
        "Rainbow" => "challenge_rainbow.png",
        _ => return None,
    };
    phi_plugin_asset_data_uri(file)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn record(score: f64, acc: f64) -> RenderRecord {
        RenderRecord {
            song_id: "test".to_string(),
            song_name: "Test".to_string(),
            difficulty: "IN".to_string(),
            score: Some(score),
            acc,
            rks: 15.0,
            difficulty_value: 15.0,
            is_fc: false,
        }
    }

    #[test]
    fn grade_thresholds_follow_phi_plugin_cards() {
        assert_eq!(grade_for(&record(1_000_000.0, 100.0)), "AP");
        assert_eq!(grade_for(&record(960_000.0, 96.0)), "V");
        assert_eq!(grade_for(&record(920_000.0, 92.0)), "S");
        assert_eq!(grade_for(&record(880_000.0, 88.0)), "A");
    }

    #[test]
    fn full_combo_uses_the_blue_fc_icon_grade() {
        let mut score = record(975_000.0, 98.0);
        score.is_fc = true;
        assert_eq!(grade_for(&score), "FC");
    }

    #[test]
    fn push_acc_colors_follow_requested_thresholds() {
        use engine::PushAccHint::{AlreadyPhi, TargetAcc, Unreachable};

        assert_eq!(push_acc_fill(Some(AlreadyPhi)), "#384349");
        assert_eq!(push_acc_fill(Some(Unreachable)), "#384349");
        assert_eq!(push_acc_fill(Some(TargetAcc { acc: 99.7 })), "#8D5ABF");
        assert_eq!(push_acc_fill(Some(TargetAcc { acc: 99.5 })), "#4FC5C5");
        assert_eq!(push_acc_fill(Some(TargetAcc { acc: 99.0 })), "#56C67A");
        assert_eq!(push_acc_fill(Some(TargetAcc { acc: 98.5 })), "#C99C65");
        assert_eq!(push_acc_fill(Some(TargetAcc { acc: 98.499 })), "#CD5257");
    }

    #[test]
    fn long_song_titles_wrap_without_ellipsis_and_preserve_all_text() {
        let title = "Labyrinth in Kowloon: Walled World";
        let (lines, font_size) = layout_song_title(title);

        assert_eq!(lines.len(), 2);
        assert_eq!(lines.join(" "), title);
        assert!(lines.iter().all(|line| !line.contains("...")));
        assert!(font_size < 15.0);
    }

    #[test]
    fn challenge_rank_changes_background_palette() {
        assert_eq!(rank_palette(Some("Green")).primary, "#17b85c");
        assert_eq!(rank_palette(Some("Rainbow")).primary, "#a75cff");
        assert_eq!(rank_palette(Some("Red")).primary, "#d22436");
    }
}
