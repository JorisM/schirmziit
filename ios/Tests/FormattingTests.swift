import XCTest
@testable import SchirmziitKit

final class FormattingTests: XCTestCase {
    func testDurationReadsLikeAPersonWroteIt() {
        XCTAssertEqual(Formatting.duration(600_000), "10 min")
        XCTAssertEqual(Formatting.duration(3_600_000), "1 h")
        XCTAssertEqual(Formatting.duration(8_040_000), "2 h 14 min")
    }

    func testDurationRendersSecondsBelowAMinute() {
        XCTAssertEqual(Formatting.duration(20_000), "20 s")
        XCTAssertEqual(Formatting.duration(45_400), "45 s")
    }

    func testDurationKeepsZeroAsZeroMinutes() {
        XCTAssertEqual(Formatting.duration(0), "0 min")
    }

    func testDurationNeverRendersSixtySeconds() {
        XCTAssertEqual(Formatting.duration(59_500), "1 min")
        XCTAssertEqual(Formatting.duration(60_000), "1 min")
    }

    func testDurationLeavesLongerSpansAlone() {
        XCTAssertEqual(Formatting.duration(90_000), "2 min")
        XCTAssertEqual(Formatting.duration(3_600_000), "1 h")
        XCTAssertEqual(Formatting.duration(8_040_000), "2 h 14 min")
    }

    func testLocalHourComesFromTheTimestampsOwnOffset() {
        XCTAssertEqual(Formatting.localHour(from: "2026-08-21T15:00:00+02:00"), 15)
        XCTAssertEqual(Formatting.localHour(from: "2026-08-21T23:00:00Z"), 23)
    }

    func testUnparseableTimestampIsDroppedRatherThanBecomingMidnight() {
        // Slicing blindly would make this hour 0 and paint phantom night-time
        // usage, which is exactly what the ribbon exists to reveal.
        XCTAssertNil(Formatting.localHour(from: "nonsense"))
        XCTAssertNil(Formatting.localHour(from: ""))
        XCTAssertNil(Formatting.localHour(from: "2026-08-21T99:00:00Z"))
    }

    func testHoursFromTotalsSumsPerLocalHour() {
        let hours = Formatting.hoursFromTotals([
            DeviceTotal(start: "2026-08-21T08:00:00+02:00", screenOnMs: 100, unlockCount: 1, backgroundMeasured: false),
            DeviceTotal(start: "2026-08-21T08:00:00+02:00", screenOnMs: 200, unlockCount: 1, backgroundMeasured: false),
            DeviceTotal(start: "2026-08-21T23:00:00+02:00", screenOnMs: 50, unlockCount: 0, backgroundMeasured: false),
        ])
        XCTAssertEqual(hours.count, 24)
        XCTAssertEqual(hours[8], 300)
        XCTAssertEqual(hours[23], 50)
    }

    func testRampScalesToTheBusiestHourNotAFixedCeiling() {
        XCTAssertEqual(Formatting.rampStep(ms: 0, busiest: 3_600_000), 0)
        XCTAssertEqual(Formatting.rampStep(ms: 3_600_000, busiest: 3_600_000), 5)
        XCTAssertEqual(Formatting.rampStep(ms: 1_800_000, busiest: 3_600_000), 3)
        XCTAssertEqual(Formatting.rampStep(ms: 1_800_000, busiest: 1_800_000), 5)
    }

    func testRampDoesNotDivideByZeroOnAnUnusedDay() {
        XCTAssertEqual(Formatting.rampStep(ms: 0, busiest: 0), 0)
    }

    private func series(_ points: [(String, Int)]) -> [UsageSeries] {
        [UsageSeries(
            package: "com.a",
            label: "A",
            points: points.map { UsagePoint(start: $0.0, foregroundMs: $0.1, launchCount: 1, backgroundMs: 0) }
        )]
    }

    func testEveryDayInRangeAppearsEvenWithNoRows() {
        let days = Formatting.dailyTotals(
            series([("2026-08-18", 60_000), ("2026-08-20", 30_000)]),
            from: "2026-08-18", to: "2026-08-20"
        )
        XCTAssertEqual(days.map(\.day), ["2026-08-18", "2026-08-19", "2026-08-20"])
        XCTAssertEqual(days.map(\.ms), [60_000, 0, 30_000])
    }

    func testAPointOutsideTheRangeIsDroppedNotFoldedIntoTheFirstDay() {
        let days = Formatting.dailyTotals(series([("2026-07-01", 60_000)]), from: "2026-08-18", to: "2026-08-20")
        XCTAssertEqual(days.map(\.ms), [0, 0, 0])
    }

    func testSplitAppsSeparatesTheGlancesFromTheDay() {
        let (shown, brief) = Formatting.splitApps([
            Formatting.AppEntry(label: "A", ms: 3_600_000), Formatting.AppEntry(label: "B", ms: 45_000), Formatting.AppEntry(label: "C", ms: 60_000),
        ])
        XCTAssertEqual(shown.map(\.label), ["A", "C"])
        XCTAssertEqual(brief.map(\.label), ["B"])
    }

    func testSplitAppsDropsAnAppThatRoundsToZeroSeconds() {
        let (shown, brief) = Formatting.splitApps([Formatting.AppEntry(label: "A", ms: 3_600_000), Formatting.AppEntry(label: "Blink", ms: 300)])
        XCTAssertEqual(shown.map(\.label), ["A"])
        XCTAssertTrue(brief.isEmpty)
    }

    func testSplitAppsKeepsAnAppThatRoundsToOneSecond() {
        let (_, brief) = Formatting.splitApps([Formatting.AppEntry(label: "Blink", ms: 900)])
        XCTAssertEqual(brief.map(\.label), ["Blink"])
    }

    /// The central claim of the fold: a brief app is already folded and must
    /// never be the thing an eight-row cap crowds out. Nine ranked apps (one
    /// more than the cap) plus two glances proves the cap only ever eats into
    /// `shown` — if the cap were applied to `shown + brief` together instead,
    /// this would fail by losing a brief app rather than a ranked one.
    func testVisibleAppsCapsShownRowsButNeverTheFoldedGlances() {
        let ranked = (1...9).map { Formatting.AppEntry(label: "Ranked\($0)", ms: 60_000 + $0) }
        let brief = [Formatting.AppEntry(label: "Brief1", ms: 30_000), Formatting.AppEntry(label: "Brief2", ms: 20_000)]
        let split = Formatting.splitApps(ranked + brief)
        let visible = Formatting.visibleApps(split, cap: 8)
        XCTAssertEqual(visible.shown.count, 8)
        XCTAssertEqual(visible.brief.map(\.label), ["Brief1", "Brief2"])
    }

    /// Background listening travels with the row it belongs to, and never
    /// changes where that row lands: the fold and the rank read foreground only.
    func testAnAppIsRankedAndFoldedByForegroundAloneNotByListening() {
        let (shown, brief) = Formatting.splitApps([
            Formatting.AppEntry(label: "Book", ms: 30_000, backgroundMs: 7_200_000),
            Formatting.AppEntry(label: "Game", ms: 600_000, backgroundMs: 0),
        ])

        XCTAssertEqual(shown.map(\.label), ["Game"], "two hours of listening is not a minute of screen")
        XCTAssertEqual(brief.map(\.label), ["Book"])
        XCTAssertEqual(brief.first?.backgroundMs, 7_200_000, "the folded row keeps its listening")
    }
}
