use super::StatsStorage;
use crate::AppError;
use sqlx::Row;

fn error(e: impl std::fmt::Display) -> AppError {
    AppError::Internal(format!("RKS guess wins storage: {e}"))
}

impl StatsStorage {
    pub async fn record_rks_guess_win(
        &self,
        game_id: &str,
        user: &str,
        mode: &str,
        finished_at: &str,
    ) -> Result<(), AppError> {
        // One durable ledger row per game, even after retries or a process restart.
        sqlx::query("INSERT INTO rks_guess_wins(game_id,user_hash,mode,finished_at) VALUES(?,?,?,?) ON CONFLICT(game_id) DO NOTHING")
            .bind(game_id).bind(user).bind(mode).bind(finished_at)
            .execute(&self.pool).await.map_err(error)?;
        Ok(())
    }

    pub async fn rks_guess_win_leaderboard(
        &self,
        user: &str,
    ) -> Result<serde_json::Value, AppError> {
        let mut tx = self.pool.begin().await.map_err(error)?;
        let rows = sqlx::query(
            "SELECT w.user_hash,COUNT(*) wins,SUM(w.mode='single') single_wins,SUM(w.mode='public') public_wins,
             MAX(w.finished_at) reached_at,
             CASE WHEN p.is_public=1 THEN COALESCE(NULLIF(p.nickname,''),NULLIF(p.alias,''),'Phigros Player')
             ELSE '匿名玩家' END nickname
             FROM rks_guess_wins w LEFT JOIN user_profile p ON p.user_hash=w.user_hash
             LEFT JOIN leaderboard_rks l ON l.user_hash=w.user_hash
             WHERE COALESCE(l.is_hidden,0)=0 GROUP BY w.user_hash
             ORDER BY wins DESC,reached_at ASC,w.user_hash ASC LIMIT 100")
            .fetch_all(&mut *tx).await.map_err(error)?;
        let my_wins: i64 =
            sqlx::query_scalar("SELECT COUNT(*) FROM rks_guess_wins WHERE user_hash=?")
                .bind(user)
                .fetch_one(&mut *tx)
                .await
                .map_err(error)?;
        tx.commit().await.map_err(error)?;
        let items: Vec<_> = rows.iter().enumerate().map(|(index, row)| serde_json::json!({
            "rank": index + 1, "nickname": row.get::<String,_>("nickname"),
            "totalWins": row.get::<i64,_>("wins"), "singleWins": row.get::<i64,_>("single_wins"),
            "publicWins": row.get::<i64,_>("public_wins"), "isMe": row.get::<String,_>("user_hash") == user,
        })).collect();
        Ok(serde_json::json!({"items": items, "myWins": my_wins}))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    async fn wins_are_durable_idempotent_ranked_and_private() {
        let path = std::env::temp_dir().join(format!("rks-wins-{}.db", uuid::Uuid::new_v4()));
        let storage = StatsStorage::connect_sqlite(path.to_str().unwrap(), false)
            .await
            .unwrap();
        storage.init_schema().await.unwrap();
        for (game, user, mode, time) in [
            ("g1", "a", "single", "01"),
            ("g2", "a", "public", "03"),
            ("g3", "b", "public", "02"),
            ("g4", "b", "single", "04"),
        ] {
            storage
                .record_rks_guess_win(game, user, mode, time)
                .await
                .unwrap();
        }
        storage
            .record_rks_guess_win("g1", "b", "public", "05")
            .await
            .unwrap();
        sqlx::query("INSERT INTO user_profile(user_hash,nickname,is_public,created_at,updated_at) VALUES('a','公开玩家',1,'',''),('b','隐藏姓名',0,'','')")
            .execute(&storage.pool).await.unwrap();
        storage.pool.close().await;
        let storage = StatsStorage::connect_sqlite(path.to_str().unwrap(), false)
            .await
            .unwrap();
        let board = storage.rks_guess_win_leaderboard("b").await.unwrap();
        assert_eq!(board["myWins"], 2);
        assert_eq!(board["items"][0]["nickname"], "公开玩家");
        assert_eq!(board["items"][0]["singleWins"], 1);
        assert_eq!(board["items"][0]["publicWins"], 1);
        assert_eq!(board["items"][1]["nickname"], "匿名玩家");
        assert_eq!(board["items"][1]["isMe"], true);
        assert!(!board.to_string().contains("隐藏姓名"));
        assert!(
            storage
                .record_rks_guess_win("bad", "a", "unknown", "05")
                .await
                .is_err()
        );
        storage.pool.close().await;
        let _ = std::fs::remove_file(path);
    }
}
