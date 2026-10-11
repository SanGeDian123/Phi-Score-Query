use std::{collections::BTreeMap, fs, path::PathBuf, sync::Arc};

use axum::{
    Router,
    body::{Body, Bytes},
    http::{Request, StatusCode},
    response::IntoResponse,
};
use moka::future::Cache;
use serde_json::{Value, json};
use tokio::sync::Semaphore;
use tower::ServiceExt;

use phi_backend::{
    config::{AppConfig, TapTapMultiConfig},
    features::{
        auth::{client::TapTapClient, qrcode_service::QrCodeService},
        song::{
            aliases::{UpdateSongAliasesRequest, describe_song, update_song_aliases},
            models::SongCatalog,
        },
    },
    startup::song_loader::load_song_catalog,
    state::{AppState, Reloadable},
};

const PRIMARY: &str = "LunarHorizon.Test";
const SECONDARY: &str = "ZincOdyssey.Test";
const OLD_ALIAS: &str = "ancient-galaxy";
const SHARED_ALIAS: &str = "shared-asteroid";
const NEW_ALIAS: &str = "rainbow-cylinder";
const ADMIN_TOKEN: &str = "alias-management-test-token";
const OVERRIDES: &str = "song-alias-overrides.json";

struct InfoFixture {
    path: PathBuf,
}

impl InfoFixture {
    fn new() -> Self {
        let path = std::env::temp_dir().join(format!(
            "phi-song-alias-management-{}",
            uuid::Uuid::new_v4()
        ));
        fs::create_dir(&path).expect("create isolated fixture directory");
        fs::write(
            path.join("info.csv"),
            "id,song,composer,illustrator,EZ,HD,IN,AT\n\
             LunarHorizon.Test,Lunar Horizon,Test Composer,Test Illustrator,1,5,10,\n\
             ZincOdyssey.Test,Zinc Odyssey,Other Composer,Other Illustrator,2,6,11,\n",
        )
        .expect("write song fixture");
        fs::write(
            path.join("nicklist.yaml"),
            format!(
                "{PRIMARY}:\n  - {OLD_ALIAS}\n  - {SHARED_ALIAS}\n\
                 {SECONDARY}:\n  - {SHARED_ALIAS}\n  - untouched-obelisk\n"
            ),
        )
        .expect("write nickname fixture");
        Self { path }
    }

    fn catalog(&self) -> SongCatalog {
        load_song_catalog(&self.path).expect("load fixture catalog")
    }

    fn overrides(&self) -> BTreeMap<String, Vec<String>> {
        serde_json::from_slice(&fs::read(self.path.join(OVERRIDES)).expect("read saved aliases"))
            .expect("saved alias overrides are valid JSON")
    }
}

impl Drop for InfoFixture {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.path);
    }
}

fn revision(target: &Reloadable<SongCatalog>, song_id: &str) -> String {
    let catalog = target.snapshot();
    describe_song(&catalog, &catalog.by_id[song_id]).revision
}

fn update(song_id: &str, aliases: Vec<String>, revision: String) -> UpdateSongAliasesRequest {
    UpdateSongAliasesRequest {
        song_id: song_id.to_owned(),
        aliases,
        revision,
    }
}

fn search_ids(catalog: &SongCatalog, query: &str) -> Vec<String> {
    catalog
        .search(query)
        .iter()
        .map(|song| song.id.clone())
        .collect()
}

fn new_test_state(catalog: SongCatalog) -> AppState {
    let mut taptap = TapTapMultiConfig::default();
    for config in [&mut taptap.cn, &mut taptap.global] {
        config.device_code_endpoint = "http://example.invalid/device/code".into();
        config.token_endpoint = "http://example.invalid/token".into();
        config.user_info_endpoint = "http://example.invalid/userinfo".into();
        config.leancloud_base_url = "http://example.invalid/leancloud".into();
        config.leancloud_app_id = "test-app-id".into();
        config.leancloud_app_key = "test-app-key".into();
    }
    AppState {
        chart_constants: Reloadable::new(std::collections::HashMap::new()),
        song_catalog: Reloadable::new(catalog),
        taptap_client: Arc::new(TapTapClient::new(&taptap).expect("create test TapTap client")),
        qrcode_service: Arc::new(QrCodeService::default()),
        stats: None,
        stats_storage: None,
        render_semaphore: Arc::new(Semaphore::new(1)),
        bn_image_cache: Cache::<String, Bytes>::builder().max_capacity(1024).build(),
        song_image_cache: Cache::<String, Bytes>::builder().max_capacity(1024).build(),
    }
}

