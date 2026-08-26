use crate::wire::{IngestHour, IngestResponse};
use chrono::{DateTime, Utc};
use std::collections::HashSet;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SyncPlan {
    pub send: Vec<IngestHour>,
    pub deferred: Vec<IngestHour>,
}

/// Choose which pending hours go in the next request. Oldest first, so a long
/// offline stretch drains in chronological order and a partial success is still
/// a contiguous prefix of history.
pub fn plan_sync(pending: &[IngestHour], max_rows: usize, max_bytes: usize) -> SyncPlan {
    let mut sorted = pending.to_vec();
    sorted.sort_by_key(|h| h.hour_start);

    let mut send = Vec::new();
    let mut deferred = Vec::new();
    let mut bytes = 0usize;

    for hour in sorted {
        let size = serde_json::to_vec(&hour).map(|v| v.len()).unwrap_or(0);
        // `send.is_empty()` keeps one oversized row from wedging the queue.
        let fits = send.len() < max_rows && (bytes + size <= max_bytes || send.is_empty());
        if fits {
            bytes += size;
            send.push(hour);
        } else {
            deferred.push(hour);
        }
    }

    SyncPlan { send, deferred }
}

/// Apply a server response to the queue: drop what landed, drop what will never
/// land, keep everything else.
pub fn apply_result(pending: Vec<IngestHour>, response: &IngestResponse) -> Vec<IngestHour> {
    let accepted: HashSet<DateTime<Utc>> = response.accepted.iter().copied().collect();
    let permanent: HashSet<DateTime<Utc>> = response
        .rejected
        .iter()
        .filter(|r| r.permanent)
        .map(|r| r.hour_start)
        .collect();

    pending
        .into_iter()
        .filter(|h| !accepted.contains(&h.hour_start) && !permanent.contains(&h.hour_start))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::wire::{IngestApp, IngestHour, IngestResponse, Rejected};
    use chrono::{TimeZone, Timelike};

    fn hour(h: u32, apps: usize) -> IngestHour {
        IngestHour {
            hour_start: Utc.with_ymd_and_hms(2026, 8, 21, h, 0, 0).unwrap(),
            tz: "Europe/Zurich".into(),
            computed_at: Utc.with_ymd_and_hms(2026, 8, 21, 23, 0, 0).unwrap(),
            screen_on_ms: 1000,
            unlock_count: 1,
            background_measured: false,
            apps: (0..apps)
                .map(|i| IngestApp {
                    package: format!("com.a{i}"),
                    label: format!("App {i}"),
                    foreground_ms: 1000,
                    launch_count: 1,
                    background_ms: 0,
                })
                .collect(),
        }
    }

    #[test]
    fn sends_everything_when_under_the_caps() {
        let plan = plan_sync(&[hour(10, 1), hour(11, 1)], 500, 1_000_000);
        assert_eq!(plan.send.len(), 2);
        assert!(plan.deferred.is_empty());
    }

    #[test]
    fn defers_beyond_the_row_cap_oldest_first() {
        let plan = plan_sync(&[hour(12, 1), hour(10, 1), hour(11, 1)], 2, 1_000_000);
        assert_eq!(plan.send.len(), 2);
        assert_eq!(plan.send[0].hour_start.hour(), 10, "oldest data goes first");
        assert_eq!(plan.send[1].hour_start.hour(), 11);
        assert_eq!(plan.deferred.len(), 1);
        assert_eq!(plan.deferred[0].hour_start.hour(), 12);
    }

    #[test]
    fn defers_beyond_the_byte_cap() {
        let plan = plan_sync(&[hour(10, 50), hour(11, 50)], 500, 400);
        assert_eq!(plan.send.len(), 1, "byte cap must split the batch");
        assert_eq!(plan.deferred.len(), 1);
    }

    #[test]
    fn always_sends_at_least_one_row_even_if_it_exceeds_the_byte_cap() {
        // Otherwise one oversized row wedges the queue forever.
        let plan = plan_sync(&[hour(10, 500)], 500, 10);
        assert_eq!(plan.send.len(), 1);
    }

    #[test]
    fn accepted_rows_are_dropped() {
        let pending = vec![hour(10, 1), hour(11, 1)];
        let response = IngestResponse {
            accepted: vec![pending[0].hour_start],
            rejected: vec![],
        };
        let left = apply_result(pending, &response);
        assert_eq!(left.len(), 1);
        assert_eq!(left[0].hour_start.hour(), 11);
    }

    #[test]
    fn permanently_rejected_rows_are_dropped_to_avoid_an_infinite_retry() {
        let pending = vec![hour(10, 1)];
        let response = IngestResponse {
            accepted: vec![],
            rejected: vec![Rejected {
                hour_start: pending[0].hour_start,
                reason: "hour_start in the future".into(),
                permanent: true,
            }],
        };
        assert!(apply_result(pending, &response).is_empty());
    }

    #[test]
    fn transiently_rejected_rows_are_kept() {
        let pending = vec![hour(10, 1)];
        let response = IngestResponse {
            accepted: vec![],
            rejected: vec![Rejected {
                hour_start: pending[0].hour_start,
                reason: "database unavailable".into(),
                permanent: false,
            }],
        };
        assert_eq!(apply_result(pending, &response).len(), 1);
    }

    #[test]
    fn rows_the_server_did_not_mention_are_kept() {
        let pending = vec![hour(10, 1), hour(11, 1)];
        let response = IngestResponse {
            accepted: vec![],
            rejected: vec![],
        };
        assert_eq!(apply_result(pending, &response).len(), 2);
    }
}
