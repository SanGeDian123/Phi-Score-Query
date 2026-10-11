use phi_backend::features::stats::storage::StatsStorage;
use sqlx::{Row, sqlite::SqlitePoolOptions};

async fn storage() -> StatsStorage {
    let pool = SqlitePoolOptions::new().max_connections(1)
        .connect("sqlite::memory:").await.unwrap();
    let storage = StatsStorage { pool };
    storage.init_schema().await.unwrap();
    storage
}

#[tokio::test]
async fn leaderboard_follows_current_rks_including_decreases() {
    let storage = storage().await;
    for (second, current) in [16.1234, 16.0, 16.0, 16.2].into_iter().enumerate() {
        let now = format!("2026-10-07T00:00:{second:02}.000000000Z");
        storage.upsert_leaderboard_rks("u1", current, Some("session_token"), 0.0, false, &now)
            .await.unwrap();
        let (score, updated) = storage.get_prev_rks("u1").await.unwrap().unwrap();
        assert!((score - current).abs() < 1e-9);
        assert_eq!(updated, now);
    }
}

#[tokio::test]
async fn delayed_old_write_cannot_restore_stale_score_or_details() {
    let storage = storage().await;
    let newer = "2026-10-07T00:00:02.000000000Z";
    let older = "2026-10-07T00:00:01.000000000Z";
    storage.upsert_leaderboard_rks("u1", 16.0, None, 0.0, false, newer).await.unwrap();
    storage.upsert_details("u1", Some("new"), None, None, newer).await.unwrap();
    storage.upsert_leaderboard_rks("u1", 16.1234, None, 0.0, false, older).await.unwrap();
    storage.upsert_details("u1", Some("old"), None, None, older).await.unwrap();
    assert_eq!(storage.get_prev_rks("u1").await.unwrap().unwrap(), (16.0, newer.to_string()));
    let details: String = sqlx::query("SELECT rks_composition_json FROM leaderboard_details WHERE user_hash='u1'")
        .fetch_one(&storage.pool).await.unwrap().get(0);
    assert_eq!(details, "new");
}

#[tokio::test]
async fn current_rks_refresh_preserves_hidden_flag_and_original_registration() {
    let storage = storage().await;
    let first = "2026-10-07T00:00:01.000000000Z";
    let second = "2026-10-07T00:00:02.000000000Z";
    storage.upsert_leaderboard_rks("u1", 16.1, None, 1.0, true, first).await.unwrap();
    storage.upsert_leaderboard_rks("u1", 16.0, None, 0.0, false, second).await.unwrap();
    let row = sqlx::query("SELECT total_rks,is_hidden,created_at FROM leaderboard_rks WHERE user_hash='u1'")
        .fetch_one(&storage.pool).await.unwrap();
    assert_eq!(row.get::<f64, _>(0), 16.0);
    assert_eq!(row.get::<i64, _>(1), 1);
    assert_eq!(row.get::<String, _>(2), first);
}
