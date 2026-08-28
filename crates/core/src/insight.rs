//! One week against the one before it — the sentence a parent comes back for.
//!
//! The arithmetic lives here, once, for the same reason the wire format does:
//! the dashboard, an iPhone and an Android phone all print "evenings are up 40
//! minutes", and three implementations of "which week" is three chances to
//! print three different numbers from the same database. The server does the
//! query, this module does the comparing, and every surface renders numbers it
//! did not compute.
//!
//! Nothing here is a judgement. A comparison says what moved; the words around
//! it belong to the locale files, and none of them score a child.

use crate::error::CoreError;
use chrono::{DateTime, Datelike, Duration, NaiveDate, TimeZone, Timelike, Utc};
use chrono_tz::Tz;
use std::collections::BTreeMap;

/// Seven days, so a week is compared with a week: any other window makes a
/// Tuesday look at a Saturday.
pub const WEEK_DAYS: i64 = 7;

/// Evening starts at 21:00 local. Not "after school" and not "after dinner" —
/// the hours around bedtime are the ones a family actually argues about, and a
/// wider window would just restate the total.
pub const EVENING_FROM_HOUR: u32 = 21;

/// Five minutes. Below this an app has not moved, it has wobbled, and
/// "Calculator, up 40 seconds" is noise dressed as an insight.
pub const MOVER_FLOOR_MS: i64 = 5 * 60 * 1000;

/// At most three. A list of movers long enough to scroll is a report, and a
/// parent reads a sentence.
pub const MAX_MOVERS: usize = 3;

/// One app's measured foreground time in one hour.
///
/// No `background_ms`: media playing with the screen off is a separate measure
/// everywhere in this product, and a comparison that quietly folded it in
/// would inflate every week over every other week.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HourPoint {
    pub hour_start: DateTime<Utc>,
    pub package: String,
    pub label: String,
    pub foreground_ms: i64,
}

/// One app, in both weeks. `ms` may be lower than `previous_ms`; a mover moves
/// in either direction and the renderer says which.
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
pub struct AppMove {
    pub package: String,
    pub label: String,
    pub foreground_ms: i64,
    pub previous_foreground_ms: i64,
}

impl AppMove {
    pub fn delta_ms(&self) -> i64 {
        self.foreground_ms - self.previous_foreground_ms
    }
}

/// Seven days against the seven before them.
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
pub struct WeekComparison {
    /// First and last local day of the recent week, both inclusive.
    pub from: NaiveDate,
    pub to: NaiveDate,
    /// The week before, both inclusive.
    pub previous_from: NaiveDate,
    pub previous_to: NaiveDate,
    pub total_ms: i64,
    pub previous_total_ms: i64,
    /// Screen time from `evening_from_hour` to local midnight, a subset of
    /// `total_ms` and never something to add to it.
    pub evening_ms: i64,
    pub previous_evening_ms: i64,
    /// Shipped rather than assumed, so a renderer can name the hour it is
    /// talking about instead of hard-coding a 21 of its own.
    pub evening_from_hour: u32,
    /// Ranked by distance moved, longest first, at most [`MAX_MOVERS`].
    pub movers: Vec<AppMove>,
    /// False when no device reported anything in the earlier week. A week
    /// against nothing is not a doubling, it is a first week, and every
    /// surface has to say so rather than print a percentage.
    pub previous_measured: bool,
}

impl WeekComparison {
    pub fn delta_ms(&self) -> i64 {
        self.total_ms - self.previous_total_ms
    }

    pub fn evening_delta_ms(&self) -> i64 {
        self.evening_ms - self.previous_evening_ms
    }
}

