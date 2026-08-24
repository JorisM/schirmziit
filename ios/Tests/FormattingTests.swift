import XCTest
@testable import SchirmziitKit

final class FormattingTests: XCTestCase {
    func testDurationReadsLikeAPersonWroteIt() {
        XCTAssertEqual(Formatting.duration(600_000), "10 min")
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
            DeviceTotal(start: "2026-08-21T08:00:00+02:00", screenOnMs: 100, unlockCount: 1),
            DeviceTotal(start: "2026-08-21T08:00:00+02:00", screenOnMs: 200, unlockCount: 1),
            DeviceTotal(start: "2026-08-21T23:00:00+02:00", screenOnMs: 50, unlockCount: 0),
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
            points: points.map { UsagePoint(start: $0.0, foregroundMs: $0.1, launchCount: 1) }
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
}
