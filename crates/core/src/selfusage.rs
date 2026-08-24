//! Parsing the server's usage response for the two agents. The parent surfaces
//! decode it themselves; the agents must not, or two apps drift apart on what a
//! day means.

use crate::error::CoreError;
use chrono::{Duration, NaiveDate};
use std::collections::BTreeMap;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DayTotal {
    pub day: String,
    pub foreground_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AppTotal {
    pub package: String,
    pub label: String,
    pub foreground_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DayDetail {
    pub total_ms: i64,
    pub unlock_count: i32,
    /// 24 entries, screen-on milliseconds by local hour.
    pub hours: Vec<i64>,
    /// Ranked, longest first.
    pub apps: Vec<AppTotal>,
}

// Unknown fields are ignored on purpose: the server may grow a field before an
// installed agent knows about it, and an old phone must keep working.
#[derive(serde::Deserialize)]
struct PointWire {
    start: String,
    foreground_ms: i64,
}

#[derive(serde::Deserialize)]
struct SeriesWire {
    package: String,
    label: String,
    points: Vec<PointWire>,
}

#[derive(serde::Deserialize)]
struct TotalWire {
    start: String,
    screen_on_ms: i64,
    unlock_count: i32,
}

#[derive(serde::Deserialize)]
struct UsageWire {
    from: NaiveDate,
    to: NaiveDate,
    series: Vec<SeriesWire>,
    device_totals: Vec<TotalWire>,
}

fn parse(json: &str) -> Result<UsageWire, CoreError> {
    serde_json::from_str(json).map_err(|e| CoreError::BadJson(e.to_string()))
}

/// Day totals for every day in the response's own range, zero-filled.
pub fn day_strip(json: &str) -> Result<Vec<DayTotal>, CoreError> {
    let wire = parse(json)?;

    let mut totals: BTreeMap<String, i64> = BTreeMap::new();
    let mut day = wire.from;
    while day <= wire.to {
        totals.insert(day.to_string(), 0);
        day += Duration::days(1);
    }

    for series in &wire.series {
        for point in &series.points {
            // Only days the response claimed. A stray point must not land on
            // day one, which would read as a busy Monday that never happened.
            if let Some(slot) = totals.get_mut(&point.start) {
                *slot += point.foreground_ms;
            }
        }
    }

    Ok(totals
        .into_iter()
        .map(|(day, foreground_ms)| DayTotal { day, foreground_ms })
        .collect())
}

/// One local day: the hour ribbon, the ranked apps, the unlocks.
pub fn day_detail(json: &str) -> Result<DayDetail, CoreError> {
    let wire = parse(json)?;

    let mut hours = vec![0i64; 24];
    let mut unlock_count = 0;
    for total in &wire.device_totals {
        unlock_count += total.unlock_count;
        // Parsed strictly. Slicing blindly turns an unparseable value into hour
        // 0 and paints midnight usage that never happened.
        if let Some(hour) = local_hour(&total.start) {
            hours[hour] += total.screen_on_ms;
        }
    }

    let mut apps: Vec<AppTotal> = wire
        .series
        .iter()
        .map(|s| AppTotal {
            package: s.package.clone(),
            label: s.label.clone(),
            foreground_ms: s.points.iter().map(|p| p.foreground_ms).sum(),
        })
        .collect();
    apps.sort_by(|a, b| {
        b.foreground_ms
            .cmp(&a.foreground_ms)
            .then(a.package.cmp(&b.package))
    });

    Ok(DayDetail {
        total_ms: apps.iter().map(|a| a.foreground_ms).sum(),
        unlock_count,
        hours,
        apps,
    })
}

/// "2026-08-21T15:00:00+02:00" -> 15. The server already applied the caller's
/// timezone, so the local hour is the field after the T.
fn local_hour(stamp: &str) -> Option<usize> {
    let (date, rest) = stamp.split_once('T')?;
    if date.len() != 10 {
        return None;
    }
    let hour: usize = rest.get(0..2)?.parse().ok()?;
    (hour < 24).then_some(hour)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn day_response() -> &'static str {
        r#"{
            "child_id": "11111111-1111-1111-1111-111111111111",
            "from": "2026-08-18", "to": "2026-08-20", "bucket": "day", "tz": "Europe/Zurich",
            "devices": [],
            "series": [
                {"package": "com.a", "label": "A", "points": [
                    {"start": "2026-08-18", "foreground_ms": 60000, "launch_count": 1},
                    {"start": "2026-08-20", "foreground_ms": 30000, "launch_count": 2}
                ]}
            ],
            "device_totals": []
        }"#
    }

    #[test]
    fn a_day_with_no_rows_is_a_zero_not_a_hole() {
        let days = day_strip(day_response()).unwrap();
        assert_eq!(days.len(), 3, "every day between from and to appears");
        assert_eq!(
            days[0],
            DayTotal {
                day: "2026-08-18".into(),
                foreground_ms: 60_000
            }
        );
        assert_eq!(
            days[1],
            DayTotal {
                day: "2026-08-19".into(),
                foreground_ms: 0
            }
        );
        assert_eq!(
            days[2],
            DayTotal {
                day: "2026-08-20".into(),
                foreground_ms: 30_000
            }
        );
    }

    #[test]
    fn a_point_outside_the_range_is_dropped_not_folded_into_the_first_day() {
        let json = day_response().replace(
            "2026-08-18\", \"foreground_ms\": 60000",
            "2026-07-01\", \"foreground_ms\": 60000",
        );
        let days = day_strip(&json).unwrap();
        assert_eq!(days.iter().map(|d| d.foreground_ms).sum::<i64>(), 30_000);
        assert_eq!(
            days[0].foreground_ms, 0,
            "an out-of-range day must not land on day one"
        );
    }

    #[test]
    fn a_captcha_page_throws_rather_than_reading_as_an_empty_day() {
        let err = day_strip("<html><body>Are you a robot?</body></html>");
        assert!(
            err.is_err(),
            "an unparseable body must never read as no usage"
        );
        assert!(day_detail("<html>nope</html>").is_err());
    }

    #[test]
    fn detail_folds_hours_apps_and_unlocks() {
        let json = r#"{
            "child_id": "1", "from": "2026-08-20", "to": "2026-08-20",
            "bucket": "hour", "tz": "Europe/Zurich", "devices": [],
            "series": [
                {"package": "com.a", "label": "A", "points": [
                    {"start": "2026-08-20T10:00:00+02:00", "foreground_ms": 60000, "launch_count": 1}]},
                {"package": "com.b", "label": "B", "points": [
                    {"start": "2026-08-20T11:00:00+02:00", "foreground_ms": 90000, "launch_count": 3}]}
            ],
            "device_totals": [
                {"start": "2026-08-20T10:00:00+02:00", "screen_on_ms": 60000, "unlock_count": 4},
                {"start": "2026-08-20T23:00:00+02:00", "screen_on_ms": 15000, "unlock_count": 1}
            ]
        }"#;
        let detail = day_detail(json).unwrap();
        assert_eq!(detail.total_ms, 150_000);
        assert_eq!(detail.unlock_count, 5);
        assert_eq!(detail.hours.len(), 24);
        assert_eq!(detail.hours[10], 60_000);
        assert_eq!(
            detail.hours[23], 15_000,
            "a late hour is the whole point of the ribbon"
        );
        assert_eq!(detail.apps[0].label, "B", "ranked by time, not by name");
    }

    #[test]
    fn an_unparseable_timestamp_is_skipped_not_counted_as_midnight() {
        let json = r#"{
            "child_id": "1", "from": "2026-08-20", "to": "2026-08-20",
            "bucket": "hour", "tz": "Europe/Zurich", "devices": [], "series": [],
            "device_totals": [{"start": "not a timestamp", "screen_on_ms": 60000, "unlock_count": 1}]
        }"#;
        let detail = day_detail(json).unwrap();
        assert_eq!(
            detail.hours[0], 0,
            "phantom midnight usage is the bug the ribbon exists to reveal"
        );
    }
}
