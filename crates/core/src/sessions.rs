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

/// Playback state at a window boundary. Persisted and fed into the next call,
/// exactly like [`OpenApp`], or a stretch that spans two syncs is lost.
///
/// Invariant: `since.is_some()` iff `playing.is_some() && screen_off`.
#[derive(Debug, Clone, PartialEq, Eq, Default, serde::Serialize, serde::Deserialize)]
pub struct PlaybackCarry {
    pub playing: Option<String>,
    /// Defaults to false: with no carry-over the screen state is unknown, and
    /// guessing "off" would invent background time nobody listened to.
    pub screen_off: bool,
    pub since: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct StitchResult {
    pub sessions: Vec<Session>,
    pub open: Option<OpenApp>,
    pub unlocks: Vec<DateTime<Utc>>,
    /// Media playing while the screen was off, per package. Never overlaps a
    /// foreground session, and never summed into one.
    pub background: Vec<Session>,
    pub playback: PlaybackCarry,
}

/// Turn a stream of foreground transitions into closed sessions.
///
/// `prev_open` is the app still in the foreground when the previous window
/// ended; the returned `open` must be persisted and fed into the next call, or
/// usage is lost on every sync.
pub fn stitch(
    prev_open: Option<OpenApp>,
    prev_playback: Option<PlaybackCarry>,
    events: &[RawEvent],
    window_end: DateTime<Utc>,
) -> StitchResult {
    let mut sorted: Vec<&RawEvent> = events.iter().filter(|e| e.at <= window_end).collect();
    sorted.sort_by_key(|e| e.at);

    let mut out = StitchResult {
        open: prev_open,
        playback: prev_playback.unwrap_or_default(),
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
            EventKind::ScreenOff => {
                close(&mut out, event.at);
                out.playback.screen_off = true;
                open_background(&mut out, event.at);
            }
            EventKind::ScreenOn => {
                close_background(&mut out, event.at);
                out.playback.screen_off = false;
            }
            EventKind::Unlock => {
                out.unlocks.push(event.at);
                // An unlock means the screen is on, whether or not the
                // SCREEN_INTERACTIVE that should have preceded it arrived.
                close_background(&mut out, event.at);
                out.playback.screen_off = false;
            }
            EventKind::PlaybackStarted { package } => {
                if out.playback.playing.as_deref() != Some(package.as_str()) {
                    close_background(&mut out, event.at);
                    out.playback.playing = Some(package.clone());
                    open_background(&mut out, event.at);
                }
            }
            EventKind::PlaybackStopped { package } => {
                if out.playback.playing.as_deref() == Some(package.as_str()) {
                    close_background(&mut out, event.at);
                    out.playback.playing = None;
                }
            }
        }
    }

    out
}

/// A stretch runs only while both hold: something is playing, and the screen
/// is off. Whichever becomes true second is where it starts.
fn open_background(out: &mut StitchResult, at: DateTime<Utc>) {
    if out.playback.screen_off && out.playback.playing.is_some() && out.playback.since.is_none() {
        out.playback.since = Some(at);
    }
}

