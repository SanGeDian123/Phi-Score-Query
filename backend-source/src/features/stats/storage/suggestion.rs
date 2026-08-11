use sqlx::Row;

use crate::error::AppError;
use crate::features::suggestion::models::{
    SuggestionAuthor, SuggestionCommentRecord, SuggestionNotificationItem, SuggestionPostRecord,
};

use super::StatsStorage;

impl StatsStorage {
    pub async fn suggestion_author(&self, user_hash: &str) -> Result<SuggestionAuthor, AppError> {
        let row = sqlx::query(
            "SELECT
                COALESCE(NULLIF(TRIM(up.nickname), ''), NULLIF(TRIM(up.alias), ''), 'Phigros Player') AS nickname,
                up.avatar AS avatar,
                up.challenge_mode_rank AS challenge_mode_rank,
                COALESCE(lr.total_rks, 0.0) AS rks
             FROM (SELECT ? AS user_hash) seed
             LEFT JOIN user_profile up ON up.user_hash = seed.user_hash
             LEFT JOIN leaderboard_rks lr ON lr.user_hash = seed.user_hash",
        )
        .bind(user_hash)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query suggestion author: {e}")))?;
        Ok(SuggestionAuthor {
            nickname: row
                .try_get::<String, _>("nickname")
                .unwrap_or_else(|_| "Phigros Player".to_string()),
            avatar: row.try_get("avatar").ok(),
            challenge_mode_rank: row.try_get("challenge_mode_rank").ok(),
            rks: row.try_get("rks").unwrap_or(0.0),
        })
    }

    pub async fn latest_suggestion_post_at(
        &self,
        user_hash: &str,
    ) -> Result<Option<String>, AppError> {
        sqlx::query_scalar(
            "SELECT created_at FROM suggestion_posts WHERE user_hash=? ORDER BY created_at DESC LIMIT 1",
        )
        .bind(user_hash)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query latest suggestion post: {e}")))
    }

    pub async fn latest_suggestion_comment_at(
        &self,
        user_hash: &str,
    ) -> Result<Option<String>, AppError> {
        sqlx::query_scalar(
            "SELECT created_at FROM suggestion_comments WHERE user_hash=? ORDER BY created_at DESC LIMIT 1",
        )
        .bind(user_hash)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query latest suggestion comment: {e}")))
    }