/// `today` is excluded: a day still being lived is always shorter than the one
/// it is compared with, and an insight that reads "down 3 hours" every morning
/// is worse than no insight. The recent week is the seven complete days ending
/// yesterday.
///
/// `previous_measured` is the caller's answer to "did any device report at all
/// in the earlier window" — silence and a genuine zero look identical in a sum
/// and must not read alike.
pub fn compare(
    tz: &str,
    today: NaiveDate,
    points: &[HourPoint],
    previous_measured: bool,
) -> Result<WeekComparison, CoreError> {
    let zone: Tz = tz
        .parse()
        .map_err(|_| CoreError::UnknownTimezone(tz.to_string()))?;
    let weeks = weeks(tz, today)?;
    let Weeks {
        from,
        to,
        previous_from,
        previous_to,
        ..
    } = weeks;

    let mut total_ms = 0;
    let mut previous_total_ms = 0;
    let mut evening_ms = 0;
    let mut previous_evening_ms = 0;
    // Keyed by package: two devices reporting the same app are one app to a
    // parent, and the label follows the package rather than the row.
    let mut apps: BTreeMap<String, AppMove> = BTreeMap::new();

    for point in points {
        let local = point.hour_start.with_timezone(&zone);
        let day = local.date_naive();
        let recent = day >= from && day <= to;
        let previous = day >= previous_from && day <= previous_to;
        if !recent && !previous {
            // A caller may hand over a wider range than it asked about; the
            // window is decided here, not by whatever the query returned.
            continue;
        }
        let evening = local.hour() >= EVENING_FROM_HOUR;

        let entry = apps
            .entry(point.package.clone())
            .or_insert_with(|| AppMove {
                package: point.package.clone(),
                label: point.label.clone(),
                foreground_ms: 0,
                previous_foreground_ms: 0,
            });

        if recent {
            total_ms += point.foreground_ms;
            entry.foreground_ms += point.foreground_ms;
            if evening {
                evening_ms += point.foreground_ms;
            }
        } else {
            previous_total_ms += point.foreground_ms;
            entry.previous_foreground_ms += point.foreground_ms;
            if evening {
                previous_evening_ms += point.foreground_ms;
            }
        }
    }

    let mut movers: Vec<AppMove> = apps
        .into_values()
        .filter(|app| app.delta_ms().abs() >= MOVER_FLOOR_MS)
        .collect();
    // Distance first, then the longer app, then the package — a stable order,
    // because a card that reshuffles between two equal movers on every poll
    // reads as data changing when nothing changed.
    movers.sort_by(|a, b| {
        b.delta_ms()
            .abs()
            .cmp(&a.delta_ms().abs())
            .then(b.foreground_ms.cmp(&a.foreground_ms))
            .then(a.package.cmp(&b.package))
    });
    movers.truncate(MAX_MOVERS);

    Ok(WeekComparison {
        from,
        to,
        previous_from,
        previous_to,
        total_ms,
        previous_total_ms,
        evening_ms,
        previous_evening_ms,
        evening_from_hour: EVENING_FROM_HOUR,
        movers,
        previous_measured,
    })
}

/// The two weeks, as local days and as the instants a query needs. A caller
/// that derived these itself would be the second implementation of "which
/// week", which is the thing this module exists to prevent.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Weeks {
    pub from: NaiveDate,
    pub to: NaiveDate,
    pub previous_from: NaiveDate,
    pub previous_to: NaiveDate,
    /// Local midnight at the start of `previous_from`: the first instant
    /// either week contains.
    pub start: DateTime<Utc>,
    /// Local midnight at the start of `from` — the boundary between the two
    /// weeks, and the exclusive end of the earlier one.
    pub previous_end: DateTime<Utc>,
    /// Local midnight at the start of `today`, exclusive: today belongs to
    /// neither week.
    pub end: DateTime<Utc>,
}

/// The seven complete days ending yesterday, and the seven before them.
pub fn weeks(tz: &str, today: NaiveDate) -> Result<Weeks, CoreError> {
    let zone: Tz = tz
        .parse()
        .map_err(|_| CoreError::UnknownTimezone(tz.to_string()))?;
    let to = today - Duration::days(1);
    let from = to - Duration::days(WEEK_DAYS - 1);
    let previous_to = from - Duration::days(1);
    let previous_from = previous_to - Duration::days(WEEK_DAYS - 1);
    Ok(Weeks {
        from,
        to,
        previous_from,
        previous_to,
        start: local_midnight(zone, previous_from),
        previous_end: local_midnight(zone, from),
        end: local_midnight(zone, today),
    })
}

