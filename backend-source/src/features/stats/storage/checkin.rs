use sqlx::Row;
use super::StatsStorage;
use crate::{AppError, features::checkin::{CheckinRecord, CheckinStatus}};
fn error(e: impl std::fmt::Display) -> AppError { AppError::Internal(format!("签到存储: {e}")) }
impl StatsStorage {
    pub async fn insert_checkin(&self, user: &str, record: &CheckinRecord) -> Result<(), AppError> {
        // A single INSERT is atomic. Totals derive from this ledger, never a separate increment.
        sqlx::query("INSERT INTO daily_checkins(user_hash,date,coin,luck,record_json) VALUES(?,?,?,?,?) ON CONFLICT(user_hash,date) DO NOTHING")
            .bind(user).bind(&record.date).bind(record.coin).bind(record.luck)
            .bind(serde_json::to_string(record).map_err(error)?).execute(&self.pool).await.map_err(error)?;
        Ok(())
    }
    pub async fn checkin_status(&self, user: &str, date: &str, month: &str) -> Result<CheckinStatus, AppError> {
        let mut tx = self.pool.begin().await.map_err(error)?;
        let total = sqlx::query("SELECT COUNT(*) days,COALESCE(SUM(coin),0) coin FROM daily_checkins WHERE user_hash=?").bind(user).fetch_one(&mut *tx).await.map_err(error)?;
        let today: Option<String> = sqlx::query_scalar("SELECT record_json FROM daily_checkins WHERE user_hash=? AND date=?").bind(user).bind(date).fetch_optional(&mut *tx).await.map_err(error)?;
        let dates = sqlx::query_scalar("SELECT date FROM daily_checkins WHERE user_hash=? AND date LIKE ? ORDER BY date").bind(user).bind(format!("{month}-%")).fetch_all(&mut *tx).await.map_err(error)?;
        tx.commit().await.map_err(error)?;
        Ok(CheckinStatus { server_date: date.into(), month: month.into(), total_coin: total.try_get("coin").map_err(error)?, total_days: total.try_get("days").map_err(error)?, today: today.map(|s| serde_json::from_str(&s)).transpose().map_err(error)?, dates })
    }
    pub async fn checkin_leaderboard(&self) -> Result<serde_json::Value, AppError> {
        let rows = sqlx::query("SELECT c.user_hash,COUNT(*) days,MAX(c.date) last_date,CASE WHEN p.is_public=1 THEN COALESCE(NULLIF(p.nickname,''),NULLIF(p.alias,''),'Phigros Player') ELSE '匿名玩家' END nickname FROM daily_checkins c LEFT JOIN user_profile p ON p.user_hash=c.user_hash LEFT JOIN leaderboard_rks l ON l.user_hash=c.user_hash WHERE COALESCE(l.is_hidden,0)=0 GROUP BY c.user_hash ORDER BY days DESC,last_date ASC,c.user_hash ASC LIMIT 100").fetch_all(&self.pool).await.map_err(error)?;
        let items: Vec<_> = rows.iter().enumerate().map(|(i,r)| serde_json::json!({"rank":i+1,"nickname":r.get::<String,_>("nickname"),"totalDays":r.get::<i64,_>("days")})).collect();
        Ok(serde_json::json!({"items":items}))
    }
    pub async fn checkin_trend(&self, start: &str, end: &str) -> Result<Vec<serde_json::Value>, AppError> {
        let rows = sqlx::query("WITH RECURSIVE dates(day) AS (SELECT ? UNION ALL SELECT date(day,'+1 day') FROM dates WHERE day < ?) SELECT day, (SELECT COUNT(*) FROM daily_checkins WHERE date=day) count, (SELECT COUNT(*) FROM (SELECT user_hash FROM leaderboard_rks WHERE date(datetime(created_at,'+8 hours'))<=day UNION SELECT user_hash FROM daily_checkins WHERE date<=day)) users FROM dates").bind(start).bind(end).fetch_all(&self.pool).await.map_err(error)?;
        Ok(rows.iter().map(|r| { let count=r.get::<i64,_>("count"); let users=r.get::<i64,_>("users"); serde_json::json!({"date":r.get::<String,_>("day"),"count":count,"totalUsers":users,"rate": if users>0 {count as f64/users as f64*100.0} else {0.0}}) }).collect())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    async fn checkin_is_idempotent_isolated_and_persistent() {
        let path = std::env::temp_dir().join(format!("checkin-{}.db",uuid::Uuid::new_v4()));
        let db=StatsStorage::connect_sqlite(path.to_str().unwrap(),false).await.unwrap(); db.init_schema().await.unwrap();
        let r=CheckinRecord{date:"2026-09-06".into(),coin:50,luck:100,quote_index:0,chart:None};
        let (a,b)=tokio::join!(db.insert_checkin("a",&r),db.insert_checkin("a",&r)); a.unwrap();b.unwrap();
        let s=db.checkin_status("a","2026-09-06","2026-09").await.unwrap();assert_eq!(s.total_coin,50);assert_eq!(s.total_days,1);assert_eq!(s.dates.len(),1);
        assert_eq!(db.checkin_status("b","2026-09-06","2026-09").await.unwrap().total_coin,0);
        let mut next=r.clone();next.date="2026-09-07".into();next.coin=15;db.insert_checkin("a",&next).await.unwrap();
        let reopened=StatsStorage::connect_sqlite(path.to_str().unwrap(),false).await.unwrap();
        let s=reopened.checkin_status("a","2026-09-08","2026-08").await.unwrap();assert_eq!(s.total_coin,65);assert_eq!(s.total_days,2);assert!(s.today.is_none());assert!(s.dates.is_empty());
        let trend=db.checkin_trend("2026-09-05","2026-09-07").await.unwrap();assert_eq!(trend[0]["count"],0);assert_eq!(trend[1]["count"],1);
        db.pool.close().await;reopened.pool.close().await;let _=std::fs::remove_file(path);
    }
}
