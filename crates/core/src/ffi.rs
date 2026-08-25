//! The Android/iOS boundary. UniFFI cannot carry `chrono::DateTime`, so every
//! instant crossing here is `i64` epoch milliseconds, converted in this file and
//! nowhere else. Keep this module a translator: rules belong in the modules it
//! calls.

use crate::buckets::bucket_hours;
use crate::error::CoreError;
use crate::events::{EventKind, RawEvent};
use crate::queue::{apply_result, plan_sync};
use crate::selfusage::{day_detail, day_strip};
use crate::sessions::{OpenApp, Session, stitch};
use crate::wire::{IngestApp, IngestHour, IngestRequest, IngestResponse, SCHEMA_VERSION};
use chrono::{DateTime, TimeZone, Utc};
use std::collections::HashMap;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiError {
    #[error("unknown timezone: {tz}")]
    UnknownTimezone { tz: String },
    #[error("malformed json: {detail}")]
    BadJson { detail: String },
}

impl From<CoreError> for FfiError {
    fn from(err: CoreError) -> Self {
        match err {
            CoreError::UnknownTimezone(tz) => FfiError::UnknownTimezone { tz },
            CoreError::BadJson(detail) => FfiError::BadJson { detail },
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum EventKindFfi {
    Resumed { package: String },
    Paused { package: String },
    ScreenOff,
    Unlock,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct RawEventFfi {
    pub at_millis: i64,
    pub kind: EventKindFfi,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct OpenAppFfi {
    pub package: String,
    pub since_millis: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SessionFfi {
    pub package: String,
    pub start_millis: i64,
    pub end_millis: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct StitchOutcome {
    pub closed: Vec<SessionFfi>,
    pub open: Option<OpenAppFfi>,
    pub unlock_millis: Vec<i64>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct PendingAppFfi {
    pub package: String,
    pub label: String,
    pub foreground_ms: i64,
    pub launch_count: i32,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct PendingHourFfi {
    pub hour_start_millis: i64,
    pub tz: String,
    pub computed_at_millis: i64,
    pub screen_on_ms: i64,
    pub unlock_count: i32,
    pub apps: Vec<PendingAppFfi>,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SyncPlanFfi {
    pub send: Vec<PendingHourFfi>,
    pub deferred: Vec<PendingHourFfi>,
}

fn to_utc(millis: i64) -> DateTime<Utc> {
    Utc.timestamp_millis_opt(millis)
        .single()
        .unwrap_or_else(|| DateTime::from_timestamp_nanos(0))
}

#[uniffi::export]
pub fn stitch_events(
    prev_open: Option<OpenAppFfi>,
    events: Vec<RawEventFfi>,
    window_end_millis: i64,
) -> StitchOutcome {
    let prev = prev_open.map(|o| OpenApp {
        package: o.package,
        since: to_utc(o.since_millis),
    });
    let mapped: Vec<RawEvent> = events
        .into_iter()
        .map(|e| RawEvent {
            at: to_utc(e.at_millis),
            kind: match e.kind {
                EventKindFfi::Resumed { package } => EventKind::Resumed { package },
                EventKindFfi::Paused { package } => EventKind::Paused { package },
                EventKindFfi::ScreenOff => EventKind::ScreenOff,
                EventKindFfi::Unlock => EventKind::Unlock,
            },
        })
        .collect();

    let result = stitch(prev, None, &mapped, to_utc(window_end_millis));
    StitchOutcome {
        closed: result
            .sessions
            .into_iter()
            .map(|s| SessionFfi {
                package: s.package,
                start_millis: s.start.timestamp_millis(),
                end_millis: s.end.timestamp_millis(),
            })
            .collect(),
        open: result.open.map(|o| OpenAppFfi {
            package: o.package,
            since_millis: o.since.timestamp_millis(),
        }),
        unlock_millis: result
            .unlocks
            .iter()
            .map(|u| u.timestamp_millis())
            .collect(),
    }
}

#[uniffi::export]
pub fn bucket_sessions(
    sessions: Vec<SessionFfi>,
    unlock_millis: Vec<i64>,
    tz: String,
    labels: HashMap<String, String>,
    computed_at_millis: i64,
) -> Result<Vec<PendingHourFfi>, FfiError> {
    let mapped: Vec<Session> = sessions
        .into_iter()
        .map(|s| Session {
            package: s.package,
            start: to_utc(s.start_millis),
            end: to_utc(s.end_millis),
        })
        .collect();
    let unlocks: Vec<DateTime<Utc>> = unlock_millis.into_iter().map(to_utc).collect();

    let buckets = bucket_hours(&mapped, &[], &unlocks, &tz)?;

    // One PendingHourFfi per hour: join the per-app rows with the device row.
    let mut out: Vec<PendingHourFfi> = Vec::new();
    for device in &buckets.device {
        let apps: Vec<PendingAppFfi> = buckets
            .apps
            .iter()
            .filter(|a| a.hour_start == device.hour_start)
            .map(|a| PendingAppFfi {
                package: a.package.clone(),
                // The server cannot know that com.zhiliaoapp.musically is
                // TikTok; PackageManager can, so the label rides along.
                label: labels
                    .get(&a.package)
                    .cloned()
                    .unwrap_or_else(|| a.package.clone()),
                foreground_ms: a.foreground_ms,
                launch_count: a.launch_count,
            })
            .collect();

        out.push(PendingHourFfi {
            hour_start_millis: device.hour_start.timestamp_millis(),
            tz: buckets.tz.clone(),
            computed_at_millis,
            screen_on_ms: device.screen_on_ms,
            unlock_count: device.unlock_count,
            apps,
        });
    }
    Ok(out)
}

fn to_wire(hour: &PendingHourFfi) -> IngestHour {
    IngestHour {
        hour_start: to_utc(hour.hour_start_millis),
        tz: hour.tz.clone(),
        computed_at: to_utc(hour.computed_at_millis),
        screen_on_ms: hour.screen_on_ms,
        unlock_count: hour.unlock_count,
        background_measured: false,
        apps: hour
            .apps
            .iter()
            .map(|a| IngestApp {
                package: a.package.clone(),
                label: a.label.clone(),
                foreground_ms: a.foreground_ms,
                launch_count: a.launch_count,
                background_ms: 0,
            })
            .collect(),
    }
}

#[uniffi::export]
pub fn ingest_body(
    hours: Vec<PendingHourFfi>,
    device_time_millis: i64,
) -> Result<String, FfiError> {
    let request = IngestRequest {
        schema: SCHEMA_VERSION,
        device_time: to_utc(device_time_millis),
        hours: hours.iter().map(to_wire).collect(),
    };
    serde_json::to_string(&request).map_err(|e| FfiError::BadJson {
        detail: e.to_string(),
    })
}

#[uniffi::export]
pub fn plan_next_sync(pending: Vec<PendingHourFfi>, max_rows: u32, max_bytes: u32) -> SyncPlanFfi {
    let wire: Vec<IngestHour> = pending.iter().map(to_wire).collect();
    let plan = plan_sync(&wire, max_rows as usize, max_bytes as usize);

    // Map back by hour_start: the plan reorders and splits, it never mutates.
    let pick = |chosen: &[IngestHour]| -> Vec<PendingHourFfi> {
        chosen
            .iter()
            .filter_map(|h| {
                pending
                    .iter()
                    .find(|p| p.hour_start_millis == h.hour_start.timestamp_millis())
                    .cloned()
            })
            .collect()
    };

    SyncPlanFfi {
        send: pick(&plan.send),
        deferred: pick(&plan.deferred),
    }
}

#[uniffi::export]
pub fn apply_ingest_result(
    pending: Vec<PendingHourFfi>,
    response_json: String,
) -> Result<Vec<PendingHourFfi>, FfiError> {
    // Deliberately fails loudly on garbage. A captcha page or proxy error must
    // not read as "everything accepted" and delete a child's day.
    let response: IngestResponse =
        serde_json::from_str(&response_json).map_err(|e| FfiError::BadJson {
            detail: e.to_string(),
        })?;

    let wire: Vec<IngestHour> = pending.iter().map(to_wire).collect();
    let kept = apply_result(wire, &response);
    let keep_millis: Vec<i64> = kept
        .iter()
        .map(|h| h.hour_start.timestamp_millis())
        .collect();

    Ok(pending
        .into_iter()
        .filter(|p| keep_millis.contains(&p.hour_start_millis))
        .collect())
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct DayTotalFfi {
    pub day: String,
    pub foreground_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct AppTotalFfi {
    pub package: String,
    pub label: String,
    pub foreground_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct DayDetailFfi {
    pub total_ms: i64,
    pub unlock_count: i32,
    pub hours: Vec<i64>,
    pub apps: Vec<AppTotalFfi>,
}

#[uniffi::export]
pub fn parse_day_strip(json: String) -> Result<Vec<DayTotalFfi>, FfiError> {
    Ok(day_strip(&json)?
        .into_iter()
        .map(|d| DayTotalFfi {
            day: d.day,
            foreground_ms: d.foreground_ms,
        })
        .collect())
}

#[uniffi::export]
pub fn parse_day_detail(json: String) -> Result<DayDetailFfi, FfiError> {
    let detail = day_detail(&json)?;
    Ok(DayDetailFfi {
        total_ms: detail.total_ms,
        unlock_count: detail.unlock_count,
        hours: detail.hours,
        apps: detail
            .apps
            .into_iter()
            .map(|a| AppTotalFfi {
                package: a.package,
                label: a.label,
                foreground_ms: a.foreground_ms,
            })
            .collect(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    const HOUR: i64 = 3_600_000;
    // 2026-08-21T12:00:00Z
    const NOON: i64 = 1_787_313_600_000;

    fn resumed(at: i64, package: &str) -> RawEventFfi {
        RawEventFfi {
            at_millis: at,
            kind: EventKindFfi::Resumed {
                package: package.into(),
            },
        }
    }

    fn paused(at: i64, package: &str) -> RawEventFfi {
        RawEventFfi {
            at_millis: at,
            kind: EventKindFfi::Paused {
                package: package.into(),
            },
        }
    }

    fn hour(h: i64) -> PendingHourFfi {
        PendingHourFfi {
            hour_start_millis: h,
            tz: "UTC".into(),
            computed_at_millis: h,
            screen_on_ms: 0,
            unlock_count: 0,
            apps: vec![],
        }
    }

    #[test]
    fn stitch_round_trips_through_millis() {
        let out = stitch_events(
            None,
            vec![resumed(NOON, "com.a"), paused(NOON + 300_000, "com.a")],
            NOON + HOUR,
        );
        assert_eq!(out.closed.len(), 1);
        assert_eq!(out.closed[0].start_millis, NOON);
        assert_eq!(out.closed[0].end_millis, NOON + 300_000);
        assert_eq!(out.open, None);
    }

    #[test]
    fn carry_over_survives_the_ffi_boundary() {
        let first = stitch_events(None, vec![resumed(NOON, "com.a")], NOON + 1_800_000);
        let open = first.open.clone().expect("app still in foreground");
        assert_eq!(open.since_millis, NOON);

        let second = stitch_events(
            Some(open),
            vec![paused(NOON + HOUR, "com.a")],
            NOON + HOUR + 60_000,
        );
        assert_eq!(
            second.closed[0].end_millis - second.closed[0].start_millis,
            HOUR
        );
    }

    #[test]
    fn bucket_sessions_labels_apps_and_keeps_local_hours() {
        let sessions = vec![SessionFfi {
            package: "com.a".into(),
            start_millis: NOON,
            end_millis: NOON + 600_000,
        }];
        let labels = HashMap::from([("com.a".to_string(), "App A".to_string())]);
        let hours = bucket_sessions(
            sessions,
            vec![NOON],
            "Europe/Zurich".into(),
            labels,
            NOON + HOUR,
        )
        .unwrap();

        assert_eq!(hours.len(), 1);
        assert_eq!(hours[0].tz, "Europe/Zurich");
        assert_eq!(hours[0].computed_at_millis, NOON + HOUR);
        assert_eq!(hours[0].unlock_count, 1);
        assert_eq!(
            hours[0].apps[0].label, "App A",
            "label must come from the device"
        );
        assert_eq!(hours[0].apps[0].foreground_ms, 600_000);
    }

    #[test]
    fn an_unlabelled_package_falls_back_to_its_id() {
        let sessions = vec![SessionFfi {
            package: "com.unknown".into(),
            start_millis: NOON,
            end_millis: NOON + 1000,
        }];
        let hours = bucket_sessions(sessions, vec![], "UTC".into(), HashMap::new(), NOON).unwrap();
        assert_eq!(hours[0].apps[0].label, "com.unknown");
    }

    #[test]
    fn an_unknown_timezone_is_an_ffi_error() {
        let err = bucket_sessions(vec![], vec![], "Mars/Olympus".into(), HashMap::new(), NOON)
            .unwrap_err();
        assert!(matches!(err, FfiError::UnknownTimezone { .. }));
    }

    #[test]
    fn ingest_body_is_the_wire_format_the_server_accepts() {
        let h = PendingHourFfi {
            hour_start_millis: NOON,
            tz: "Europe/Zurich".into(),
            computed_at_millis: NOON + HOUR,
            screen_on_ms: 1000,
            unlock_count: 2,
            apps: vec![PendingAppFfi {
                package: "com.a".into(),
                label: "App A".into(),
                foreground_ms: 1000,
                launch_count: 1,
            }],
        };
        let body = ingest_body(vec![h], NOON + HOUR).unwrap();
        let parsed: serde_json::Value = serde_json::from_str(&body).unwrap();

        assert_eq!(parsed["schema"], 1);
        assert_eq!(parsed["hours"][0]["tz"], "Europe/Zurich");
        assert_eq!(parsed["hours"][0]["apps"][0]["package"], "com.a");
        // RFC3339, not millis: this is what the server's serde expects.
        assert!(
            parsed["hours"][0]["hour_start"]
                .as_str()
                .unwrap()
                .ends_with("Z")
        );
    }

    #[test]
    fn apply_ingest_result_drops_accepted_and_permanently_rejected() {
        let pending = vec![hour(NOON), hour(NOON + HOUR), hour(NOON + 2 * HOUR)];
        let response = r#"{
            "accepted": ["2026-08-21T12:00:00Z"],
            "rejected": [
                {"hour_start":"2026-08-21T13:00:00Z","reason":"future","permanent":true},
                {"hour_start":"2026-08-21T14:00:00Z","reason":"db down","permanent":false}
            ]
        }"#;
        let left = apply_ingest_result(pending, response.into()).unwrap();
        assert_eq!(left.len(), 1);
        assert_eq!(
            left[0].hour_start_millis,
            NOON + 2 * HOUR,
            "transient rejection stays queued"
        );
    }

    #[test]
    fn a_malformed_server_response_is_an_error_not_a_wiped_queue() {
        let pending = vec![hour(NOON)];
        assert!(apply_ingest_result(pending, "<html>captcha</html>".into()).is_err());
    }

    #[test]
    fn plan_next_sync_respects_the_row_cap_oldest_first() {
        let plan = plan_next_sync(vec![hour(NOON + HOUR), hour(NOON)], 1, 1_000_000);
        assert_eq!(plan.send.len(), 1);
        assert_eq!(plan.send[0].hour_start_millis, NOON);
        assert_eq!(plan.deferred.len(), 1);
    }
}
