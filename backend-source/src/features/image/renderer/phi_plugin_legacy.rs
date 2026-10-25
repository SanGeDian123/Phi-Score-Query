//! Phi-Plugin's pre-2025-02-14 800px B19 composition: a Phi card and two
//! staggered columns, diagonal covers, translucent blue/red score panels.
use std::{collections::HashMap, fmt::Write, path::Path};
use crate::rks_contract::engine::PushAccHint;
use crate::{error::AppError, features::image::Theme};
use super::{PlayerStats, RenderRecord, MAIN_FONT_NAME};
use super::bn_background::select_random_background;
use super::bn_card_cover::resolve_card_cover_href;
use super::resources::get_scaled_image_data_uri;
use super::resource_fonts::PHI_PLUGIN_FONT_NAME;
use super::svg_error::svg_fmt_error;
use super::text::escape_xml;

pub(super) fn generate<S: std::hash::BuildHasher>(scores: &[RenderRecord], stats: &PlayerStats, push_map: Option<&HashMap<String, PushAccHint, S>>, theme: Theme,
    embed: bool, public_base: Option<&str>) -> Result<String, AppError> {
    let is_p30 = stats.image_title == "P30";
    let row_count = if is_p30 { scores.len().div_ceil(2) } else { (scores.len() + 1).div_ceil(2) };
    let height = (230 + row_count.saturating_sub(1) * 108 + 64 + 100 + 70) as u32;
    let bg = select_random_background(&theme, embed, public_base, 800, height, "#fff".into());
    let mut svg = String::with_capacity(100_000);
    writeln!(svg, r##"<svg xmlns="http://www.w3.org/2000/svg" width="800" height="{height}" viewBox="0 0 800 {height}">
<defs><style>text{{font-family:'{PHI_PLUGIN_FONT_NAME}','{MAIN_FONT_NAME}',sans-serif;fill:#fff}} .meta{{font-size:16px}} .digits{{font-family:'{PHI_PLUGIN_FONT_NAME}','{MAIN_FONT_NAME}';font-size:24px}} </style>
<clipPath id="old-icon"><rect x="14" y="56" width="118" height="118" rx="28"/></clipPath></defs><rect width="800" height="{height}" fill="#12212f"/>"##).map_err(svg_fmt_error)?;
    if let Some(href) = bg.image_href {
        writeln!(svg, r##"<image href="{}" width="800" height="{height}" preserveAspectRatio="xMidYMid slice"/><rect width="800" height="{height}" fill="#000" opacity=".25"/>"##, escape_xml(&href)).map_err(svg_fmt_error)?;
    }
    writeln!(svg, r##"<path d="M0 40 H456 L410.4 190 H0 Z" fill="#000" opacity=".5"/><path d="M455.4 47.5 H800 V182.5 H412.8 Z" fill="#000" opacity=".5"/><path d="M456 40 L412.8 190" stroke="#fff" stroke-width="2"/>"##).map_err(svg_fmt_error)?;
    let icon = get_scaled_image_data_uri(Path::new("resources/templates/image/bn/phi_plugin_assets/legacy_phigros_icon.png"), 236, 236);
    if let Some(icon) = icon {
        writeln!(svg, r##"<image href="{}" x="14" y="56" width="118" height="118" clip-path="url(#old-icon)"/>"##, escape_xml(&icon)).map_err(svg_fmt_error)?;
    }
    let name = stats.player_name.as_deref().unwrap_or("Phigros Player");
    let name: String = name.chars().take(31).collect();
    let level = stats.challenge_rank.as_ref().map_or("--", |(_, l)| l.as_str());
    let data = stats.data_string.as_deref().unwrap_or("--").trim_start_matches("Data:").trim();
    writeln!(svg, r##"<text x="149.9" y="101" font-size="30">Phigros</text><text x="149.9" y="150" font-size="30">RankingScore查询</text>
<text x="461" y="73" class="meta">Player: {}</text><text x="461" y="97" class="meta">RankingScore: {:.4}</text>
<text x="461" y="121" class="meta">ChallengeMode:</text><text x="461" y="145" class="meta">Data: {}</text><text x="461" y="169" class="meta">Date: {}</text>"##,
        escape_xml(&name), stats.real_rks.unwrap_or_default(), escape_xml(data),
        stats.update_time.format("%Y %b %d %H:%M:%S")).map_err(svg_fmt_error)?;
    if let Some(plate) = super::phi_plugin::challenge_plate_data_uri(stats.challenge_rank.as_ref().map(|(c, _)| c.as_str())) {
        writeln!(svg, r##"<image href="{}" x="580.2" y="106" width="47.4" height="20"/><text x="604" y="119.4" text-anchor="middle" font-size="16">{}</text>"##, escape_xml(plate), escape_xml(level)).map_err(svg_fmt_error)?;
    }
    if !is_p30 {
        if let Some(phi) = stats.ap_top_3_scores.first() {
            card(&mut svg, phi, "Phi", 36.0, 230.0, Some(PushAccHint::AlreadyPhi), embed, public_base)?;
        } else {
            writeln!(svg, r##"<g transform="translate(36 230)"><rect width="348" height="94" fill="#666" opacity=".5"/><text x="14" y="40" font-size="24">TIME_OUT</text><text x="14" y="65" font-size="10">&gt;&gt;&gt; PhigrOS Client Finding Phi.score</text></g>"##).map_err(svg_fmt_error)?;
        }
    }
    for (i, record) in scores.iter().enumerate() {
        let num = i + 1;
        let (x, y) = if is_p30 {
            (if i % 2 == 0 { 36.0 } else { 428.0 }, 230.0 + (i / 2) as f64 * 108.0 + if i % 2 == 0 { 0.0 } else { 64.0 })
        } else if num % 2 == 0 { (36.0, 230.0 + (num / 2) as f64 * 108.0) }
        else { (428.0, 294.0 + (num / 2) as f64 * 108.0) };
        let hint = push_map.and_then(|map| map.get(&format!("{}-{}", record.song_id, record.difficulty))).copied();
        card(&mut svg, record, &format!("#{num}"), x, y, hint, embed, public_base)?;
    }
    let footer_y = height - 28;
    writeln!(svg, r##"<text x="780" y="{footer_y}" text-anchor="end" style="font-family:'{MAIN_FONT_NAME}',sans-serif;fill:#d0d7da;font-size:12px">{}</text></svg>"##,
        escape_xml(stats.custom_footer_text.as_deref().unwrap_or("Phi Score Query"))).map_err(svg_fmt_error)?;
    Ok(svg)
}

fn card(svg: &mut String, r: &RenderRecord, label: &str, x: f64, y: f64, hint: Option<PushAccHint>, embed: bool,
    public_base: Option<&str>) -> Result<(), AppError> {
    let key = format!("legacy-{}", label.replace('#', ""));
    let color = if r.difficulty == "AT" { "#f00000" } else { "#00b7f0" };
    let diff_color = match r.difficulty.as_str() { "AT" => "#777", "IN" => "#f00", "HD" => "#00b0f0", _ => "#92d050" };
    writeln!(svg, r##"<g transform="translate({x} {y})"><defs><clipPath id="{key}"><path d="M32.68 0 H180 L147.32 94.92 H0 Z"/></clipPath></defs>
<rect x="141.03" y="5" width="204.58" height="90" fill="{color}" fill-opacity=".30"/><path d="M345.61 5 V95" stroke="{color}" stroke-width="3"/><path d="M32.68 0 H180 L147.32 94.92 H0 Z" fill="#28424e"/>"##).map_err(svg_fmt_error)?;
    if let Some(href) = resolve_card_cover_href(&r.song_id, embed, public_base, 190.0, 100.0) {
        writeln!(svg, r##"<image href="{}" x="0" width="180" height="94.92" preserveAspectRatio="xMidYMid slice" clip-path="url(#{key})"/>"##, escape_xml(&href)).map_err(svg_fmt_error)?;
    }
    writeln!(svg, r##"<path d="M7.68 3.8 H34.19 L28.31 18.99 H1.8 Z" fill="#fff"/><text x="18" y="15.8" text-anchor="middle" style="fill:#000;font-size:10px">{}</text><path d="M-12.61 63.61 H35.99 L25.21 94.92 H-23.39 Z" fill="{diff_color}"/><text x="6.3" y="78.8" font-size="12" text-anchor="middle">{} {:.1}</text><text x="6.3" y="89.75" font-size="12" text-anchor="middle">{:.2}</text>"##,
        escape_xml(label), escape_xml(&r.difficulty), r.difficulty_value, r.rks).map_err(svg_fmt_error)?;
    let (title_lines, font_size) = layout_title(&r.song_name);
    writeln!(svg, r##"<clipPath id="{key}-title"><rect x="175" y="5" width="167.56" height="22.5"/></clipPath><g clip-path="url(#{key}-title)">"##).map_err(svg_fmt_error)?;
    let title_y = if title_lines.len() == 1 { 22.0 } else { 14.0 };
    for (i, line) in title_lines.iter().enumerate() {
        writeln!(svg, r##"<text x="258.8" y="{}" font-size="{font_size}" text-anchor="middle">{}</text>"##,
            title_y + i as f64 * 10.5, escape_xml(line)).map_err(svg_fmt_error)?;
    }
    let (suggest, suggest_color) = match hint {
        Some(PushAccHint::TargetAcc { acc }) => (format!(">> {acc:.2}%"), "#c0ddad"),
        Some(PushAccHint::PhiOnly) => (">> 100.00%".into(), "#c0ddad"),
        Some(PushAccHint::AlreadyPhi | PushAccHint::Unreachable) => (">> 无法推分".into(), "#c0ddad"),
        None => (String::new(), "#c0ddad"),
    };
    writeln!(svg, r##"</g><text x="272.04" y="54.5" class="digits" text-anchor="middle">{:.0}</text><path d="M201.48 64.5 H328.47" stroke="#fff" stroke-width="2"/><text x="204.86" y="83.5" font-size="16">{:.2}%</text><text x="275.19" y="79.5" font-size="12" fill="{suggest_color}" style="fill:{suggest_color}">{}</text>"##,
        r.score.unwrap_or_default(), r.acc, escape_xml(&suggest)).map_err(svg_fmt_error)?;
    let grade = super::phi_plugin::grade_for(r);
    if let Some(icon) = super::phi_plugin::grade_icon_data_uri(grade) {
        writeln!(svg, r##"<image href="{}" x="166.22" y="46.39" width="35.27" height="34.95" preserveAspectRatio="xMidYMid meet"/>"##, escape_xml(icon)).map_err(svg_fmt_error)?;
    } else {
        writeln!(svg, r##"<text x="184" y="75" font-size="30" text-anchor="middle">{grade}</text>"##).map_err(svg_fmt_error)?;
    }
    svg.push_str("</g>\n");
    Ok(())
}

fn title_width(value: &str) -> f64 {
    use resvg::usvg::fontdb::{Family, Query};
    let db = super::resources::get_global_font_db();
    let face = db.query(&Query { families: &[Family::Name(PHI_PLUGIN_FONT_NAME)], ..Default::default() });
    face.and_then(|id| db.with_face_data(id, |data, index| {
        ttf_parser::Face::parse(data, index).ok().map(|font| {
            let units = f64::from(font.units_per_em());
            value.chars().map(|ch| font.glyph_index(ch).and_then(|g| font.glyph_hor_advance(g))
                .map_or(if ch.is_ascii() { 0.6 } else { 1.0 }, |advance| f64::from(advance) / units)).sum()
        })
    })).flatten().unwrap_or_else(|| value.chars().map(|ch| if ch.is_ascii() { 0.6 } else { 1.0 }).sum())
}

fn layout_title(value: &str) -> (Vec<String>, f64) {
    let value = value.trim();
    let single_size = (164.0 / title_width(value).max(0.1)).min(15.0);
    if single_size >= 11.0 { return (vec![value.into()], single_size.floor()); }
    let chars = value.chars().collect::<Vec<_>>();
    let (_, left, right) = (1..chars.len()).filter_map(|i| {
        let left = chars[..i].iter().collect::<String>().trim().to_string();
        let right = chars[i..].iter().collect::<String>().trim().to_string();
        if left.is_empty() || right.is_empty() { return None; }
        let split_word = chars[i - 1].is_ascii_alphanumeric() && chars[i].is_ascii_alphanumeric();
        let a = title_width(&left); let b = title_width(&right);
        Some((a.max(b) + (a - b).abs() * 0.15 + if split_word { 8.0 } else { 0.0 }, left, right))
    }).min_by(|a, b| a.0.total_cmp(&b.0)).unwrap_or((0.0, value.into(), String::new()));
    let font_size = (164.0 / title_width(&left).max(title_width(&right))).clamp(8.5, 10.0);
    let mut lines = vec![left, right];
    for line in &mut lines {
        if title_width(line) * font_size > 164.0 {
            while title_width(&format!("{line}…")) * font_size > 164.0 { line.pop(); }
            line.push('…');
        }
    }
    (lines, font_size)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn time_rewind_legacy_b19_preview() {
        let _ = crate::config::AppConfig::init_global();
        let constants = crate::startup::chart_loader::parse_chart_constants(
            include_bytes!(concat!(env!("CARGO_MANIFEST_DIR"), "/resources/difficulty_20250214.csv")).as_slice()).unwrap();
        let mut charts = constants.iter().flat_map(|(id, c)|
            [("EZ", c.ez), ("HD", c.hd), ("IN", c.in_level), ("AT", c.at)].into_iter()
                .filter_map(move |(diff, c)| c.map(|c| (id.clone(), diff, c as f64)))
        ).collect::<Vec<_>>();
        charts.retain(|(id, _, _)| Path::new("resources/ill/illLow").join(format!("{id}.png")).is_file());
        charts.sort_by(|a, b| b.2.total_cmp(&a.2).then(a.0.cmp(&b.0)));
        let catalog = csv::Reader::from_path("info/info.csv").unwrap().records().filter_map(Result::ok)
            .map(|r| (r[0].to_string(), r[1].to_string())).collect::<std::collections::HashMap<_, _>>();
        let mut all = charts.into_iter().take(120).enumerate().map(|(i, (id, diff, c))| {
            let acc = if i % 4 == 0 { 100.0 } else { 99.7 - (i % 9) as f64 * 0.08 };
            let name = catalog.get(&id).unwrap_or(&id).to_string();
            RenderRecord { song_id: id, song_name: name, difficulty: diff.into(),
                score: Some(if acc == 100.0 { 1_000_000.0 } else { 990_000.0 - (i % 9) as f64 * 730.0 }), acc,
                rks: crate::rks_contract::engine::calculate_chart_rks(acc, c), difficulty_value: c, is_fc: i % 3 == 0 }
        }).collect::<Vec<_>>();
        all.sort_by(|a, b| b.rks.total_cmp(&a.rks));
        let phi = all.iter().find(|r| r.score == Some(1_000_000.0)).unwrap().clone();
        let rks = (all.iter().take(19).map(|r| r.rks).sum::<f64>() + phi.rks) / 20.0;
        let mut stats = PlayerStats { image_title: "B19".into(), ap_top_3_avg: Some(phi.rks), best_27_avg: Some(all.iter().take(19).map(|r| r.rks).sum::<f64>() / 19.0),
            real_rks: Some(rks), player_name: Some("PSQ".into()), player_avatar: None,
            update_time: chrono::Utc::now(), n: 21, ap_top_3_scores: vec![phi],
            challenge_rank: Some(("Gold".into(), "46".into())), data_string: Some("3GiB 877MiB 674KiB".into()),
            grade_counts: Default::default(), custom_footer_text: Some("Phi Score Query · 时间回溯模式，仅供娱乐".into()), is_user_generated: false };
        let hints = crate::features::image::handler::time_rewind::push_hints(&all, 21);
        let svg = generate(&all[..21], &stats, Some(&hints), Theme::Black, true, None).unwrap();
        assert_eq!(svg.matches("class=\"digits\"").count(), 22);
        assert!(svg.contains("#21"));
        assert!(svg.contains("时间回溯模式，仅供娱乐"));
        assert!(!svg.contains(">Phi-Plugin<"));
        if let Some(out) = std::env::var_os("TIME_REWIND_PREVIEW_DIR") {
            let out = std::path::PathBuf::from(out); std::fs::create_dir_all(&out).unwrap();
            std::fs::write(out.join("09-Phi-Plugin-B19.svg"), &svg).unwrap();
            let png = super::super::render_svg_to_png(&svg, false).unwrap();
            std::fs::write(out.join("09-Phi-Plugin-B19.png"), png).unwrap();
            stats.n = 19;
            for (filename, template) in [("10-经典-B19", None), ("11-简约-B19", Some("minimal"))] {
                let svg = super::super::generate_svg_string(&all[..19], &stats, Some(&hints), &Theme::White, true, None, template).unwrap();
                assert!(svg.contains("时间回溯模式，仅供娱乐"));
                assert!(!svg.contains("Best 27 Avg"));
                std::fs::write(out.join(format!("{filename}.svg")), &svg).unwrap();
                let png = super::super::render_svg_to_png(&svg, false).unwrap();
                std::fs::write(out.join(format!("{filename}.png")), png).unwrap();
            }
        }
    }
    #[test]
    fn time_rewind_title_lines_remain_inside_the_score_panel() {
        for title in ["Compute It With Some Devilish Alcoholic Steampunk Engines", "新年多指选曲看这里各位新人有福音新年挑战可以让你彻底摆脱双指萌新的困境", "W".repeat(150).as_str()] {
            let (lines, size) = layout_title(title);
            assert!(lines.len() <= 2);
            assert!(lines.iter().all(|line| title_width(line) * size <= 164.01));
        }
    }
}