fn close_background(out: &mut StitchResult, at: DateTime<Utc>) {
    if let (Some(since), Some(package)) = (out.playback.since, out.playback.playing.clone())
        && at > since
    {
        out.background.push(Session {
            package,
            start: since,
            end: at,
        });
    }
    out.playback.since = None;
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
        let first = stitch(None, None, &[resumed(10, 0, "com.a")], t(10, 30));
        assert_eq!(first.sessions, vec![]);
        assert_eq!(
            first.open,
            Some(OpenApp {
                package: "com.a".into(),
                since: t(10, 0)
            })
        );

        // Next window contains no RESUMED at all - the app never left the foreground.
        let second = stitch(first.open, None, &[paused(10, 50, "com.a")], t(11, 0));
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

    fn started(h: u32, m: u32, p: &str) -> RawEvent {
        RawEvent {
            at: t(h, m),
            kind: EventKind::PlaybackStarted { package: p.into() },
        }
    }

    fn stopped(h: u32, m: u32, p: &str) -> RawEvent {
        RawEvent {
            at: t(h, m),
            kind: EventKind::PlaybackStopped { package: p.into() },
        }
    }

    fn screen_off(h: u32, m: u32) -> RawEvent {
        RawEvent {
            at: t(h, m),
            kind: EventKind::ScreenOff,
        }
    }

    fn screen_on(h: u32, m: u32) -> RawEvent {
        RawEvent {
            at: t(h, m),
            kind: EventKind::ScreenOn,
        }
    }

    fn bg(package: &str, from: DateTime<Utc>, to: DateTime<Utc>) -> Session {
        Session {
            package: package.into(),
            start: from,
            end: to,
        }
    }

    #[test]
    fn playback_with_the_screen_on_is_not_background_listening() {
        // Music while the child uses another app is real consumption, but the
        // same minute is already counted as that app's foreground time.
        let r = stitch(
            None,
            None,
            &[started(21, 0, "com.abs"), stopped(21, 30, "com.abs")],
            t(22, 0),
        );
        assert_eq!(r.background, vec![]);
    }

    #[test]
    fn the_stretch_starts_when_the_screen_goes_off_mid_playback() {
        let r = stitch(
            None,
            None,
            &[
                started(21, 0, "com.abs"),
                screen_off(21, 10),
                stopped(21, 40, "com.abs"),
            ],
            t(22, 0),
        );
        assert_eq!(r.background, vec![bg("com.abs", t(21, 10), t(21, 40))]);
    }

    #[test]
    fn the_stretch_ends_when_the_screen_wakes() {
        let r = stitch(
            None,
            None,
            &[
                screen_off(21, 0),
                started(21, 5, "com.abs"),
                screen_on(21, 25),
            ],
            t(22, 0),
        );
        assert_eq!(r.background, vec![bg("com.abs", t(21, 5), t(21, 25))]);
        assert_eq!(
            r.playback.since, None,
            "no open stretch once the screen is on"
        );
        assert_eq!(
            r.playback.playing.as_deref(),
            Some("com.abs"),
            "still playing, just not background"
        );
    }

    #[test]
    fn an_unlock_also_ends_the_stretch() {
        // KEYGUARD_HIDDEN can arrive without a SCREEN_INTERACTIVE we mapped.
        let r = stitch(
            None,
            None,
            &[
                screen_off(21, 0),
                started(21, 5, "com.abs"),
                RawEvent {
                    at: t(21, 20),
                    kind: EventKind::Unlock,
                },
            ],
            t(22, 0),
        );
        assert_eq!(r.background, vec![bg("com.abs", t(21, 5), t(21, 20))]);
    }

    #[test]
    fn an_open_stretch_carries_across_windows_without_losing_time() {
        let first = stitch(
            None,
            None,
            &[screen_off(21, 0), started(21, 5, "com.abs")],
            t(21, 30),
        );
        assert_eq!(first.background, vec![], "nothing closed yet");
        assert_eq!(first.playback.since, Some(t(21, 5)));

        let second = stitch(
            None,
            Some(first.playback),
            &[stopped(22, 10, "com.abs")],
            t(22, 30),
        );
        assert_eq!(
            second.background,
            vec![bg("com.abs", t(21, 5), t(22, 10))],
            "time between windows must not be lost"
        );
    }

    #[test]
    fn a_stop_for_a_different_app_leaves_the_stretch_open() {
        let r = stitch(
            None,
            None,
            &[
                screen_off(21, 0),
                started(21, 5, "com.abs"),
                stopped(21, 10, "com.other"),
            ],
            t(22, 0),
        );
        assert_eq!(r.background, vec![]);
        assert_eq!(r.playback.since, Some(t(21, 5)));
    }

    #[test]
    fn a_second_app_taking_over_closes_the_first_stretch() {
        let r = stitch(
            None,
            None,
            &[
                screen_off(21, 0),
                started(21, 5, "com.abs"),
                started(21, 20, "com.spotify"),
            ],
            t(22, 0),
        );
        assert_eq!(r.background, vec![bg("com.abs", t(21, 5), t(21, 20))]);
        assert_eq!(r.playback.playing.as_deref(), Some("com.spotify"));
        assert_eq!(r.playback.since, Some(t(21, 20)));
    }

    #[test]
    fn a_zero_length_stretch_is_not_recorded() {
        // A skip or a seek can start and stop a session in the same
        // millisecond. A zero-length session would still create an app row in
        // its hour and put a name on the child's screen for nothing.
        let r = stitch(
            None,
            None,
            &[
                screen_off(21, 0),
                started(21, 5, "com.abs"),
                stopped(21, 5, "com.abs"),
            ],
            t(22, 0),
        );
        assert_eq!(r.background, vec![]);
    }

    #[test]
    fn playback_defaults_to_screen_on_so_a_cold_start_never_invents_time() {
        // With no carry-over we do not know the screen state. Assuming "off"
        // would bill an unknown stretch as background listening.
        let r = stitch(None, None, &[started(21, 0, "com.abs")], t(22, 0));
        assert_eq!(r.background, vec![]);
        assert_eq!(r.playback.since, None);
    }

    #[test]
    fn screen_on_does_not_change_foreground_totals_or_unlocks() {
        // ScreenOn exists for background listening only. If it ever closes a
        // foreground session, every screen-time number in the product shifts.
        let without = stitch(
            None,
            None,
            &[resumed(10, 0, "com.a"), paused(10, 5, "com.a")],
            t(11, 0),
        );
        let with_on = stitch(
            None,
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