    #[allow(clippy::too_many_arguments)]
    pub async fn insert_suggestion_post(
        &self,
        id: &str,
        user_hash: &str,
        description: &str,
        image_name: &str,
        author: &SuggestionAuthor,
        created_at: &str,
    ) -> Result<(), AppError> {
        sqlx::query(
            "INSERT INTO suggestion_posts
             (id,user_hash,description,image_name,nickname,avatar,challenge_mode_rank,rks,created_at)
             VALUES(?,?,?,?,?,?,?,?,?)",
        )
        .bind(id)
        .bind(user_hash)
        .bind(description)
        .bind(image_name)
        .bind(&author.nickname)
        .bind(&author.avatar)
        .bind(author.challenge_mode_rank)
        .bind(author.rks)
        .bind(created_at)
        .execute(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("insert suggestion post: {e}")))?;
        Ok(())
    }

    pub async fn random_suggestion_post(
        &self,
        exclude: Option<&str>,
    ) -> Result<Option<SuggestionPostRecord>, AppError> {
        let row = if let Some(exclude) = exclude {
            sqlx::query(
                "SELECT id,user_hash,description,image_name,nickname,avatar,challenge_mode_rank,rks,created_at
                 FROM suggestion_posts WHERE status='active' AND id<>? ORDER BY RANDOM() LIMIT 1",
            )
            .bind(exclude)
            .fetch_optional(&self.pool)
            .await
        } else {
            sqlx::query(
                "SELECT id,user_hash,description,image_name,nickname,avatar,challenge_mode_rank,rks,created_at
                 FROM suggestion_posts WHERE status='active' ORDER BY RANDOM() LIMIT 1",
            )
            .fetch_optional(&self.pool)
            .await
        }
        .map_err(|e| AppError::Internal(format!("query random suggestion post: {e}")))?;
        Ok(row.map(|row| SuggestionPostRecord {
            id: row.try_get("id").unwrap_or_default(),
            description: row.try_get("description").unwrap_or_default(),
            image_name: row.try_get("image_name").unwrap_or_default(),
            nickname: row
                .try_get("nickname")
                .unwrap_or_else(|_| "Phigros Player".to_string()),
            avatar: row.try_get("avatar").ok(),
            challenge_mode_rank: row.try_get("challenge_mode_rank").ok(),
            rks: row.try_get("rks").unwrap_or(0.0),
            created_at: row.try_get("created_at").unwrap_or_default(),
            user_hash: row.try_get("user_hash").unwrap_or_default(),
        }))
    }

    pub async fn suggestion_post(
        &self,
        post_id: &str,
    ) -> Result<Option<SuggestionPostRecord>, AppError> {
        let row = sqlx::query(
            "SELECT id,user_hash,description,image_name,nickname,avatar,challenge_mode_rank,rks,created_at
             FROM suggestion_posts WHERE id=? AND status='active' LIMIT 1",
        )
        .bind(post_id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query suggestion post: {e}")))?;
        Ok(row.map(|row| SuggestionPostRecord {
            id: row.try_get("id").unwrap_or_default(),
            description: row.try_get("description").unwrap_or_default(),
            image_name: row.try_get("image_name").unwrap_or_default(),
            nickname: row
                .try_get("nickname")
                .unwrap_or_else(|_| "Phigros Player".to_string()),
            avatar: row.try_get("avatar").ok(),
            challenge_mode_rank: row.try_get("challenge_mode_rank").ok(),
            rks: row.try_get("rks").unwrap_or(0.0),
            created_at: row.try_get("created_at").unwrap_or_default(),
            user_hash: row.try_get("user_hash").unwrap_or_default(),
        }))
    }

    pub async fn own_suggestion_posts(
        &self,
        user_hash: &str,
    ) -> Result<Vec<SuggestionPostRecord>, AppError> {
        let rows = sqlx::query(
            "SELECT id,user_hash,description,image_name,nickname,avatar,challenge_mode_rank,rks,created_at
             FROM suggestion_posts
             WHERE user_hash=? AND status='active' ORDER BY created_at DESC LIMIT 50",
        )
        .bind(user_hash)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query own suggestion posts: {e}")))?;
        Ok(rows
            .into_iter()
            .map(|row| SuggestionPostRecord {
                id: row.try_get("id").unwrap_or_default(),
                description: row.try_get("description").unwrap_or_default(),
                image_name: row.try_get("image_name").unwrap_or_default(),
                nickname: row
                    .try_get("nickname")
                    .unwrap_or_else(|_| "Phigros Player".to_string()),
                avatar: row.try_get("avatar").ok(),
                challenge_mode_rank: row.try_get("challenge_mode_rank").ok(),
                rks: row.try_get("rks").unwrap_or(0.0),
                created_at: row.try_get("created_at").unwrap_or_default(),
                user_hash: row.try_get("user_hash").unwrap_or_default(),
            })
            .collect())
    }

    pub async fn commented_suggestion_posts(
        &self,
        user_hash: &str,
    ) -> Result<Vec<SuggestionPostRecord>, AppError> {
        let rows = sqlx::query(
            "SELECT p.id,p.user_hash,p.description,p.image_name,p.nickname,p.avatar,
                    p.challenge_mode_rank,p.rks,p.created_at,MAX(c.created_at) AS latest_comment_at
             FROM suggestion_posts p
             JOIN suggestion_comments c ON c.post_id=p.id
             WHERE c.user_hash=? AND c.status='active' AND p.status='active'
             GROUP BY p.id ORDER BY latest_comment_at DESC LIMIT 50",
        )
        .bind(user_hash)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query commented suggestion posts: {e}")))?;
        Ok(rows
            .into_iter()
            .map(|row| SuggestionPostRecord {
                id: row.try_get("id").unwrap_or_default(),
                description: row.try_get("description").unwrap_or_default(),
                image_name: row.try_get("image_name").unwrap_or_default(),
                nickname: row
                    .try_get("nickname")
                    .unwrap_or_else(|_| "Phigros Player".to_string()),
                avatar: row.try_get("avatar").ok(),
                challenge_mode_rank: row.try_get("challenge_mode_rank").ok(),
                rks: row.try_get("rks").unwrap_or(0.0),
                created_at: row.try_get("created_at").unwrap_or_default(),
                user_hash: row.try_get("user_hash").unwrap_or_default(),
            })
            .collect())
    }

    pub async fn suggestion_post_exists(&self, post_id: &str) -> Result<bool, AppError> {
        let value: i64 = sqlx::query_scalar(
            "SELECT EXISTS(SELECT 1 FROM suggestion_posts WHERE id=? AND status='active')",
        )
        .bind(post_id)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query suggestion post: {e}")))?;
        Ok(value != 0)
    }

    #[allow(clippy::too_many_arguments)]
    pub async fn insert_suggestion_comment(
        &self,
        id: &str,
        post_id: &str,
        user_hash: &str,
        text: &str,
        image_name: Option<&str>,
        author: &SuggestionAuthor,
        created_at: &str,
    ) -> Result<(), AppError> {
        sqlx::query(
            "INSERT INTO suggestion_comments
             (id,post_id,user_hash,text,image_name,nickname,avatar,challenge_mode_rank,rks,created_at)
             VALUES(?,?,?,?,?,?,?,?,?,?)",
        )
        .bind(id)
        .bind(post_id)
        .bind(user_hash)
        .bind(text)
        .bind(image_name)
        .bind(&author.nickname)
        .bind(&author.avatar)
        .bind(author.challenge_mode_rank)
        .bind(author.rks)
        .bind(created_at)
        .execute(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("insert suggestion comment: {e}")))?;
        Ok(())
    }

    pub async fn suggestion_comments(
        &self,
        post_id: &str,
    ) -> Result<Vec<SuggestionCommentRecord>, AppError> {
        let rows = sqlx::query(
            "SELECT id,user_hash,text,image_name,nickname,avatar,challenge_mode_rank,rks,created_at
             FROM suggestion_comments
             WHERE post_id=? AND status='active' ORDER BY created_at ASC LIMIT 100",
        )
        .bind(post_id)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query suggestion comments: {e}")))?;
        Ok(rows
            .into_iter()
            .map(|row| SuggestionCommentRecord {
                id: row.try_get("id").unwrap_or_default(),
                text: row.try_get("text").unwrap_or_default(),
                image_name: row
                    .try_get::<Option<String>, _>("image_name")
                    .ok()
                    .flatten()
                    .filter(|value| !value.trim().is_empty()),
                nickname: row
                    .try_get("nickname")
                    .unwrap_or_else(|_| "Phigros Player".to_string()),
                avatar: row.try_get("avatar").ok(),
                challenge_mode_rank: row.try_get("challenge_mode_rank").ok(),
                rks: row.try_get("rks").unwrap_or(0.0),
                created_at: row.try_get("created_at").unwrap_or_default(),
                user_hash: row.try_get("user_hash").unwrap_or_default(),
            })
            .collect())
    }

    pub async fn suggestion_comment(
        &self,
        comment_id: &str,
    ) -> Result<Option<SuggestionCommentRecord>, AppError> {
        let row = sqlx::query(
            "SELECT id,user_hash,text,image_name,nickname,avatar,challenge_mode_rank,rks,created_at
             FROM suggestion_comments WHERE id=? AND status='active' LIMIT 1",
        )
        .bind(comment_id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query suggestion comment: {e}")))?;
        Ok(row.map(|row| SuggestionCommentRecord {
            id: row.try_get("id").unwrap_or_default(),
            text: row.try_get("text").unwrap_or_default(),
            image_name: row
                .try_get::<Option<String>, _>("image_name")
                .ok()
                .flatten()
                .filter(|value| !value.trim().is_empty()),
            nickname: row
                .try_get("nickname")
                .unwrap_or_else(|_| "Phigros Player".to_string()),
            avatar: row.try_get("avatar").ok(),
            challenge_mode_rank: row.try_get("challenge_mode_rank").ok(),
            rks: row.try_get("rks").unwrap_or(0.0),
            created_at: row.try_get("created_at").unwrap_or_default(),
            user_hash: row.try_get("user_hash").unwrap_or_default(),
        }))
    }

    pub async fn delete_suggestion_post(
        &self,
        post_id: &str,
        user_hash: &str,
    ) -> Result<Option<Vec<String>>, AppError> {
        let mut transaction = self
            .pool
            .begin()
            .await
            .map_err(|e| AppError::Internal(format!("begin delete suggestion post: {e}")))?;
        let mut images: Vec<String> = sqlx::query_scalar(
            "SELECT image_name FROM suggestion_posts WHERE id=? AND user_hash=? AND status='active'",
        )
        .bind(post_id)
        .bind(user_hash)
        .fetch_all(&mut *transaction)
        .await
        .map_err(|e| AppError::Internal(format!("query suggestion post image: {e}")))?;
        if images.is_empty() {
            transaction
                .rollback()
                .await
                .map_err(|e| AppError::Internal(format!("rollback delete suggestion post: {e}")))?;
            return Ok(None);
        }
        let comment_images: Vec<Option<String>> = sqlx::query_scalar(
            "SELECT image_name FROM suggestion_comments WHERE post_id=? AND status='active'",
        )
        .bind(post_id)
        .fetch_all(&mut *transaction)
        .await
        .map_err(|e| AppError::Internal(format!("query suggestion comment images: {e}")))?;
        images.extend(
            comment_images
                .into_iter()
                .flatten()
                .filter(|value| !value.trim().is_empty()),
        );
        sqlx::query(
            "UPDATE suggestion_comments SET status='deleted' WHERE post_id=? AND status='active'",
        )
        .bind(post_id)
        .execute(&mut *transaction)
        .await
        .map_err(|e| AppError::Internal(format!("delete suggestion post comments: {e}")))?;
        sqlx::query(
            "UPDATE suggestion_posts SET status='deleted' WHERE id=? AND user_hash=? AND status='active'",
        )
            .bind(post_id)
            .bind(user_hash)
            .execute(&mut *transaction)
            .await
            .map_err(|e| AppError::Internal(format!("delete suggestion post: {e}")))?;
        transaction
            .commit()
            .await
            .map_err(|e| AppError::Internal(format!("commit delete suggestion post: {e}")))?;
        Ok(Some(images))
    }

    pub async fn delete_suggestion_comment(
        &self,
        comment_id: &str,
        user_hash: &str,
    ) -> Result<Option<Option<String>>, AppError> {
        let row: Option<Option<String>> = sqlx::query_scalar(
            "SELECT image_name FROM suggestion_comments WHERE id=? AND user_hash=? AND status='active'",
        )
        .bind(comment_id)
        .bind(user_hash)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query suggestion comment image: {e}")))?;
        let Some(image_name) = row else {
            return Ok(None);
        };
        sqlx::query(
            "UPDATE suggestion_comments SET status='deleted' WHERE id=? AND user_hash=? AND status='active'",
        )
        .bind(comment_id)
        .bind(user_hash)
        .execute(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("delete suggestion comment: {e}")))?;
        Ok(Some(image_name.filter(|value| !value.trim().is_empty())))
    }

    pub async fn suggestion_notifications(
        &self,
        user_hash: &str,
        after: &str,
    ) -> Result<Vec<SuggestionNotificationItem>, AppError> {
        let rows = sqlx::query(
            "SELECT p.id AS post_id,p.description AS post_title,COUNT(c.id) AS comment_count,
                    MAX(c.created_at) AS latest_comment_at
             FROM suggestion_posts p
             JOIN suggestion_comments c ON c.post_id=p.id
             WHERE p.user_hash=? AND p.status='active' AND c.status='active'
               AND c.user_hash<>? AND julianday(c.created_at)>julianday(?)
             GROUP BY p.id,p.description ORDER BY latest_comment_at ASC LIMIT 50",
        )
        .bind(user_hash)
        .bind(user_hash)
        .bind(after)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("query suggestion notifications: {e}")))?;
        Ok(rows
            .into_iter()
            .map(|row| SuggestionNotificationItem {
                post_id: row.try_get("post_id").unwrap_or_default(),
                post_title: row.try_get("post_title").unwrap_or_default(),
                comment_count: row.try_get("comment_count").unwrap_or(0),
                latest_comment_at: row.try_get("latest_comment_at").unwrap_or_default(),
            })
            .collect())
    }
}

