use crate::config::Config;
use chrono::{DateTime, Duration, Local, NaiveTime, Utc};
use sqlx::PgPool;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RetentionReport {
    pub folded_days: u64,
    pub deleted_hours: u64,
}

/// Fold hourly rows older than the cutoff into `usage_days`, then delete them.
/// `now` is a parameter so this is testable without waiting 13 months.
pub async fn run_once(
    pool: &PgPool,
    hourly_months: i64,
    now: DateTime<Utc>,
) -> Result<RetentionReport, sqlx::Error> {
    let cutoff = now - Duration::days(hourly_months * 30);
    let mut tx = pool.begin().await?;

    // The day comes from each row's own tz: a day means what the child
    // experienced, even if they were travelling.
    let folded = sqlx::query!(
        r#"INSERT INTO usage_days
             (child_id, package, day, foreground_ms, launch_count, background_ms)
           SELECT d.child_id, u.package, (u.hour_start AT TIME ZONE u.tz)::date,
                  SUM(u.foreground_ms), SUM(u.launch_count), SUM(u.background_ms)
           FROM usage_hours u
           JOIN devices d ON d.id = u.device_id
           WHERE u.hour_start < $1
           GROUP BY d.child_id, u.package, 3
           ON CONFLICT (child_id, package, day) DO UPDATE
             SET foreground_ms = usage_days.foreground_ms + EXCLUDED.foreground_ms,
                 launch_count  = usage_days.launch_count  + EXCLUDED.launch_count,
                 background_ms = usage_days.background_ms + EXCLUDED.background_ms"#,
        cutoff
    )
    .execute(&mut *tx)
    .await?
    .rows_affected();

    // Same transaction as the fold. Split apart, a crash in between would
    // either lose the data or double-count it on the next run.
    let deleted = sqlx::query!("DELETE FROM usage_hours WHERE hour_start < $1", cutoff)
        .execute(&mut *tx)
        .await?
        .rows_affected();

    sqlx::query!("DELETE FROM device_hours WHERE hour_start < $1", cutoff)
        .execute(&mut *tx)
        .await?;

    sqlx::query!("DELETE FROM sessions WHERE expires_at < $1", now)
        .execute(&mut *tx)
        .await?;

    tx.commit().await?;
    Ok(RetentionReport {
        folded_days: folded,
        deleted_hours: deleted,
    })
}

/// Daily in-process scheduler. In-process rather than a Kubernetes CronJob so a
/// self-hoster gets working retention from one container.
pub fn spawn(pool: PgPool, config: Config) {
    tokio::spawn(async move {
        let at =
            NaiveTime::parse_from_str(&config.retention_job_at, "%H:%M").unwrap_or_else(|_| {
                tracing::warn!(
                    value = %config.retention_job_at,
                    "invalid RETENTION_JOB_AT, using 04:00"
                );
                NaiveTime::from_hms_opt(4, 0, 0).expect("valid time")
            });

        loop {
            let now = Local::now();
            let mut next = now.date_naive().and_time(at);
            if next <= now.naive_local() {
                next += Duration::days(1);
            }
            let wait = (next - now.naive_local())
                .to_std()
                .unwrap_or(std::time::Duration::from_secs(3600));
            tokio::time::sleep(wait).await;

            match run_once(&pool, config.retention_hourly_months, Utc::now()).await {
                Ok(report) => tracing::info!(?report, "retention run complete"),
                Err(error) => tracing::error!(%error, "retention run failed"),
            }
        }
    });
}
