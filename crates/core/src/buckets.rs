use crate::error::CoreError;
use crate::sessions::Session;
use chrono::{DateTime, Duration, TimeZone, Timelike, Utc};
use chrono_tz::Tz;
use std::collections::BTreeMap;

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct AppBucket {
    pub package: String,
    pub hour_start: DateTime<Utc>,
    pub foreground_ms: i64,
    pub launch_count: i32,
    /// Media playing with the screen off. A separate measure: never summed
    /// into `foreground_ms`, and never into `DeviceBucket::screen_on_ms`.
    pub background_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct DeviceBucket {
    pub hour_start: DateTime<Utc>,
    pub screen_on_ms: i64,
    pub unlock_count: i32,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct Buckets {
    pub tz: String,
    pub apps: Vec<AppBucket>,
    pub device: Vec<DeviceBucket>,
}

/// Start of the local hour containing `at`, expressed as a UTC instant.
///
/// Local rather than UTC hours: zones like Asia/Kolkata (+05:30) and
/// Asia/Kathmandu (+05:45) are not whole-hour offsets, so UTC-hour buckets can
/// never be re-cut into correct local hours after the fact.
fn local_hour_start(at: DateTime<Utc>, tz: Tz) -> DateTime<Utc> {
    let local = at.with_timezone(&tz);
    let naive = local
        .date_naive()
        .and_hms_opt(local.hour(), 0, 0)
        .expect("hour 0-23 with 0 minutes is always a valid time");
    match tz.from_local_datetime(&naive).earliest() {
        Some(dt) => dt.with_timezone(&Utc),
        // The local hour start falls inside a DST gap (a transition partway
        // through the hour). Fall back to the instant itself: no time is
        // created or destroyed.
        None => at,
    }
}

fn next_local_hour(from: DateTime<Utc>, tz: Tz) -> DateTime<Utc> {
    let candidate = from + Duration::hours(1);
    // A DST shift can move the local hour boundary; re-truncate to be safe.
    let truncated = local_hour_start(candidate, tz);
    if truncated > from {
        truncated
    } else {
        candidate
    }
}

/// Walk a session hour by local hour. Shared by foreground and background so
/// the two can never drift on DST or half-hour zones.
fn slice_hours(session: &Session, zone: Tz, mut on_slice: impl FnMut(DateTime<Utc>, i64, bool)) {
    let mut cursor = session.start;
    let mut first_slice = true;
    while cursor < session.end {
        let hour_start = local_hour_start(cursor, zone);
        let hour_end = next_local_hour(hour_start, zone);
        let slice_end = session.end.min(hour_end);
        on_slice(
            hour_start,
            (slice_end - cursor).num_milliseconds(),
            first_slice,
        );
        first_slice = false;
        cursor = slice_end;
    }
}

pub fn bucket_hours(
    sessions: &[Session],
    background: &[Session],
    unlocks: &[DateTime<Utc>],
    tz: &str,
) -> Result<Buckets, CoreError> {
    let zone: Tz = tz
        .parse()
        .map_err(|_| CoreError::UnknownTimezone(tz.to_string()))?;

    let mut apps: BTreeMap<(DateTime<Utc>, String), AppBucket> = BTreeMap::new();
    let mut device: BTreeMap<DateTime<Utc>, DeviceBucket> = BTreeMap::new();

    for session in sessions {
        slice_hours(session, zone, |hour_start, ms, first_slice| {
            let entry = apps
                .entry((hour_start, session.package.clone()))
                .or_insert_with(|| AppBucket {
                    package: session.package.clone(),
                    hour_start,
                    foreground_ms: 0,
                    launch_count: 0,
                    background_ms: 0,
                });
            entry.foreground_ms += ms;
            if first_slice {
                entry.launch_count += 1;
            }

            device
                .entry(hour_start)
                .or_insert_with(|| DeviceBucket {
                    hour_start,
                    screen_on_ms: 0,
                    unlock_count: 0,
                })
                .screen_on_ms += ms;
        });
    }

    for session in background {
        slice_hours(session, zone, |hour_start, ms, _first_slice| {
            apps.entry((hour_start, session.package.clone()))
                .or_insert_with(|| AppBucket {
                    package: session.package.clone(),
                    hour_start,
                    foreground_ms: 0,
                    launch_count: 0,
                    background_ms: 0,
                })
                .background_ms += ms;

            // The hour has to exist even with nothing else in it: bucket_sessions
            // builds one pending hour per device row, so without this a night of
            // listening is never sent at all.
            device.entry(hour_start).or_insert_with(|| DeviceBucket {
                hour_start,
                screen_on_ms: 0,
                unlock_count: 0,
            });
        });
    }

    for unlock in unlocks {
        let hour_start = local_hour_start(*unlock, zone);
        device
            .entry(hour_start)
            .or_insert_with(|| DeviceBucket {
                hour_start,
                screen_on_ms: 0,
                unlock_count: 0,
            })
            .unlock_count += 1;
    }

    Ok(Buckets {
        tz: tz.to_string(),
        apps: apps.into_values().collect(),
        device: device.into_values().collect(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::sessions::Session;
    use chrono::TimeZone;

    fn utc(y: i32, mo: u32, d: u32, h: u32, mi: u32) -> DateTime<Utc> {
        Utc.with_ymd_and_hms(y, mo, d, h, mi, 0).unwrap()
    }

    fn session(p: &str, start: DateTime<Utc>, end: DateTime<Utc>) -> Session {
        Session {
            package: p.into(),
            start,
            end,
        }
    }

    #[test]
    fn background_time_is_bucketed_separately_from_foreground() {
        let fg = [session(
            "com.a",
            utc(2026, 8, 21, 21, 0),
            utc(2026, 8, 21, 21, 10),
        )];
        let bg = [session(
            "com.abs",
            utc(2026, 8, 21, 21, 20),
            utc(2026, 8, 21, 21, 50),
        )];
        let b = bucket_hours(&fg, &bg, &[], "Europe/Zurich").unwrap();

        let a = b.apps.iter().find(|a| a.package == "com.a").unwrap();
        assert_eq!(a.foreground_ms, 10 * 60 * 1000);
        assert_eq!(a.background_ms, 0);

        let abs = b.apps.iter().find(|a| a.package == "com.abs").unwrap();
        assert_eq!(abs.background_ms, 30 * 60 * 1000);
        assert_eq!(
            abs.foreground_ms, 0,
            "background time is never foreground time"
        );
        assert_eq!(abs.launch_count, 0, "a background stretch is not a launch");
    }

    #[test]
    fn background_time_never_reaches_screen_on_ms() {
        let bg = [session(
            "com.abs",
            utc(2026, 8, 21, 22, 0),
            utc(2026, 8, 21, 22, 30),
        )];
        let b = bucket_hours(&[], &bg, &[], "Europe/Zurich").unwrap();
        assert_eq!(b.device.len(), 1);
        assert_eq!(b.device[0].screen_on_ms, 0);
        assert_eq!(b.device[0].unlock_count, 0);
    }

    #[test]
    fn a_background_only_hour_still_produces_a_device_row() {
        // Otherwise the whole hour is dropped: bucket_sessions builds one
        // pending hour per device row, and a sleeping child has no other
        // events at all.
        let bg = [session(
            "com.abs",
            utc(2026, 8, 21, 23, 10),
            utc(2026, 8, 21, 23, 40),
        )];
        let b = bucket_hours(&[], &bg, &[], "Europe/Zurich").unwrap();
        assert_eq!(b.device.len(), 1, "the hour must exist to be sent at all");
        assert_eq!(b.apps.len(), 1);
    }

    #[test]
    fn a_background_stretch_spanning_hours_splits_on_the_local_hour() {
        let bg = [session(
            "com.abs",
            utc(2026, 8, 21, 20, 50),
            utc(2026, 8, 21, 22, 10),
        )];
        let b = bucket_hours(&[], &bg, &[], "Europe/Zurich").unwrap();
        assert_eq!(b.apps.len(), 3);
        assert_eq!(b.apps[0].background_ms, 10 * 60 * 1000);
        assert_eq!(b.apps[1].background_ms, 60 * 60 * 1000);
        assert_eq!(b.apps[2].background_ms, 10 * 60 * 1000);
        let launches: i32 = b.apps.iter().map(|a| a.launch_count).sum();
        assert_eq!(launches, 0);
    }

    #[test]
    fn one_session_inside_one_hour() {
        let s = [session(
            "com.a",
            utc(2026, 8, 21, 12, 10),
            utc(2026, 8, 21, 12, 25),
        )];
        let b = bucket_hours(&s, &[], &[], "Europe/Zurich").unwrap();
        assert_eq!(b.apps.len(), 1);
        assert_eq!(b.apps[0].hour_start, utc(2026, 8, 21, 12, 0));
        assert_eq!(b.apps[0].foreground_ms, 15 * 60 * 1000);
        assert_eq!(b.apps[0].launch_count, 1);
    }

    #[test]
    fn session_spanning_hours_splits_but_counts_one_launch() {
        let s = [session(
            "com.a",
            utc(2026, 8, 21, 12, 50),
            utc(2026, 8, 21, 14, 10),
        )];
        let b = bucket_hours(&s, &[], &[], "Europe/Zurich").unwrap();
        assert_eq!(b.apps.len(), 3);
        assert_eq!(b.apps[0].foreground_ms, 10 * 60 * 1000);
        assert_eq!(b.apps[1].foreground_ms, 60 * 60 * 1000);
        assert_eq!(b.apps[2].foreground_ms, 10 * 60 * 1000);
        let launches: i32 = b.apps.iter().map(|a| a.launch_count).sum();
        assert_eq!(launches, 1, "one session is one launch, not one per hour");
        assert_eq!(
            b.apps[0].launch_count, 1,
            "attributed to the hour it started in"
        );
    }

    #[test]
    fn buckets_align_to_local_hours_for_a_half_hour_offset_zone() {
        // Asia/Kolkata is UTC+05:30, so a local hour starts at :30 past a UTC hour.
        let s = [session(
            "com.a",
            utc(2026, 8, 21, 12, 40),
            utc(2026, 8, 21, 12, 50),
        )];
        let b = bucket_hours(&s, &[], &[], "Asia/Kolkata").unwrap();
        assert_eq!(b.apps[0].hour_start, utc(2026, 8, 21, 12, 30));
    }

    #[test]
    fn dst_spring_forward_does_not_produce_a_phantom_hour() {
        // Europe/Zurich 2026-03-29: 02:00 local jumps to 03:00 local.
        // 00:50 UTC -> 01:50 CET, 01:10 UTC -> 03:10 CEST.
        let s = [session(
            "com.a",
            utc(2026, 3, 29, 0, 50),
            utc(2026, 3, 29, 1, 10),
        )];
        let b = bucket_hours(&s, &[], &[], "Europe/Zurich").unwrap();
        let total: i64 = b.apps.iter().map(|a| a.foreground_ms).sum();
        assert_eq!(
            total,
            20 * 60 * 1000,
            "wall-clock jump must not invent or destroy time"
        );
        assert_eq!(b.apps.len(), 2, "01:00 CET bucket and 03:00 CEST bucket");
    }

    #[test]
    fn unlocks_land_in_their_local_hour() {
        let b = bucket_hours(
            &[],
            &[],
            &[utc(2026, 8, 21, 12, 5), utc(2026, 8, 21, 12, 40)],
            "Europe/Zurich",
        )
        .unwrap();
        assert_eq!(b.device.len(), 1);
        assert_eq!(b.device[0].unlock_count, 2);
    }

    #[test]
    fn device_screen_on_is_the_sum_of_foreground_time_in_that_hour() {
        let s = [
            session("com.a", utc(2026, 8, 21, 12, 0), utc(2026, 8, 21, 12, 10)),
            session("com.b", utc(2026, 8, 21, 12, 10), utc(2026, 8, 21, 12, 15)),
        ];
        let b = bucket_hours(&s, &[], &[], "Europe/Zurich").unwrap();
        assert_eq!(b.device[0].screen_on_ms, 15 * 60 * 1000);
    }

    #[test]
    fn same_package_twice_in_an_hour_merges_with_two_launches() {
        let s = [
            session("com.a", utc(2026, 8, 21, 12, 0), utc(2026, 8, 21, 12, 5)),
            session("com.a", utc(2026, 8, 21, 12, 30), utc(2026, 8, 21, 12, 35)),
        ];
        let b = bucket_hours(&s, &[], &[], "Europe/Zurich").unwrap();
        assert_eq!(b.apps.len(), 1);
        assert_eq!(b.apps[0].foreground_ms, 10 * 60 * 1000);
        assert_eq!(b.apps[0].launch_count, 2);
    }

    #[test]
    fn unknown_timezone_is_an_error_not_a_silent_utc_fallback() {
        assert_eq!(
            bucket_hours(&[], &[], &[], "Mars/Olympus").unwrap_err(),
            CoreError::UnknownTimezone("Mars/Olympus".into())
        );
    }
}