fn build_app(fixture: &InfoFixture, state: AppState) -> Router {
    // Read only the checked-in template; do not alter environment variables or global config.
    let mut config: AppConfig = config::Config::builder()
        .add_source(config::File::from_str(
            include_str!("../config.example.toml"),
            config::FileFormat::Toml,
        ))
        .build()
        .expect("parse isolated test configuration")
        .try_deserialize()
        .expect("deserialize isolated test configuration");
    config.resources.info_path = fixture.path.to_string_lossy().into_owned();
    config.leaderboard.admin_tokens = vec![ADMIN_TOKEN.into()];
    Router::<AppState>::new()
        .nest(
            "/api/v2",
            phi_backend::features::song::aliases::router(config),
        )
        .with_state(state)
}

fn put_request(token: Option<&str>, aliases: Value, revision: &str) -> Request<Body> {
    let mut builder = Request::builder()
        .method("PUT")
        .uri("/api/v2/admin/songs/aliases")
        .header("content-type", "application/json");
    if let Some(token) = token {
        builder = builder.header("X-Admin-Token", token);
    }
    builder
        .body(Body::from(
            json!({ "songId": PRIMARY, "aliases": aliases, "revision": revision }).to_string(),
        ))
        .expect("build PUT request")
}

#[tokio::test]
async fn admin_alias_routes_require_token_and_publish_saved_aliases_immediately() {
    let fixture = InfoFixture::new();
    let state = new_test_state(fixture.catalog());
    let target = state.song_catalog.clone();
    let initial_revision = revision(&target, PRIMARY);
    let app = build_app(&fixture, state);

    for token in [None, Some("incorrect-token")] {
        let mut get = Request::builder().uri("/api/v2/admin/songs/aliases");
        if let Some(token) = token {
            get = get.header("X-Admin-Token", token);
        }
        let response = app
            .clone()
            .oneshot(get.body(Body::empty()).unwrap())
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
        let response = app
            .clone()
            .oneshot(put_request(token, json!([NEW_ALIAS]), &initial_revision))
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }
    assert!(!fixture.path.join(OVERRIDES).exists());
    assert_eq!(revision(&target, PRIMARY), initial_revision);

    let response = app
        .clone()
        .oneshot(
            Request::builder()
                .uri("/api/v2/admin/songs/aliases")
                .header("X-Admin-Token", ADMIN_TOKEN)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    assert_eq!(response.headers()["cache-control"], "no-store");
    let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
        .await
        .unwrap();
    let body: Value = serde_json::from_slice(&bytes).unwrap();
    let items = body["items"].as_array().expect("admin alias items");
    assert_eq!(items.len(), 2);
    let primary = items.iter().find(|item| item["songId"] == PRIMARY).unwrap();
    assert_eq!(primary["revision"], initial_revision);
    assert_eq!(primary["aliases"], json!([OLD_ALIAS, SHARED_ALIAS]));

    let nicklist_before = fs::read(fixture.path.join("nicklist.yaml")).unwrap();
    let response = app
        .oneshot(put_request(
            Some(ADMIN_TOKEN),
            json!([format!("  {NEW_ALIAS}  ")]),
            &initial_revision,
        ))
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    let bytes = axum::body::to_bytes(response.into_body(), 1024 * 1024)
        .await
        .unwrap();
    let saved: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(saved["songId"], PRIMARY);
    assert_eq!(saved["aliases"], json!([NEW_ALIAS]));
    assert_ne!(saved["revision"], initial_revision);

    let catalog = target.snapshot();
    assert_eq!(search_ids(&catalog, NEW_ALIAS), vec![PRIMARY]);
    assert!(search_ids(&catalog, OLD_ALIAS).is_empty());
    assert_eq!(search_ids(&catalog, SHARED_ALIAS), vec![SECONDARY]);
    assert_eq!(search_ids(&catalog, "untouched-obelisk"), vec![SECONDARY]);
    assert_eq!(
        fixture.overrides().get(PRIMARY),
        Some(&vec![NEW_ALIAS.to_owned()])
    );
    assert!(!fixture.overrides().contains_key(SECONDARY));
    assert_eq!(
        fs::read(fixture.path.join("nicklist.yaml")).unwrap(),
        nicklist_before
    );

    let restarted = fixture.catalog();
    assert_eq!(search_ids(&restarted, NEW_ALIAS), vec![PRIMARY]);
    assert!(search_ids(&restarted, OLD_ALIAS).is_empty());
    assert_eq!(search_ids(&restarted, SHARED_ALIAS), vec![SECONDARY]);
    assert_eq!(
        describe_song(&restarted, &restarted.by_id[PRIMARY]).revision,
        saved["revision"].as_str().unwrap()
    );
}

#[tokio::test]
async fn stale_revision_returns_conflict_without_changing_disk_or_catalog() {
    let fixture = InfoFixture::new();
    let state = new_test_state(fixture.catalog());
    let target = state.song_catalog.clone();
    let stale_revision = revision(&target, PRIMARY);
    update_song_aliases(
        &fixture.path,
        &target,
        update(PRIMARY, vec![NEW_ALIAS.into()], stale_revision.clone()),
    )
    .await
    .expect("initial save");
    let saved_bytes = fs::read(fixture.path.join(OVERRIDES)).unwrap();
    let saved_revision = revision(&target, PRIMARY);
    let response = build_app(&fixture, state)
        .oneshot(put_request(
            Some(ADMIN_TOKEN),
            json!(["obsolete-request"]),
            &stale_revision,
        ))
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::CONFLICT);
    assert_eq!(fs::read(fixture.path.join(OVERRIDES)).unwrap(), saved_bytes);
    assert_eq!(revision(&target, PRIMARY), saved_revision);
    assert_eq!(search_ids(&target.snapshot(), NEW_ALIAS), vec![PRIMARY]);
}

