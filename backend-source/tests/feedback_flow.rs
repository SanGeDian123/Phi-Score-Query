use std::sync::{Arc, Once};

use axum::{
    Router,
    body::Bytes,
    http::{Request, StatusCode},
};
use moka::future::Cache;
use tokio::sync::Semaphore;
use tower::ServiceExt;
use uuid::Uuid;

use phi_backend::{
    config::{AppConfig, TapTapConfig, TapTapMultiConfig, TapTapVersion},
    features::{
        auth::{client::TapTapClient, handler::create_auth_router},
        feedback,
        song::models::SongCatalog,
        stats::storage::StatsStorage,
    },
    state::AppState,
};

fn init_test_config() {
    static INIT: Once = Once::new();
    INIT.call_once(|| {
        unsafe {
            std::env::set_var("APP_STATS_USER_HASH_SALT", "test-user-hash-salt");
            std::env::set_var("APP_LEADERBOARD_ADMIN_TOKENS", "feedback-test-admin");
            std::env::set_var("APP_SESSION_JWT_SECRET", "test-jwt-secret");
            std::env::set_var("APP_SESSION_EXCHANGE_SHARED_SECRET", "test-exchange-secret");
            std::env::set_var(
                "APP_SESSION_AUTH_EMBED_SECRET",
                "test-embed-secret-1234567890",
            );
        }
        let _ = AppConfig::init_global();
    });
}

fn dummy_taptap_config(app_id: &str, app_key: &str) -> TapTapConfig {
    TapTapConfig {
        device_code_endpoint: "http://example.invalid/device/code".to_string(),
        token_endpoint: "http://example.invalid/token".to_string(),
        user_info_endpoint: "http://example.invalid/userinfo".to_string(),
        leancloud_base_url: "http://example.invalid/leancloud".to_string(),
        leancloud_app_id: app_id.to_string(),
        leancloud_app_key: app_key.to_string(),
    }
}

fn make_state(storage: Arc<StatsStorage>) -> AppState {
    let taptap_cfg = TapTapMultiConfig {
        cn: dummy_taptap_config("cn-app-id", "cn-app-key"),
        global: dummy_taptap_config("global-app-id", "global-app-key"),
        default_version: TapTapVersion::CN,
    };
    let taptap_client = Arc::new(TapTapClient::new(&taptap_cfg).expect("TapTapClient::new"));

    let bn_image_cache: Cache<String, Bytes> = Cache::builder().max_capacity(16).build();
    let song_image_cache: Cache<String, Bytes> = Cache::builder().max_capacity(16).build();

    AppState {
        chart_constants: phi_backend::state::Reloadable::new(std::collections::HashMap::default()),
        song_catalog: phi_backend::state::Reloadable::new(SongCatalog::default()),
        taptap_client,
        qrcode_service: Arc::new(
            phi_backend::features::auth::qrcode_service::QrCodeService::default(),
        ),
        stats: None,
        stats_storage: Some(storage),
        render_semaphore: Arc::new(Semaphore::new(1)),
        bn_image_cache,
        song_image_cache,
    }
}

fn make_app(state: AppState) -> Router {
    let api_router = Router::<AppState>::new()
        .nest("/auth", create_auth_router())
        .merge(phi_backend::features::rks_guess::create_rks_guess_router())
        .merge(feedback::router());

    let api_router = api_router.layer(axum::middleware::from_fn_with_state(
        state.clone(),
        phi_backend::features::auth::bearer::bearer_auth_middleware,
    ));

    Router::<AppState>::new()
        .nest("/api/v2", api_router)
        .with_state(state)
}

async fn request(
    app: &Router,
    method: &str,
    path: &str,
    token: &str,
    admin: bool,
    body: serde_json::Value,
) -> (StatusCode, Vec<u8>) {
    let req = Request::builder()
        .method(method)
        .uri(format!("/api/v2{path}"))
        .header("content-type", "application/json")
        .header(
            if admin {
                "x-admin-token"
            } else {
                "authorization"
            },
            if admin {
                token.to_string()
            } else {
                format!("Bearer {token}")
            },
        )
        .header("x-exchange-secret", "test-exchange-secret")
        .body(axum::body::Body::from(body.to_string()))
        .unwrap();
    let response = app.clone().oneshot(req).await.unwrap();
    (
        response.status(),
        axum::body::to_bytes(response.into_body(), 6 * 1024 * 1024)
            .await
            .unwrap()
            .to_vec(),
    )
}
async fn exchange(app: &Router, user: &str) -> String {
    let (status, bytes) = request(
        app,
        "POST",
        "/auth/session/exchange",
        "",
        false,
        serde_json::json!({"sessionToken":user}),
    )
    .await;
    assert_eq!(
        status,
        StatusCode::OK,
        "{}",
        String::from_utf8_lossy(&bytes)
    );
    serde_json::from_slice::<serde_json::Value>(&bytes).unwrap()["accessToken"]
        .as_str()
        .unwrap()
        .into()
}

