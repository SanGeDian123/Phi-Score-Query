use sqlx::Row;

use crate::error::AppError;

use super::{ChartAchievementSample, StatsStorage};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ChartAchievementPosition {
    pub score: i64,
    pub is_full_combo: bool,
    pub exceeded_count: i64,
}

impl StatsStorage {
    pub async fn upsert_chart_achievement_samples(
        &self,
        user_hash: &str,
        samples: &[ChartAchievementSample],
        updated_at: &str,
    ) -> Result<(), AppError> {
        if samples.is_empty() {
            return Ok(());
        }
        let mut tx = self
            .pool
            .begin()
            .await
            .map_err(|e| AppError::Internal(format!("begin chart achievement samples: {e}")))?;
        for sample in samples {
            sqlx::query(
                "INSERT INTO chart_achievement_samples
                 (user_hash,song_id,difficulty,score,is_full_combo,updated_at)
                 VALUES(?,?,?,?,?,?)
                 ON CONFLICT(user_hash,song_id,difficulty) DO UPDATE SET
                   score=excluded.score,
                   is_full_combo=excluded.is_full_combo,
                   updated_at=excluded.updated_at",
            )
            .bind(user_hash)
            .bind(&sample.song_id)
            .bind(&sample.difficulty)
            .bind(sample.score)
            .bind(i64::from(sample.is_full_combo))
            .bind(updated_at)
            .execute(&mut *tx)
            .await
            .map_err(|e| AppError::Internal(format!("upsert chart achievement sample: {e}")))?;
        }
        tx.commit()
            .await
            .map_err(|e| AppError::Internal(format!("commit chart achievement samples: {e}")))?;
        Ok(())
    }

    pub async fn chart_achievement_counts(
        &self,
        song_id: &str,
        difficulty: &str,
    ) -> Result<[i64; 8], AppError> {
        let row = sqlx::query(
            "SELECT
               COUNT(1) AS total,
               SUM(CASE WHEN score < 700000 AND is_full_combo=0 THEN 1 ELSE 0 END) AS f,
               SUM(CASE WHEN score BETWEEN 700000 AND 819999 AND is_full_combo=0 THEN 1 ELSE 0 END) AS c,
               SUM(CASE WHEN score BETWEEN 820000 AND 879999 AND is_full_combo=0 THEN 1 ELSE 0 END) AS b,
               SUM(CASE WHEN score BETWEEN 880000 AND 919999 AND is_full_combo=0 THEN 1 ELSE 0 END) AS a,
               SUM(CASE WHEN score BETWEEN 920000 AND 959999 AND is_full_combo=0 THEN 1 ELSE 0 END) AS s,
               SUM(CASE WHEN score BETWEEN 960000 AND 999999 AND is_full_combo=0 THEN 1 ELSE 0 END) AS v,
               SUM(CASE WHEN score < 1000000 AND is_full_combo<>0 THEN 1 ELSE 0 END) AS fc,
               SUM(CASE WHEN score=1000000 THEN 1 ELSE 0 END) AS ap
             FROM chart_achievement_samples sample
             WHERE song_id=? AND difficulty=?
               AND NOT EXISTS (SELECT 1 FROM publicly_restricted_users blocked
                 WHERE blocked.user_hash=sample.user_hash)",
        )
        .bind(song_id)
        .bind(difficulty)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query chart achievement rates: {e}")))?;
        Ok([
            row.try_get("f").unwrap_or(0),
            row.try_get("c").unwrap_or(0),
            row.try_get("b").unwrap_or(0),
            row.try_get("a").unwrap_or(0),
            row.try_get("s").unwrap_or(0),
            row.try_get("v").unwrap_or(0),
            row.try_get("fc").unwrap_or(0),
            row.try_get("ap").unwrap_or(0),
        ])
    }

