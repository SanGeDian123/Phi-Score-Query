//! Run from a backend runtime directory containing config.toml and artwork.
//! Each invocation isolates one real raster render for external memory sampling.
use phi_backend::{config::AppConfig, features::image::{Theme, PlayerStats, RenderRecord, generate_svg_string, render_svg_to_png}};
use std::{collections::hash_map::RandomState, fs, path::PathBuf, time::Instant};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    AppConfig::init_global()?;
    let args: Vec<String> = std::env::args().collect();
    let style = args.get(1).map(String::as_str).unwrap_or("phi-plugin");
    let n: usize = args.get(2).map(String::as_str).unwrap_or("200").parse()?;
    let output = PathBuf::from(args.get(3).map(String::as_str).unwrap_or("best200.png"));
    let mut covers: Vec<_> = fs::read_dir("resources/ill/illLow")?.flatten()
        .map(|entry| entry.path()).filter(|path| path.extension().is_some_and(|e| e == "png" || e == "jpg")).collect();
    covers.sort();
    assert!(covers.len() >= n, "probe requires {n} distinct real covers");
    let records: Vec<_> = covers.iter().take(n).enumerate().map(|(i, path)| {
        let id = path.file_stem().unwrap().to_string_lossy().into_owned();
        RenderRecord { song_name: id.split('.').next().unwrap().into(), song_id: id,
            difficulty: "IN".into(), score: Some(998765.0 - i as f64 * 101.0),
            acc: 99.8 - i as f64 * 0.005, rks: 16.4 - i as f64 * 0.01,
            difficulty_value: 16.4 - i as f64 * 0.005, is_fc: i % 3 == 0 }
    }).collect();
    let stats = PlayerStats { image_title: format!("Best {n}"), ap_top_3_avg: Some(16.39),
        best_27_avg: Some(16.25), real_rks: Some(16.264), player_name: Some("PSQ render probe".into()),
        player_avatar: None, update_time: chrono::Utc::now(), n: n as u32,
        ap_top_3_scores: vec![], challenge_rank: Some(("Gold".into(), "48".into())),
        data_string: Some("Data: 2 GiB".into()), grade_counts: Default::default(),
        custom_footer_text: Some("Phi Score Query".into()), is_user_generated: false };
    let started = Instant::now();
    let svg = if let Some(source) = args.get(4) { fs::read_to_string(source)? } else {
        generate_svg_string::<RandomState>(&records, &stats, None, &Theme::White,
            false, None, (style != "classic").then_some(style))?
    };
    println!("svg_bytes={} svg_ms={}", svg.len(), started.elapsed().as_millis());
    fs::write(output.with_extension("svg"), &svg)?;
    let root_end = svg.find('>').unwrap();
    let root = &svg[..root_end];
    let height: u32 = root.split("height=\"").nth(1).unwrap().split('"').next().unwrap().parse()?;
    let source_width: u32 = root.split("width=\"").nth(1).unwrap().split('"').next().unwrap().parse()?;
    let scaled_root = root.replace(&format!("width=\"{source_width}\""), "width=\"1440\"").replace(&format!("height=\"{height}\""), &format!("height=\"{}\"", (height as f64 * 1440.0 / source_width as f64).round() as u32));
    let svg = format!("{}{}", scaled_root, &svg[root_end..]);
    let png = render_svg_to_png(&svg, false)?;
    let image = image::load_from_memory(&png)?;
    println!("style={style} count={n} width={} height={} png_bytes={} total_ms={}",
        image.width(), image.height(), png.len(), started.elapsed().as_millis());
    fs::write(output, png)?;
    Ok(())
}
