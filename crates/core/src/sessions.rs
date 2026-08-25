use crate::events::{EventKind, RawEvent};
use chrono::{DateTime, Utc};

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct OpenApp {
    pub package: String,
    pub since: DateTime<Utc>,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct Session {
    pub package: String,
    pub start: DateTime<Utc>,
    pub end: DateTime<Utc>,
}

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct StitchResult {
    pub sessions: Vec<Session>,
    pub open: Option<OpenApp>,
    pub unlocks: Vec<DateTime<Utc>>,
}

/// Turn a stream of foreground transitions into closed sessions.
///
/// `prev_open` is the app still in the foreground when the previous window
/// ended; the returned `open` must be persisted and fed into the next call, or
/// usage is lost on every sync.
pub fn stitch(
    prev_open: Option<OpenApp>,
    events: &[RawEvent],
    window_end: DateTime<Utc>,
) -> StitchResult {
    let mut sorted: Vec<&RawEvent> = events.iter().filter(|e| e.at <= window_end).collect();
    sorted.sort_by_key(|e| e.at);

    let mut out = StitchResult {
        open: prev_open,
        ..Default::default()
    };

    for event in sorted {
        match &event.kind {
            EventKind::Resumed { package } => {
                close(&mut out, event.at);
                out.open = Some(OpenApp {
                    package: package.clone(),
                    since: event.at,
                });
            }
            EventKind::Paused { package } => {
                if out.open.as_ref().is_some_and(|o| &o.package == package) {
                    close(&mut out, event.at);
                }
            }
            EventKind::ScreenOff => close(&mut out, event.at),
            EventKind::ScreenOn => {}
            EventKind::Unlock => out.unlocks.push(event.at),
        }
    }

    out
}

fn close(out: &mut StitchResult, at: DateTime<Utc>) {
    if let Some(open) = out.open.take()
        && at > open.since
    {
        out.sessions.push(Session {
            package: open.package,
            start: open.since,
            end: at,
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::events::{EventKind, RawEvent};
    use chrono::TimeZone;

    fn t(h: u32, m: u32) -> DateTime<Utc> {
        Utc.with_ymd_and_hms(2026, 8, 21, h, m, 0).unwrap()
    }

    fn resumed(h: u32, m: u32, p: &str) -> RawEvent {
        RawEvent {
            at: t(h, m),
            kind: EventKind::Resumed { package: p.into() },
        }
    }

    fn paused(h: u32, m: u32, p: &str) -> RawEvent {
        RawEvent {
            at: t(h, m),
            kind: EventKind::Paused { package: p.into() },
        }
    }

    #[test]
    fn pairs_resumed_with_paused() {
        let r = stitch(
            None,
            &[resumed(10, 0, "com.a"), paused(10, 5, "com.a")],
            t(11, 0),
        );
        assert_eq!(
            r.sessions,
            vec![Session {
                package: "com.a".into(),
                start: t(10, 0),
                end: t(10, 5)
            }]
        );
        assert_eq!(r.open, None);
    }

    #[test]
    fn switching_apps_closes_the_previous_session() {
        // Android does not always emit PAUSED before the next RESUMED.
        let r = stitch(
            None,
            &[resumed(10, 0, "com.a"), resumed(10, 7, "com.b")],
            t(11, 0),
        );
        assert_eq!(
            r.sessions,
            vec![Session {
                package: "com.a".into(),
                start: t(10, 0),
                end: t(10, 7)
            }]
        );
        assert_eq!(
            r.open,
            Some(OpenApp {
                package: "com.b".into(),
                since: t(10, 7)
            })
        );
    }

    #[test]
    fn carries_the_open_app_across_windows_without_losing_time() {
        let first = stitch(None, &[resumed(10, 0, "com.a")], t(10, 30));
        assert_eq!(first.sessions, vec![]);
        assert_eq!(
            first.open,
            Some(OpenApp {
                package: "com.a".into(),
                since: t(10, 0)
            })
        );

        // Next window contains no RESUMED at all - the app never left the foreground.
        let second = stitch(first.open, &[paused(10, 50, "com.a")], t(11, 0));
        assert_eq!(
            second.sessions,
            vec![Session {
                package: "com.a".into(),
                start: t(10, 0),
                end: t(10, 50)
            }],
            "time between windows must not be lost"
        );
        assert_eq!(second.open, None);
    }

    #[test]
    fn screen_off_closes_the_open_session() {
        let r = stitch(
            None,
            &[
                resumed(10, 0, "com.a"),
                RawEvent {
                    at: t(10, 3),
                    kind: EventKind::ScreenOff,
                },
            ],
            t(11, 0),
        );
        assert_eq!(
            r.sessions,
            vec![Session {
                package: "com.a".into(),
                start: t(10, 0),
                end: t(10, 3)
            }]
        );
        assert_eq!(r.open, None);
    }

    #[test]
    fn screen_on_does_not_change_foreground_totals_or_unlocks() {
        // ScreenOn exists for background listening only. If it ever closes a
        // foreground session, every screen-time number in the product shifts.
        let without = stitch(
            None,
            &[resumed(10, 0, "com.a"), paused(10, 5, "com.a")],
            t(11, 0),
        );
        let with_on = stitch(
            None,
            &[
                resumed(10, 0, "com.a"),
                RawEvent {
                    at: t(10, 2),
                    kind: EventKind::ScreenOn,
                },
                paused(10, 5, "com.a"),
            ],
            t(11, 0),
        );
        assert_eq!(with_on.sessions, without.sessions);
        assert_eq!(with_on.open, without.open);
        assert_eq!(with_on.unlocks, without.unlocks);
    }

    #[test]
    fn collects_unlocks() {
        let r = stitch(
            None,
            &[RawEvent {
                at: t(9, 1),
                kind: EventKind::Unlock,
            }],
            t(11, 0),
        );
        assert_eq!(r.unlocks, vec![t(9, 1)]);
    }

    #[test]
    fn ignores_a_paused_for_a_different_package() {
        let r = stitch(
            None,
            &[resumed(10, 0, "com.a"), paused(10, 2, "com.other")],
            t(11, 0),
        );
        assert_eq!(r.sessions, vec![]);
        assert_eq!(
            r.open,
            Some(OpenApp {
                package: "com.a".into(),
                since: t(10, 0)
            })
        );
    }

    #[test]
    fn drops_events_after_the_window_end() {
        let r = stitch(
            None,
            &[resumed(10, 0, "com.a"), paused(12, 0, "com.a")],
            t(11, 0),
        );
        assert_eq!(r.sessions, vec![]);
        assert_eq!(
            r.open,
            Some(OpenApp {
                package: "com.a".into(),
                since: t(10, 0)
            })
        );
    }

    #[test]
    fn out_of_order_events_are_sorted_before_stitching() {
        let r = stitch(
            None,
            &[paused(10, 5, "com.a"), resumed(10, 0, "com.a")],
            t(11, 0),
        );
        assert_eq!(
            r.sessions,
            vec![Session {
                package: "com.a".into(),
                start: t(10, 0),
                end: t(10, 5)
            }]
        );
    }
}