    pub async fn chart_achievement_total(
        &self,
        song_id: &str,
        difficulty: &str,
    ) -> Result<i64, AppError> {
        let row = sqlx::query(
            "SELECT COUNT(1) AS total FROM chart_achievement_samples sample WHERE song_id=? AND difficulty=?
               AND NOT EXISTS (SELECT 1 FROM publicly_restricted_users blocked
                 WHERE blocked.user_hash=sample.user_hash)",
        )
        .bind(song_id)
        .bind(difficulty)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query chart achievement total: {e}")))?;
        Ok(row.try_get("total").unwrap_or(0))
    }

    pub async fn chart_achievement_position(
        &self,
        user_hash: &str,
        song_id: &str,
        difficulty: &str,
    ) -> Result<Option<ChartAchievementPosition>, AppError> {
        let row = sqlx::query(
            "SELECT mine.score, mine.is_full_combo,
                    (SELECT COUNT(1) FROM chart_achievement_samples other
                     WHERE other.song_id=mine.song_id AND other.difficulty=mine.difficulty
                       AND other.score < mine.score
                       AND NOT EXISTS (SELECT 1 FROM publicly_restricted_users blocked
                         WHERE blocked.user_hash=other.user_hash)) AS exceeded_count
             FROM chart_achievement_samples mine
             WHERE mine.user_hash=? AND mine.song_id=? AND mine.difficulty=?
               AND NOT EXISTS (SELECT 1 FROM publicly_restricted_users blocked
                 WHERE blocked.user_hash=mine.user_hash)
             LIMIT 1",
        )
        .bind(user_hash)
        .bind(song_id)
        .bind(difficulty)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query chart achievement position: {e}")))?;
        Ok(row.map(|row| ChartAchievementPosition {
            score: row.try_get("score").unwrap_or(0),
            is_full_combo: row.try_get::<i64, _>("is_full_combo").unwrap_or(0) != 0,
            exceeded_count: row.try_get("exceeded_count").unwrap_or(0),
        }))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn achievement_counts_are_exclusive_and_upsert_per_user() {
        let path =
            std::env::temp_dir().join(format!("phi-achievement-{}.db", uuid::Uuid::new_v4()));
        let storage = StatsStorage::connect_sqlite(path.to_string_lossy().as_ref(), false)
            .await
            .unwrap();
        storage.init_schema().await.unwrap();
        let samples = [
            ("u-f", 699_999, false),
            ("u-c", 700_000, false),
            ("u-s", 959_999, false),
            ("u-v", 960_000, false),
            ("u-fc", 960_000, true),
            ("u-ap", 1_000_000, true),
        ];
        for (user, score, fc) in samples {
            storage
                .upsert_chart_achievement_samples(
                    user,
                    &[ChartAchievementSample {
                        song_id: "song".into(),
                        difficulty: "AT".into(),
                        score,
                        is_full_combo: fc,
                    }],
                    "2026-08-12T00:00:00Z",
                )
                .await
                .unwrap();
        }
        assert_eq!(
            storage.chart_achievement_total("song", "AT").await.unwrap(),
            6
        );
        assert_eq!(
            storage
                .chart_achievement_counts("song", "AT")
                .await
                .unwrap(),
            [1, 1, 0, 0, 1, 1, 1, 1]
        );
        let position = storage
            .chart_achievement_position("u-v", "song", "AT")
            .await
            .unwrap()
            .unwrap();
        assert_eq!(position.score, 960_000);
        assert_eq!(position.exceeded_count, 3);
        storage
            .upsert_chart_achievement_samples(
                "u-s",
                &[ChartAchievementSample {
                    song_id: "song".into(),
                    difficulty: "AT".into(),
                    score: 1_000_000,
                    is_full_combo: true,
                }],
                "2026-08-12T01:00:00Z",
            )
            .await
            .unwrap();
        assert_eq!(
            storage.chart_achievement_total("song", "AT").await.unwrap(),
            6
        );
        assert_eq!(
            storage
                .chart_achievement_counts("song", "AT")
                .await
                .unwrap(),
            [1, 1, 0, 0, 0, 1, 1, 2]
        );
        drop(storage);
        let _ = std::fs::remove_file(path);
    }
}