#[cfg(test)]
mod tests {
    use uuid::Uuid;

    use crate::features::suggestion::models::SuggestionAuthor;

    use super::StatsStorage;

    #[tokio::test]
    async fn suggestion_round_trip_uses_server_side_player_profile() {
        let path = std::env::temp_dir().join(format!("phi-suggestion-{}.db", Uuid::new_v4()));
        let storage = StatsStorage::connect_sqlite(path.to_string_lossy().as_ref(), false)
            .await
            .unwrap();
        storage.init_schema().await.unwrap();
        let now = "2026-08-11T00:00:00Z";
        sqlx::query(
            "INSERT INTO user_profile(user_hash,nickname,avatar,challenge_mode_rank,created_at,updated_at)
             VALUES('user-1','Alice','Introduction',405,?,?)",
        )
        .bind(now)
        .bind(now)
        .execute(&storage.pool)
        .await
        .unwrap();
        sqlx::query(
            "INSERT INTO leaderboard_rks(user_hash,total_rks,created_at,updated_at)
             VALUES('user-1',15.625,?,?)",
        )
        .bind(now)
        .bind(now)
        .execute(&storage.pool)
        .await
        .unwrap();

        let author = storage.suggestion_author("user-1").await.unwrap();
        assert_eq!(author.nickname, "Alice");
        assert_eq!(author.avatar.as_deref(), Some("Introduction"));
        assert_eq!(author.challenge_mode_rank, Some(405));
        assert!((author.rks - 15.625).abs() < f64::EPSILON);

        storage
            .insert_suggestion_post("post-1", "user-1", "求建议！", "score.png", &author, now)
            .await
            .unwrap();
        let post = storage.random_suggestion_post(None).await.unwrap().unwrap();
        assert_eq!(post.id, "post-1");
        assert_eq!(post.nickname, "Alice");

        let commenter = SuggestionAuthor {
            nickname: "Bob".into(),
            avatar: None,
            challenge_mode_rank: Some(302),
            rks: 14.0,
        };
        storage
            .insert_suggestion_comment(
                "comment-1",
                "post-1",
                "user-2",
                "先补短板",
                None,
                &commenter,
                now,
            )
            .await
            .unwrap();
        let comments = storage.suggestion_comments("post-1").await.unwrap();
        assert_eq!(comments.len(), 1);
        assert_eq!(comments[0].text, "先补短板");

        assert_eq!(comments[0].user_hash, "user-2");

        let commented_posts = storage.commented_suggestion_posts("user-2").await.unwrap();
        assert_eq!(commented_posts.len(), 1);
        assert_eq!(commented_posts[0].id, "post-1");

        let denied_comment_delete = storage
            .delete_suggestion_comment("comment-1", "user-1")
            .await
            .unwrap();
        assert!(denied_comment_delete.is_none());
        assert_eq!(
            storage.suggestion_comments("post-1").await.unwrap().len(),
            1
        );

        let notification_items = storage
            .suggestion_notifications("user-1", "2026-08-10T23:59:59Z")
            .await
            .unwrap();
        assert_eq!(notification_items.len(), 1);
        assert_eq!(notification_items[0].comment_count, 1);

        let denied_post_delete = storage
            .delete_suggestion_post("post-1", "user-2")
            .await
            .unwrap();
        assert!(denied_post_delete.is_none());
        assert!(storage.suggestion_post("post-1").await.unwrap().is_some());

        let deleted_comment = storage
            .delete_suggestion_comment("comment-1", "user-2")
            .await
            .unwrap();
        assert_eq!(deleted_comment, Some(None));
        assert!(
            storage
                .suggestion_comments("post-1")
                .await
                .unwrap()
                .is_empty()
        );

        let deleted_post = storage
            .delete_suggestion_post("post-1", "user-1")
            .await
            .unwrap();
        assert_eq!(deleted_post, Some(vec!["score.png".to_string()]));
        assert!(storage.suggestion_post("post-1").await.unwrap().is_none());

        storage.pool.close().await;
        let _ = std::fs::remove_file(path);
    }
}