#[tokio::test]
async fn empty_alias_list_persists_removal_even_when_upstream_nicklist_changes() {
    let fixture = InfoFixture::new();
    let target = Reloadable::new(fixture.catalog());
    let saved = update_song_aliases(
        &fixture.path,
        &target,
        update(PRIMARY, vec![], revision(&target, PRIMARY)),
    )
    .await
    .expect("clear aliases");
    assert!(saved.aliases.is_empty());
    assert_eq!(fixture.overrides().get(PRIMARY), Some(&vec![]));
    assert!(search_ids(&target.snapshot(), OLD_ALIAS).is_empty());
    assert_eq!(
        search_ids(&target.snapshot(), SHARED_ALIAS),
        vec![SECONDARY]
    );

    fs::write(
        fixture.path.join("nicklist.yaml"),
        format!(
            "{PRIMARY}:\n  - {OLD_ALIAS}\n  - upstream-constellation\n\
         {SECONDARY}:\n  - {SHARED_ALIAS}\n"
        ),
    )
    .unwrap();
    let reloaded = fixture.catalog();
    assert!(
        describe_song(&reloaded, &reloaded.by_id[PRIMARY])
            .aliases
            .is_empty()
    );
    assert!(search_ids(&reloaded, "upstream-constellation").is_empty());
    assert_eq!(search_ids(&reloaded, SHARED_ALIAS), vec![SECONDARY]);
}

#[tokio::test]
async fn invalid_aliases_are_rejected_without_any_disk_or_state_change() {
    let fixture = InfoFixture::new();
    let target = Reloadable::new(fixture.catalog());
    update_song_aliases(
        &fixture.path,
        &target,
        update(PRIMARY, vec![NEW_ALIAS.into()], revision(&target, PRIMARY)),
    )
    .await
    .expect("initial save");
    let saved_bytes = fs::read(fixture.path.join(OVERRIDES)).unwrap();
    let saved_revision = revision(&target, PRIMARY);
    let invalid_lists = vec![
        vec![String::new()],
        vec!["   ".into()],
        vec!["duplicate".into(), " duplicate ".into()],
        vec!["line\nbreak".into()],
        vec!["tab\tinside".into()],
        vec!["null\0inside".into()],
        vec!["长".repeat(129)],
        (0..201).map(|index| format!("alias-{index}")).collect(),
    ];
    for aliases in invalid_lists {
        let error = update_song_aliases(
            &fixture.path,
            &target,
            update(PRIMARY, aliases, saved_revision.clone()),
        )
        .await
        .expect_err("invalid aliases must not be accepted");
        assert_eq!(
            error.into_response().status(),
            StatusCode::UNPROCESSABLE_ENTITY
        );
        assert_eq!(fs::read(fixture.path.join(OVERRIDES)).unwrap(), saved_bytes);
        assert_eq!(revision(&target, PRIMARY), saved_revision);
    }
    assert_eq!(search_ids(&target.snapshot(), NEW_ALIAS), vec![PRIMARY]);
}

