//! Practice discovery from the installed PEZ directory. No client release is needed for additions.
use std::{collections::HashMap, fs, io::Read, path::{Path, PathBuf}, sync::{Arc, Mutex, LazyLock}, time::SystemTime};
use axum::{http::{StatusCode, header, HeaderMap}, response::{IntoResponse, Response}, Json};
use sha2::{Digest, Sha256};
use serde::Serialize;
use serde_json::Value;
use super::song::models::{SongCatalog, normalize_song_search_text};

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PracticeChart {
    song_id: String,
    title: String,
    difficulty: String,
    chart_constant: f32,
    file_name: String,
    size_bytes: u64,
    revision: String,
}
#[derive(Default)]
struct Cache { files: HashMap<PathBuf, (u64, SystemTime, Option<Value>)> }
static CACHE: LazyLock<Mutex<Cache>> = LazyLock::new(|| Mutex::new(Cache::default()));

fn directory() -> PathBuf {
    if let Some(path) = std::env::var_os("APP_PRACTICE_CHART_DIR") { return path.into(); }
    if let Some(path) = std::env::var_os("APP_UPDATE_DIR") { return PathBuf::from(path).join("practice-charts"); }
    // Installed layout: <root>/current/backend/phi-backend.exe.
    std::env::current_exe().ok().and_then(|p| p.parent()?.parent()?.parent().map(Path::to_path_buf))
        .unwrap_or_else(|| PathBuf::from(".")) .join("app-update/practice-charts")
}

pub async fn catalog(songs: Arc<SongCatalog>, headers: HeaderMap) -> Response {
    match tokio::task::spawn_blocking(move || {
        let mut cache = CACHE.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        scan(&directory(), &songs, &mut cache)
    }).await {
        Ok(Ok(charts)) => catalog_response(charts, &headers),
        _ => (StatusCode::SERVICE_UNAVAILABLE, [(header::CACHE_CONTROL, "no-store")],
              Json(serde_json::json!({"message": "Practice catalog temporarily unavailable"}))).into_response(),
    }
}

fn catalog_response(charts: Vec<PracticeChart>, headers: &HeaderMap) -> Response {
    let body = serde_json::json!({"practiceCharts": charts});
    let etag = format!("\"{}\"", hex::encode(Sha256::digest(serde_json::to_vec(&body).expect("catalog serializes"))));
    let response_headers = [(header::CACHE_CONTROL, "no-cache".to_owned()), (header::ETAG, etag.clone())];
    if headers.get(header::IF_NONE_MATCH).and_then(|h| h.to_str().ok())
        .is_some_and(|h| h.split(',').any(|v| v.trim().trim_start_matches("W/") == etag || v.trim() == "*")) {
        return (StatusCode::NOT_MODIFIED, response_headers).into_response();
    }
    (response_headers, Json(body)).into_response()
}

fn read_meta(path: &Path) -> Option<Value> {
    let mut zip = zip::ZipArchive::new(fs::File::open(path).ok()?).ok()?;
    let root: Value = {
        let entry = zip.by_name("chart.json").ok()?;
        if entry.size() > 32 * 1024 * 1024 { return None; }
        let mut bytes = Vec::new();
        entry.take(32 * 1024 * 1024 + 1).read_to_end(&mut bytes).ok()?;
        serde_json::from_slice(&bytes).ok()?
    };
    if !root["judgeLineList"].is_array() || !root["BPMList"].is_array() { return None; }
    let meta = root.get("META")?;
    for field in ["song", "background"] {
        if zip.by_name(meta[field].as_str()?).ok()?.size() == 0 { return None; }
    }
    Some(meta.clone())
}

fn identify(meta: &Value, name: &str, songs: &SongCatalog) -> Option<PracticeChart> {
    let explicit = meta["songId"].as_str().and_then(|id| songs.by_id.get(id));
    let song = if let Some(song) = explicit { song } else {
        let title = normalize_song_search_text(meta["name"].as_str()?);
        let mut matches = songs.by_id.values().filter(|song| normalize_song_search_text(&song.name) == title);
        let song = matches.next()?;
        if matches.next().is_some() { return None; }
        song
    };
    let level = meta["level"].as_str()?.split_whitespace().next()?.to_ascii_uppercase();
    let constant = match level.as_str() {
        "EZ" => song.chart_constants.ez, "HD" => song.chart_constants.hd,
        "IN" => song.chart_constants.in_level, "AT" => song.chart_constants.at, _ => return None,
    }?;
    Some(PracticeChart { song_id: song.id.clone(), title: song.name.clone(),
        difficulty: format!("{level} Lv.{constant:.1}"), chart_constant: constant, file_name: name.into(),
        size_bytes: 0, revision: String::new() })
}

