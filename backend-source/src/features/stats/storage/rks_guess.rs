use sqlx::Row;
use std::collections::HashSet;

use crate::error::AppError;

use super::{RksGuessSnapshot, StatsStorage};

const MAX_SNAPSHOT_CLUES: usize = 30;
const MIN_SINGLE_GAME_CLUES: usize = 10;
const MAX_SNAPSHOT_JSON_BYTES: usize = 256 * 1024;
const RANDOM_SAMPLE_LIMIT: i64 = 16;

impl StatsStorage {
    pub async fn upsert_rks_guess_snapshot(
        &self,
        user_hash: &str,
        total_rks: f64,
        clues_json: &str,
        updated_at: &str,
    ) -> Result<(), AppError> {
        if !total_rks.is_finite() || !(0.0..=30.0).contains(&total_rks) {
            return Err(AppError::Internal(
                "RKS guess snapshot has an invalid total RKS".into(),
            ));
        }
        if clues_json.len() > MAX_SNAPSHOT_JSON_BYTES {
            return Err(AppError::Internal("RKS guess snapshot is too large".into()));
        }
        let clues = serde_json::from_str::<Vec<serde_json::Value>>(clues_json)
            .map_err(|e| AppError::Internal(format!("validate RKS guess snapshot: {e}")))?;
        if !(2..=MAX_SNAPSHOT_CLUES).contains(&clues.len()) {
            return Err(AppError::Internal(
                "RKS guess snapshot requires between two and thirty clues".into(),
            ));
        }
        sqlx::query(
            "INSERT INTO rks_guess_snapshots(user_hash,total_rks,clues_json,updated_at)
             VALUES(?,?,?,?)
             ON CONFLICT(user_hash) DO UPDATE SET
               total_rks=excluded.total_rks,
               clues_json=excluded.clues_json,
               updated_at=excluded.updated_at",
        )
        .bind(user_hash)
        .bind(total_rks)
        .bind(clues_json)
        .bind(updated_at)
        .execute(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("upsert RKS guess snapshot: {e}")))?;
        Ok(())
    }

    /// 随机读取一份不属于当前玩家的有效匿名题目。
    ///
    /// 损坏或线索不足的旧记录会被跳过，避免单条坏数据阻断开局。
    /// 查询最多只物化少量候选记录，禁止把整张题库和所有 JSON 一次性读入内存。
    pub async fn random_rks_guess_snapshot_excluding(
        &self,
        excluded_user_hash: &str,
    ) -> Result<Option<RksGuessSnapshot>, AppError> {
        let rows = sqlx::query(
            "SELECT user_hash,total_rks,clues_json
             FROM rks_guess_snapshots
             WHERE user_hash<>?
               AND total_rks BETWEEN 0.0 AND 30.0
               AND length(clues_json) BETWEEN 2 AND ?
             ORDER BY RANDOM()
             LIMIT ?",
        )
        .bind(excluded_user_hash)
        .bind(MAX_SNAPSHOT_JSON_BYTES as i64)
        .bind(RANDOM_SAMPLE_LIMIT)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query random RKS guess snapshot: {e}")))?;

        for row in rows {
            let clues_json = row.try_get::<String, _>("clues_json").unwrap_or_default();
            let valid =
                serde_json::from_str::<Vec<serde_json::Value>>(&clues_json).is_ok_and(|clues| {
                    (MIN_SINGLE_GAME_CLUES..=MAX_SNAPSHOT_CLUES).contains(&clues.len())
                        && clues
                            .iter()
                            .filter_map(|clue| clue.get("slot").and_then(serde_json::Value::as_str))
                            .collect::<HashSet<_>>()
                            .len()
                            >= MIN_SINGLE_GAME_CLUES
                });
            if !valid {
                continue;
            }
            let total_rks = row.try_get::<f64, _>("total_rks").unwrap_or(f64::NAN);
            if !total_rks.is_finite() || !(0.0..=30.0).contains(&total_rks) {
                continue;
            }
            return Ok(Some(RksGuessSnapshot {
                user_hash: row.try_get("user_hash").unwrap_or_default(),
                total_rks,
                clues_json,
            }));
        }
        Ok(None)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn clues(prefix: &str) -> String {
        serde_json::to_string(
            &(1..=MIN_SINGLE_GAME_CLUES)
                .map(|index| serde_json::json!({ "slot": format!("{prefix}{index}") }))
                .collect::<Vec<_>>(),
        )
        .unwrap()
    }

    async fn storage(label: &str) -> (StatsStorage, std::path::PathBuf) {
        let path =
            std::env::temp_dir().join(format!("phi-rks-guess-{label}-{}.db", uuid::Uuid::new_v4()));
        let storage = StatsStorage::connect_sqlite(path.to_string_lossy().as_ref(), false)
            .await
            .unwrap();
        storage.init_schema().await.unwrap();
        (storage, path)
    }

    #[tokio::test]
    async fn snapshot_upsert_replaces_same_user_and_excludes_current_user() {
        let (storage, path) = storage("upsert").await;
        let clues_a = clues("B");
        let clues_b = clues("P");
        storage
            .upsert_rks_guess_snapshot("self", 15.0, &clues_a, "2026-08-20T00:00:00Z")
            .await
            .unwrap();
        storage
            .upsert_rks_guess_snapshot("other", 16.0, &clues_a, "2026-08-20T00:00:00Z")
            .await
            .unwrap();
        storage
            .upsert_rks_guess_snapshot("other", 16.5, &clues_b, "2026-08-20T01:00:00Z")
            .await
            .unwrap();

        let picked = storage
            .random_rks_guess_snapshot_excluding("self")
            .await
            .unwrap()
            .unwrap();
        assert_eq!(picked.user_hash, "other");
        assert_eq!(picked.total_rks, 16.5);
        assert_eq!(picked.clues_json, clues_b);

        drop(storage);
        let _ = std::fs::remove_file(path);
    }

    #[tokio::test]
    async fn no_other_snapshot_returns_none() {
        let (storage, path) = storage("none").await;
        storage
            .upsert_rks_guess_snapshot(
                "self",
                15.0,
                r#"[{"slot":"B1"},{"slot":"B2"}]"#,
                "2026-08-20T00:00:00Z",
            )
            .await
            .unwrap();
        assert!(
            storage
                .random_rks_guess_snapshot_excluding("self")
                .await
                .unwrap()
                .is_none()
        );
        drop(storage);
        let _ = std::fs::remove_file(path);
    }

    #[tokio::test]
    async fn damaged_snapshot_is_skipped() {
        let (storage, path) = storage("damaged").await;
        sqlx::query(
            "INSERT INTO rks_guess_snapshots(user_hash,total_rks,clues_json,updated_at)
             VALUES(?,?,?,?)",
        )
        .bind("broken")
        .bind(17.0_f64)
        .bind("not-json")
        .bind("2026-08-20T00:00:00Z")
        .execute(&storage.pool)
        .await
        .unwrap();
        assert!(
            storage
                .random_rks_guess_snapshot_excluding("self")
                .await
                .unwrap()
                .is_none()
        );
        drop(storage);
        let _ = std::fs::remove_file(path);
    }

    #[tokio::test]
    async fn oversized_snapshot_is_rejected_before_database_write() {
        let (storage, path) = storage("oversized").await;
        let huge_value = "x".repeat(MAX_SNAPSHOT_JSON_BYTES);
        let clues = serde_json::to_string(&vec![huge_value.clone(), huge_value]).unwrap();
        assert!(
            storage
                .upsert_rks_guess_snapshot("huge", 16.0, &clues, "2026-08-20T00:00:00Z")
                .await
                .is_err()
        );
        drop(storage);
        let _ = std::fs::remove_file(path);
    }
}