#[tokio::test]
async fn unknown_song_is_not_found_and_never_creates_an_override() {
    let fixture = InfoFixture::new();
    let target = Reloadable::new(fixture.catalog());
    let initial_revision = revision(&target, PRIMARY);
    let error = update_song_aliases(
        &fixture.path,
        &target,
        update(
            "unknown-song",
            vec![NEW_ALIAS.into()],
            initial_revision.clone(),
        ),
    )
    .await
    .expect_err("unknown song must be rejected");
    assert_eq!(error.into_response().status(), StatusCode::NOT_FOUND);
    assert!(!fixture.path.join(OVERRIDES).exists());
    assert_eq!(revision(&target, PRIMARY), initial_revision);
}

#[tokio::test]
async fn valid_limit_boundaries_and_shared_aliases_preserve_existing_search_rules() {
    let fixture = InfoFixture::new();
    let target = Reloadable::new(fixture.catalog());
    let full_length_alias = "长".repeat(128);
    let mut aliases = (0..196)
        .map(|index| format!("boundary-alias-{index}"))
        .collect::<Vec<_>>();
    aliases.extend([
        full_length_alias.clone(),
        "CaseAlias".into(),
        "casealias".into(),
        SHARED_ALIAS.into(),
    ]);
    let saved = update_song_aliases(
        &fixture.path,
        &target,
        update(PRIMARY, aliases, revision(&target, PRIMARY)),
    )
    .await
    .expect("200 aliases, 128 characters, and case variations are valid");
    assert_eq!(saved.aliases.len(), 200);
    assert!(saved.aliases.contains(&"CaseAlias".to_owned()));
    assert!(saved.aliases.contains(&"casealias".to_owned()));
    assert_eq!(
        search_ids(&target.snapshot(), &full_length_alias),
        vec![PRIMARY]
    );
    assert_eq!(
        search_ids(&target.snapshot(), SHARED_ALIAS),
        vec![PRIMARY, SECONDARY]
    );
    assert_eq!(fixture.overrides().get(PRIMARY), Some(&saved.aliases));
}

#[tokio::test]
async fn concurrent_updates_with_same_revision_allow_exactly_one_success() {
    let fixture = InfoFixture::new();
    let target = Reloadable::new(fixture.catalog());
    let initial_revision = revision(&target, PRIMARY);
    let (first, second) = tokio::join!(
        update_song_aliases(
            &fixture.path,
            &target,
            update(PRIMARY, vec![NEW_ALIAS.into()], initial_revision.clone())
        ),
        update_song_aliases(
            &fixture.path,
            &target,
            update(PRIMARY, vec!["crimson-parachute".into()], initial_revision)
        ),
    );
    let (accepted, rejected) = match (first, second) {
        (Ok(accepted), Err(rejected)) | (Err(rejected), Ok(accepted)) => (accepted, rejected),
        other => panic!("exactly one concurrent update must succeed: {other:?}"),
    };
    assert_eq!(rejected.into_response().status(), StatusCode::CONFLICT);
    assert_eq!(fixture.overrides().get(PRIMARY), Some(&accepted.aliases));
    let catalog = target.snapshot();
    assert_eq!(
        describe_song(&catalog, &catalog.by_id[PRIMARY]).revision,
        accepted.revision
    );
    assert_eq!(search_ids(&catalog, &accepted.aliases[0]), vec![PRIMARY]);
    assert_eq!(search_ids(&catalog, SHARED_ALIAS), vec![SECONDARY]);
    let reloaded = fixture.catalog();
    assert_eq!(
        describe_song(&reloaded, &reloaded.by_id[PRIMARY]).aliases,
        accepted.aliases
    );
}

#[test]
fn malformed_saved_override_rejects_reload_without_modifying_files() {
    let fixture = InfoFixture::new();
    let bytes = b"{invalid alias configuration";
    fs::write(fixture.path.join(OVERRIDES), bytes).unwrap();
    assert!(load_song_catalog(&fixture.path).is_err());
    assert_eq!(fs::read(fixture.path.join(OVERRIDES)).unwrap(), bytes);
}
