use std::fs;
use std::path::PathBuf;
use std::sync::{Arc, OnceLock};

use resvg::usvg::fontdb;

const FONTS_DIR: &str = "resources/fonts";
pub(super) const PHI_PLUGIN_FONT_NAME: &str = "PhiPluginPHI";

// 全局字体数据库单例
static GLOBAL_FONT_DB: OnceLock<Arc<fontdb::Database>> = OnceLock::new();

/// 初始化全局字体数据库
fn init_global_font_db() -> Arc<fontdb::Database> {
    let mut font_db = fontdb::Database::new();
    font_db.load_system_fonts();

    // 加载自定义字体
    let fonts_dir = PathBuf::from(FONTS_DIR);
    if fonts_dir.exists()
        && let Ok(entries) = fs::read_dir(&fonts_dir)
    {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_file()
                && (path.extension() == Some("ttf".as_ref())
                    || path.extension() == Some("otf".as_ref()))
                && let Err(e) = font_db.load_font_file(&path)
            {
                tracing::error!("加载字体文件失败 '{}': {}", path.display(), e);
            }
        }
    }

    register_phi_plugin_font(&mut font_db, &fonts_dir);
    Arc::new(font_db)
}

fn register_phi_plugin_font(font_db: &mut fontdb::Database, fonts_dir: &std::path::Path) {
    // PHI is an @font-face alias in upstream CSS. Give the bundled, identical
    // font a unique name so installed fonts with the same family cannot win.
    let bundled_face = font_db
        .faces()
        .find(|face| {
            matches!(&face.source, fontdb::Source::File(path) if path.parent() == Some(fonts_dir))
                && face
                    .families
                    .iter()
                    .any(|(name, _)| name == super::MAIN_FONT_NAME)
        })
        .cloned();
    if let Some(mut face) = bundled_face {
        let language = face.families[0].1;
        face.families = vec![(PHI_PLUGIN_FONT_NAME.to_string(), language)];
        font_db.push_face_info(face);
    }
}

/// 获取全局字体数据库
pub(super) fn get_global_font_db() -> Arc<fontdb::Database> {
    GLOBAL_FONT_DB.get_or_init(init_global_font_db).clone()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn phi_alias_resolves_to_the_bundled_upstream_font() {
        let mut db = fontdb::Database::new();
        let fonts_dir = PathBuf::from(FONTS_DIR);
        db.load_fonts_dir(&fonts_dir);
        register_phi_plugin_font(&mut db, &fonts_dir);
        let id = db
            .query(&fontdb::Query {
                families: &[fontdb::Family::Name(PHI_PLUGIN_FONT_NAME)],
                ..Default::default()
            })
            .expect("upstream PHI font alias must be registered");
        assert!(matches!(&db.face(id).unwrap().source,
            fontdb::Source::File(path) if path.parent() == Some(fonts_dir.as_path())));
    }
}