/// A local day can begin at 01:00 where a DST jump skips midnight itself.
/// `earliest` picks the first instant that exists, which is what "the start of
/// that day" means to the person living it.
fn local_midnight(zone: Tz, day: NaiveDate) -> DateTime<Utc> {
    zone.with_ymd_and_hms(day.year(), day.month(), day.day(), 0, 0, 0)
        .earliest()
        .unwrap_or_else(|| {
            zone.with_ymd_and_hms(day.year(), day.month(), day.day(), 1, 0, 0)
                .earliest()
                .expect("a local day starts within its first two hours")
        })
        .with_timezone(&Utc)
}

#[cfg(test)]
mod tests {
    use super::*;

    const TZ: &str = "Europe/Zurich";

    fn day(d: u32) -> NaiveDate {
        NaiveDate::from_ymd_opt(2026, 8, d).unwrap()
    }

    /// `d` is a local Zurich day, `hour` a local hour; the point is stored in
    /// UTC, which is what a device reports.
    fn point(d: u32, hour: u32, package: &str, ms: i64) -> HourPoint {
        let local = chrono_tz::Europe::Zurich
            .with_ymd_and_hms(2026, 8, d, hour, 0, 0)
            .unwrap();
        HourPoint {
            hour_start: local.with_timezone(&Utc),
            package: package.into(),
            label: package.into(),
            foreground_ms: ms,
        }
    }

    fn minutes(n: i64) -> i64 {
        n * 60 * 1000
    }

    #[test]
    fn a_week_ends_yesterday_because_today_is_not_over() {
        let c = compare(TZ, day(20), &[], true).unwrap();
        assert_eq!(c.to, day(19));
        assert_eq!(c.from, day(13));
        assert_eq!(c.previous_to, day(12));
        assert_eq!(c.previous_from, day(6));
    }

    #[test]
    fn each_hour_lands_in_the_week_it_was_lived_in() {
        let c = compare(
            TZ,
            day(20),
            &[
                point(19, 10, "com.a", minutes(30)),
                point(13, 10, "com.a", minutes(10)),
                point(12, 10, "com.a", minutes(20)),
                point(6, 10, "com.a", minutes(5)),
            ],
            true,
        )
        .unwrap();
        assert_eq!(c.total_ms, minutes(40));
        assert_eq!(c.previous_total_ms, minutes(25));
        assert_eq!(c.delta_ms(), minutes(15));
    }

    #[test]
    fn hours_outside_the_two_weeks_are_not_counted() {
        let c = compare(
            TZ,
            day(20),
            &[
                // Today, and the day before the earlier week began.
                point(20, 10, "com.a", minutes(90)),
                point(5, 10, "com.a", minutes(90)),
            ],
            true,
        )
        .unwrap();
        assert_eq!(c.total_ms, 0);
        assert_eq!(c.previous_total_ms, 0);
        assert!(c.movers.is_empty());
    }

    #[test]
    fn an_evening_is_counted_by_the_childs_clock_not_by_utc() {
        // 21:00 in Zurich is 19:00 UTC in August. Counting UTC hours would put
        // this in the afternoon and the evening figure would silently be wrong
        // for every family not living on GMT.
        let c = compare(
            TZ,
            day(20),
            &[
                point(19, 21, "com.a", minutes(30)),
                point(19, 20, "com.a", minutes(45)),
            ],
            true,
        )
        .unwrap();
        assert_eq!(c.evening_ms, minutes(30));
        assert_eq!(c.total_ms, minutes(75));
        assert_eq!(c.evening_from_hour, 21);
    }

    #[test]
    fn evenings_are_compared_with_evenings() {
        let c = compare(
            TZ,
            day(20),
            &[
                point(19, 22, "com.a", minutes(50)),
                point(12, 22, "com.a", minutes(10)),
            ],
            true,
        )
        .unwrap();
        assert_eq!(c.evening_ms, minutes(50));
        assert_eq!(c.previous_evening_ms, minutes(10));
        assert_eq!(c.evening_delta_ms(), minutes(40));
    }

