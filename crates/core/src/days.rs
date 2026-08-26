use crate::buckets::AppBucket;
use crate::error::CoreError;
use chrono::NaiveDate;
use chrono_tz::Tz;
use std::collections::BTreeMap;

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct DayTotal {
    pub package: String,
    pub day: NaiveDate,
    pub foreground_ms: i64,
    pub launch_count: i32,
}

/// Fold hourly buckets into local days. The day is the local date in `tz`, not
/// a UTC date: a day means what the child experienced.
pub fn roll_days(buckets: &[AppBucket], tz: &str) -> Result<Vec<DayTotal>, CoreError> {
    let zone: Tz = tz
        .parse()
        .map_err(|_| CoreError::UnknownTimezone(tz.to_string()))?;
    let mut out: BTreeMap<(NaiveDate, String), DayTotal> = BTreeMap::new();

    for b in buckets {
        let day = b.hour_start.with_timezone(&zone).date_naive();
        let entry = out
            .entry((day, b.package.clone()))
            .or_insert_with(|| DayTotal {
                package: b.package.clone(),
                day,
                foreground_ms: 0,
                launch_count: 0,
            });
        entry.foreground_ms += b.foreground_ms;
        entry.launch_count += b.launch_count;
    }

    Ok(out.into_values().collect())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::buckets::AppBucket;
    use chrono::{NaiveDate, TimeZone, Utc};

    fn bucket(h: u32, ms: i64, launches: i32) -> AppBucket {
        AppBucket {
            package: "com.a".into(),
            hour_start: Utc.with_ymd_and_hms(2026, 8, 21, h, 0, 0).unwrap(),
            foreground_ms: ms,
            launch_count: launches,
            background_ms: 0,
        }
    }

    #[test]
    fn sums_buckets_within_a_local_day() {
        let out = roll_days(&[bucket(10, 1000, 1), bucket(11, 2000, 2)], "Europe/Zurich").unwrap();
        assert_eq!(out.len(), 1);
        assert_eq!(out[0].day, NaiveDate::from_ymd_opt(2026, 8, 21).unwrap());
        assert_eq!(out[0].foreground_ms, 3000);
        assert_eq!(out[0].launch_count, 3);
    }

    #[test]
    fn late_utc_hours_belong_to_the_next_local_day() {
        // 23:00 UTC on 2026-08-21 is 01:00 on 2026-08-22 in Zurich (CEST, +02:00).
        let out = roll_days(&[bucket(23, 5000, 1)], "Europe/Zurich").unwrap();
        assert_eq!(out[0].day, NaiveDate::from_ymd_opt(2026, 8, 22).unwrap());
    }

    #[test]
    fn unknown_timezone_is_an_error() {
        assert!(roll_days(&[], "Mars/Olympus").is_err());
    }
}
