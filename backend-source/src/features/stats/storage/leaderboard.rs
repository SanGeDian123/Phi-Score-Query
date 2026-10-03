use std::collections::BTreeMap;

use chrono::NaiveDate;
use sqlx::Row;

use crate::error::AppError;

use super::StatsStorage;

impl StatsStorage {
    /// 按展示时区返回排行榜已登记用户的累计数量趋势。
    ///
    /// `leaderboard_rks` 是用户登记的权威表；以 `created_at` 计算每天新增后再累计，
    /// 这样不会把当前总数机械地重复到每一天，也不会暴露任何用户标识。
    pub async fn query_leaderboard_user_count_trend(
        &self,
        start: NaiveDate,
        end: NaiveDate,
        tz: chrono_tz::Tz,
    ) -> Result<Vec<(String, i64)>, AppError> {
        let rows = sqlx::query("SELECT created_at FROM leaderboard_rks")
            .fetch_all(&self.pool)
            .await
            .map_err(|e| AppError::Internal(format!("query leaderboard user trend: {e}")))?;

        let mut new_by_date = BTreeMap::<NaiveDate, i64>::new();
        let mut before_start = 0_i64;
        for row in rows {
            let created_at = row.try_get::<String, _>("created_at").unwrap_or_default();
            let Some(created_at) = chrono::DateTime::parse_from_rfc3339(&created_at).ok() else {
                // 历史异常时间不能让趋势低于当前总数，按起始日前已存在处理。
                before_start += 1;
                continue;
            };
            let date = created_at.with_timezone(&tz).date_naive();
            if date < start {
                before_start += 1;
            } else if date <= end {
                *new_by_date.entry(date).or_default() += 1;
            }
        }

        let mut out = Vec::new();
        let mut running = before_start;
        let mut date = start;
        while date <= end {
            running += new_by_date.get(&date).copied().unwrap_or(0);
            out.push((date.to_string(), running));
            date += chrono::Duration::days(1);
        }
        Ok(out)
    }

    pub async fn get_prev_rks(&self, user_hash: &str) -> Result<Option<(f64, String)>, AppError> {
        let row =
            sqlx::query("SELECT total_rks, updated_at FROM leaderboard_rks WHERE user_hash = ?")
                .bind(user_hash)
                .fetch_optional(&self.pool)
                .await
                .map_err(|e| AppError::Internal(format!("get prev rks: {e}")))?;
        if let Some(r) = row {
            Ok(Some((
                r.get::<f64, _>("total_rks"),
                r.get::<String, _>("updated_at"),
            )))
        } else {
            Ok(None)
        }
    }

    pub async fn upsert_leaderboard_rks(
        &self,
        user_hash: &str,
        total_rks: f64,
        user_kind: Option<&str>,
        suspicion_score: f64,
        hide: bool,
        now_rfc3339: &str,
    ) -> Result<(), AppError> {
        let is_hidden_i = i64::from(hide);
        sqlx::query(
            "INSERT INTO leaderboard_rks(user_hash,total_rks,user_kind,suspicion_score,is_hidden,created_at,updated_at) VALUES(?,?,?,?,?,?,?)
             ON CONFLICT(user_hash) DO UPDATE SET
               total_rks = CASE WHEN excluded.total_rks > leaderboard_rks.total_rks THEN excluded.total_rks ELSE leaderboard_rks.total_rks END,
               updated_at = CASE WHEN excluded.total_rks > leaderboard_rks.total_rks THEN excluded.updated_at ELSE leaderboard_rks.updated_at END,
               user_kind = COALESCE(excluded.user_kind, leaderboard_rks.user_kind),
               suspicion_score = excluded.suspicion_score,
               is_hidden = CASE WHEN leaderboard_rks.is_hidden=1 OR excluded.is_hidden=1 THEN 1 ELSE 0 END"
        )
        .bind(user_hash)
        .bind(total_rks)
        .bind(user_kind)
        .bind(suspicion_score)
        .bind(is_hidden_i)
        .bind(now_rfc3339)
        .bind(now_rfc3339)
        .execute(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("upsert leaderboard: {e}")))?;
        Ok(())
    }

    pub async fn set_leaderboard_hidden(
        &self,
        user_hash: &str,
        hide: bool,
    ) -> Result<(), AppError> {
        let is_hidden_i = i64::from(hide);
        sqlx::query("UPDATE leaderboard_rks SET is_hidden=? WHERE user_hash=?")
            .bind(is_hidden_i)
            .bind(user_hash)
            .execute(&self.pool)
            .await
            .map_err(|e| AppError::Internal(format!("update leaderboard hidden: {e}")))?;
        Ok(())
    }

    pub async fn upsert_details(
        &self,
        user_hash: &str,
        rks_comp_json: Option<&str>,
        best3_json: Option<&str>,
        ap3_json: Option<&str>,
        now_rfc3339: &str,
    ) -> Result<(), AppError> {
        sqlx::query(
            "INSERT INTO leaderboard_details(user_hash,rks_composition_json,best_top3_json,ap_top3_json,updated_at) VALUES(?,?,?,?,?)
             ON CONFLICT(user_hash) DO UPDATE SET
               rks_composition_json = COALESCE(excluded.rks_composition_json, leaderboard_details.rks_composition_json),
               best_top3_json = COALESCE(excluded.best_top3_json, leaderboard_details.best_top3_json),
               ap_top3_json = COALESCE(excluded.ap_top3_json, leaderboard_details.ap_top3_json),
               updated_at = excluded.updated_at"
        )
        .bind(user_hash)
        .bind(rks_comp_json)
        .bind(best3_json)
        .bind(ap3_json)
        .bind(now_rfc3339)
        .execute(&self.pool)
        .await
        .map_err(|e| AppError::Internal(format!("upsert details: {e}")))?;
        Ok(())
    }
}