    #[test]
    fn an_app_that_wobbled_is_not_a_mover() {
        let c = compare(
            TZ,
            day(20),
            &[
                point(19, 10, "com.calc", minutes(4)),
                point(12, 10, "com.calc", minutes(1)),
            ],
            true,
        )
        .unwrap();
        assert!(c.movers.is_empty());
        // Still part of the week's total: not a mover is not "not counted".
        assert_eq!(c.total_ms, minutes(4));
    }

    #[test]
    fn movers_are_ranked_by_distance_in_either_direction() {
        let c = compare(
            TZ,
            day(20),
            &[
                point(19, 10, "com.up", minutes(20)),
                point(12, 10, "com.up", minutes(6)),
                point(19, 10, "com.down", minutes(5)),
                point(12, 10, "com.down", minutes(65)),
                point(19, 10, "com.same", minutes(30)),
                point(12, 10, "com.same", minutes(30)),
            ],
            true,
        )
        .unwrap();
        let names: Vec<&str> = c.movers.iter().map(|m| m.package.as_str()).collect();
        assert_eq!(names, ["com.down", "com.up"]);
        assert_eq!(c.movers[0].delta_ms(), -minutes(60));
        assert_eq!(c.movers[1].delta_ms(), minutes(14));
    }

    #[test]
    fn at_most_three_movers_are_named() {
        let mut points = Vec::new();
        for (i, package) in ["com.a", "com.b", "com.c", "com.d", "com.e"]
            .iter()
            .enumerate()
        {
            points.push(point(19, 10, package, minutes(10 + i as i64 * 10)));
        }
        let c = compare(TZ, day(20), &points, true).unwrap();
        assert_eq!(c.movers.len(), MAX_MOVERS);
        assert_eq!(c.movers[0].package, "com.e");
    }

    #[test]
    fn an_app_used_in_only_one_week_still_moved() {
        let c = compare(TZ, day(20), &[point(19, 10, "com.new", minutes(90))], true).unwrap();
        assert_eq!(c.movers.len(), 1);
        assert_eq!(c.movers[0].previous_foreground_ms, 0);
        assert_eq!(c.movers[0].delta_ms(), minutes(90));
    }

    #[test]
    fn a_first_week_is_a_first_week_and_not_a_doubling() {
        let c = compare(TZ, day(20), &[point(19, 10, "com.a", minutes(90))], false).unwrap();
        assert!(!c.previous_measured);
        assert_eq!(c.previous_total_ms, 0);
    }

    #[test]
    fn the_window_is_fourteen_days_ending_at_local_midnight_today() {
        let w = weeks(TZ, day(20)).unwrap();
        // 6 August 00:00 in Zurich is 5 August 22:00 UTC in summer.
        assert_eq!(w.start.to_rfc3339(), "2026-08-05T22:00:00+00:00");
        assert_eq!(w.previous_end.to_rfc3339(), "2026-08-12T22:00:00+00:00");
        assert_eq!(w.end.to_rfc3339(), "2026-08-19T22:00:00+00:00");
        assert_eq!((w.end - w.start).num_days(), 14);
        assert_eq!((w.previous_end - w.start).num_days(), 7);
    }

    #[test]
    fn a_week_that_crosses_the_clock_change_is_still_seven_days() {
        // Zurich falls back on 25 October 2026: that week is 169 hours long,
        // and a comparison built on "start plus seven times 24 hours" would
        // hand the earlier week an hour that belongs to the later one.
        let w = weeks(TZ, NaiveDate::from_ymd_opt(2026, 10, 27).unwrap()).unwrap();
        assert_eq!(w.from, NaiveDate::from_ymd_opt(2026, 10, 20).unwrap());
        assert_eq!(w.previous_end.to_rfc3339(), "2026-10-19T22:00:00+00:00");
        assert_eq!((w.end - w.previous_end).num_hours(), 169);
    }

    #[test]
    fn an_unknown_timezone_is_an_error_everywhere() {
        assert!(compare("Mars/Olympus", day(20), &[], true).is_err());
        assert!(weeks("Mars/Olympus", day(20)).is_err());
    }
}