#[tokio::test]
async fn win_leaderboard_requires_auth_and_returns_persisted_totals() {
    init_test_config();
    let storage = Arc::new(StatsStorage {
        pool: sqlx::sqlite::SqlitePoolOptions::new()
            .max_connections(1)
            .connect("sqlite::memory:")
            .await
            .unwrap(),
    });
    storage.init_schema().await.unwrap();
    storage
        .record_rks_guess_win(
            "test-game",
            "private-player",
            "single",
            "2026-09-12T00:00:00Z",
        )
        .await
        .unwrap();
    let app = make_app(make_state(storage));
    let (status, _) = request(
        &app,
        "GET",
        "/games/rks-guess/leaderboard",
        "",
        false,
        serde_json::Value::Null,
    )
    .await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
    let token = exchange(&app, "r:win-board-reader").await;
    let (status, bytes) = request(
        &app,
        "GET",
        "/games/rks-guess/leaderboard",
        &token,
        false,
        serde_json::Value::Null,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    let result: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(result["items"][0]["totalWins"], 1);
    assert_eq!(result["items"][0]["nickname"], "匿名玩家");
    assert_eq!(result["myWins"], 0);
    assert!(!String::from_utf8_lossy(&bytes).contains("private-player"));
}

#[tokio::test]
async fn admin_save_wakes_waiting_owner_without_waiting_for_poll_timeout() {
    use serde_json::json;
    init_test_config();
    let storage = StatsStorage {
        pool: sqlx::sqlite::SqlitePoolOptions::new()
            .max_connections(1)
            .connect("sqlite::memory:")
            .await
            .unwrap(),
    };
    storage.init_schema().await.unwrap();
    let app = make_app(make_state(Arc::new(storage)));
    let alice = exchange(&app, "r:feedback-live-alice").await;
    let bob = exchange(&app, "r:feedback-live-bob").await;
    let id = Uuid::new_v4().to_string();
    assert_eq!(
        request(
            &app,
            "POST",
            "/feedback",
            &alice,
            false,
            json!({"id":id,"title":"实时通知","body":"状态更新"})
        )
        .await
        .0,
        StatusCode::OK
    );
    let (status, initial) =
        request(&app, "GET", "/feedback/updates", &alice, false, json!(null)).await;
    assert_eq!(status, StatusCode::OK);
    let initial: serde_json::Value = serde_json::from_slice(&initial).unwrap();
    assert_eq!(initial["items"], json!([]));
    let cursor = initial["cursor"].as_str().unwrap();
    let wait_path = format!("/feedback/updates?cursor={cursor}");
    let waiting = request(&app, "GET", &wait_path, &alice, false, json!(null));
    let saving = async {
        tokio::time::sleep(std::time::Duration::from_millis(30)).await;
        assert_eq!(
            request(
                &app,
                "POST",
                &format!("/admin/feedback/{id}"),
                "feedback-test-admin",
                true,
                json!({"status":"processing","reply":"正在处理","revision":0})
            )
            .await
            .0,
            StatusCode::OK
        );
    };
    let ((status, body), _) = tokio::time::timeout(std::time::Duration::from_secs(2), async {
        tokio::join!(waiting, saving)
    })
    .await
    .expect("save must wake the pending request immediately");
    assert_eq!(status, StatusCode::OK);
    let response: serde_json::Value = serde_json::from_slice(&body).unwrap();
    assert_ne!(response["cursor"], initial["cursor"]);
    assert_eq!(response["items"][0]["revision"], 1);
    assert_eq!(response["items"][0]["status"], "processing");
    let (_, other) = request(&app, "GET", "/feedback/updates", &bob, false, json!(null)).await;
    assert_eq!(
        serde_json::from_slice::<serde_json::Value>(&other).unwrap()["items"],
        json!([])
    );
    assert_eq!(
        request(&app, "GET", "/feedback/updates", "", false, json!(null))
            .await
            .0,
        StatusCode::UNAUTHORIZED
    );
}

#[tokio::test]
async fn private_feedback_end_to_end() {
    use base64::Engine;
    use serde_json::json;
    init_test_config();
    let storage = StatsStorage {
        pool: sqlx::sqlite::SqlitePoolOptions::new()
            .max_connections(1)
            .connect("sqlite::memory:")
            .await
            .unwrap(),
    };
    storage.init_schema().await.unwrap();
    let app = make_app(make_state(Arc::new(storage)));
    let alice = exchange(&app, "r:feedback-alice").await;
    let bob = exchange(&app, "r:feedback-bob").await;
    let id = Uuid::new_v4().to_string();
    let mut png = std::io::Cursor::new(Vec::new());
    image::DynamicImage::new_rgb8(2, 2)
        .write_to(&mut png, image::ImageFormat::Png)
        .unwrap();
    let draft = json!({"id":id,"title":"图片显示异常","body":"请检查图片显示","images":[base64::engine::general_purpose::STANDARD.encode(png.get_ref())]});
    assert_eq!(
        request(&app, "POST", "/feedback", "", false, draft.clone())
            .await
            .0,
        StatusCode::UNAUTHORIZED
    );
    let (status, created) = request(&app, "POST", "/feedback", &alice, false, draft.clone()).await;
    assert_eq!(
        status,
        StatusCode::OK,
        "{}",
        String::from_utf8_lossy(&created)
    );
    assert_eq!(
        request(&app, "POST", "/feedback", &alice, false, draft)
            .await
            .1,
        created,
        "retry must return the same feedback"
    );
    let (_, mine) = request(&app, "GET", "/feedback/mine", &alice, false, json!(null)).await;
    assert_eq!(
        serde_json::from_slice::<Vec<serde_json::Value>>(&mine)
            .unwrap()
            .len(),
        1
    );
    assert_eq!(
        request(&app, "GET", "/feedback/mine", &bob, false, json!(null))
            .await
            .1,
        b"[]"
    );
    assert!(
        !request(
            &app,
            "GET",
            &format!("/feedback/{id}/images/0"),
            &bob,
            false,
            json!(null)
        )
        .await
        .0
        .is_success()
    );
    assert_eq!(
        request(
            &app,
            "GET",
            &format!("/feedback/{id}/images/0"),
            &alice,
            false,
            json!(null)
        )
        .await
        .1,
        *png.get_ref()
    );
    assert_eq!(
        request(&app, "GET", "/admin/feedback", &alice, false, json!(null))
            .await
            .0,
        StatusCode::UNAUTHORIZED
    );
    assert_eq!(
        request(
            &app,
            "GET",
            "/admin/feedback",
            "feedback-test-admin",
            true,
            json!(null)
        )
        .await
        .0,
        StatusCode::OK
    );
    let path = format!("/admin/feedback/{id}");
    assert!(
        !request(
            &app,
            "POST",
            &path,
            "feedback-test-admin",
            true,
            json!({"status":"resolved","reply":"","revision":0})
        )
        .await
        .0
        .is_success()
    );
    let update = json!({"status":"resolved","reply":"已修复，请更新后查看","revision":0});
    assert_eq!(
        request(
            &app,
            "POST",
            &path,
            "feedback-test-admin",
            true,
            update.clone()
        )
        .await
        .0,
        StatusCode::OK
    );
    assert_eq!(
        request(&app, "POST", &path, "feedback-test-admin", true, update)
            .await
            .0,
        StatusCode::CONFLICT
    );
    let (_, unread) = request(
        &app,
        "GET",
        "/feedback/notifications",
        &alice,
        false,
        json!(null),
    )
    .await;
    let unread: Vec<serde_json::Value> = serde_json::from_slice(&unread).unwrap();
    assert_eq!(unread.len(), 1);
    assert_eq!(unread[0]["revision"], 1);
    assert_eq!(unread[0]["status"], "resolved");
    let read_path = format!("/feedback/{id}/read");
    assert_eq!(
        request(&app, "POST", &read_path, &bob, false, json!({"revision":1}))
            .await
            .1,
        b"false"
    );
    assert_eq!(
        request(
            &app,
            "POST",
            &read_path,
            &alice,
            false,
            json!({"revision":1})
        )
        .await
        .1,
        b"true"
    );
    assert_eq!(
        request(
            &app,
            "GET",
            "/feedback/notifications",
            &alice,
            false,
            json!(null)
        )
        .await
        .1,
        b"[]"
    );
    // Reopening a feedback item is another user-visible update.
    assert_eq!(
        request(
            &app,
            "POST",
            &path,
            "feedback-test-admin",
            true,
            json!({"status":"processing","reply":"正在继续排查","revision":1})
        )
        .await
        .0,
        StatusCode::OK
    );
    request(
        &app,
        "POST",
        &read_path,
        &alice,
        false,
        json!({"revision":1}),
    )
    .await;
    assert_ne!(
        request(
            &app,
            "GET",
            "/feedback/notifications",
            &alice,
            false,
            json!(null)
        )
        .await
        .1,
        b"[]"
    );
    for _ in 0..9 {
        assert_eq!(
            request(
                &app,
                "POST",
                "/feedback",
                &alice,
                false,
                json!({"id":Uuid::new_v4().to_string(),"title":"建议","body":"正文"})
            )
            .await
            .0,
            StatusCode::OK
        );
    }
    assert!(
        !request(
            &app,
            "POST",
            "/feedback",
            &alice,
            false,
            json!({"id":Uuid::new_v4().to_string(),"title":"建议","body":"正文"})
        )
        .await
        .0
        .is_success()
    );
    assert!(!request(&app,"POST","/feedback",&bob,false,json!({"id":Uuid::new_v4().to_string(),"title":"图片","body":"正文","images":["broken"]})).await.0.is_success());
}