fn scan(root: &Path, songs: &SongCatalog, cache: &mut Cache) -> std::io::Result<Vec<PracticeChart>> {
    let entries = match fs::read_dir(root) {
        Ok(entries) => entries,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => { cache.files.clear(); return Ok(vec![]); }
        Err(error) => return Err(error),
    };
    let mut seen = std::collections::HashSet::new();
    let mut result = Vec::new();
    for entry in entries {
        let entry = entry?;
        if !entry.file_type()?.is_file() { continue; }
        let path = entry.path();
        let Some(name) = path.file_name().and_then(|n| n.to_str()) else { continue; };
        if !name.to_ascii_lowercase().ends_with(".pez") || name.len() > 240 || name.chars().any(|c| c.is_control() || "/\\:".contains(c)) { continue; }
        let info = entry.metadata()?;
        let stamp = (info.len(), info.modified()?);
        seen.insert(path.clone());
        let item = cache.files.entry(path.clone()).or_insert((0, SystemTime::UNIX_EPOCH, None));
        if (item.0, item.1) != stamp { *item = (stamp.0, stamp.1, read_meta(&path)); }
        if let Some(mut chart) = item.2.as_ref().and_then(|meta| identify(meta, name, songs)) {
            chart.size_bytes = stamp.0;
            chart.revision = format!("{}:{}", stamp.0, stamp.1.duration_since(SystemTime::UNIX_EPOCH).unwrap_or_default().as_nanos());
            result.push(chart);
        }
    }
    cache.files.retain(|path, _| seen.contains(path));
    let rank = |s: &str| match s.split_whitespace().next().unwrap_or("") { "EZ" => 0, "HD" => 1, "IN" => 2, "AT" => 3, _ => 4 };
    result.sort_by(|a, b| a.song_id.cmp(&b.song_id).then(rank(&a.difficulty).cmp(&rank(&b.difficulty))).then(a.file_name.cmp(&b.file_name)));
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use crate::{features::song::models::SongInfo, startup::chart_loader::ChartConstants};
    struct Fixture(PathBuf);
    impl Drop for Fixture { fn drop(&mut self) { let _ = fs::remove_dir_all(&self.0); } }
    fn fixture() -> (Fixture, SongCatalog) {
        let root = std::env::temp_dir().join(format!("psq-practice-{}", uuid::Uuid::new_v4()));
        fs::create_dir(&root).unwrap();
        let mut songs = SongCatalog::default();
        songs.by_id.insert("Test.id".into(), Arc::new(SongInfo { id: "Test.id".into(), name: "Test Song".into(),
            composer: "Test".into(), illustrator: "Test".into(), chart_constants: ChartConstants { ez: None, hd: None, in_level: Some(16.1), at: Some(17.6) } }));
        (Fixture(root), songs)
    }
    fn write_chart(path: &Path, level: &str) {
        let mut zip = zip::ZipWriter::new(fs::File::create(path).unwrap());
        let json = serde_json::json!({"META":{"name":"Test Song", "level":level, "song":"music.mp3", "background":"ill.jpg"},"BPMList":[],"judgeLineList":[]}).to_string();
        for (name, bytes) in [("chart.json", json.as_bytes()), ("music.mp3", b"music"), ("ill.jpg", b"image")] {
            zip.start_file(name, zip::write::SimpleFileOptions::default()).unwrap(); zip.write_all(bytes).unwrap();
        }
        zip.finish().unwrap();
    }
    #[test] fn files_control_visibility_and_in_precedes_at() {
        let (root, songs) = fixture(); let mut cache = Cache::default();
        write_chart(&root.0.join("a.pez"), "AT Lv.17.6");
        write_chart(&root.0.join("z.pez"), "IN Lv.16.1");
        let charts = scan(&root.0, &songs, &mut cache).unwrap();
        assert_eq!(charts.len(), 2); assert!(charts[0].difficulty.starts_with("IN"));
        fs::remove_file(root.0.join("z.pez")).unwrap();
        assert_eq!(scan(&root.0, &songs, &mut cache).unwrap().len(), 1);
        fs::rename(root.0.join("a.pez"), root.0.join("a.pez.disabled")).unwrap();
        assert!(scan(&root.0, &songs, &mut cache).unwrap().is_empty());
        assert!(cache.files.is_empty());
    }
    #[test] fn conditional_catalog_tracks_resource_replacement() {
        let (root, songs) = fixture(); let mut cache = Cache::default();
        let path = root.0.join("a.pez"); write_chart(&path, "IN");
        let charts = scan(&root.0, &songs, &mut cache).unwrap();
        assert_eq!(charts[0].size_bytes, fs::metadata(&path).unwrap().len());
        assert!(!charts[0].revision.is_empty());
        let response = catalog_response(charts, &HeaderMap::new());
        let etag = response.headers()[header::ETAG].clone();
        let mut headers = HeaderMap::new(); headers.insert(header::IF_NONE_MATCH, etag.clone());
        assert_eq!(catalog_response(scan(&root.0, &songs, &mut cache).unwrap(), &headers).status(), StatusCode::NOT_MODIFIED);
        write_chart(&path, "AT Lv.17.6");
        let updated = catalog_response(scan(&root.0, &songs, &mut cache).unwrap(), &headers);
        assert_eq!(updated.status(), StatusCode::OK);
        assert_ne!(updated.headers()[header::ETAG], etag);
    }
    #[test] fn incomplete_unknown_and_changed_archives_are_not_stale() {
        let (root, songs) = fixture(); let mut cache = Cache::default();
        let path = root.0.join("chart.pez"); write_chart(&path, "IN");
        assert_eq!(scan(&root.0, &songs, &mut cache).unwrap().len(), 1);
        fs::write(&path, b"partial upload").unwrap();
        assert!(scan(&root.0, &songs, &mut cache).unwrap().is_empty());
        write_chart(&path, "AT");
        assert!(scan(&root.0, &songs, &mut cache).unwrap()[0].difficulty.starts_with("AT"));
        assert!(scan(&root.0, &SongCatalog::default(), &mut cache).unwrap().is_empty());
    }
}
