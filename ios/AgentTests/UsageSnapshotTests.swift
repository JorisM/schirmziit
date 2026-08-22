import XCTest
@testable import SchirmziitKit

final class UsageSnapshotTests: XCTestCase {
    private func snapshot(apps: [SnapshotApp], screenOnMs: Int64 = 600_000) -> UsageSnapshot {
        UsageSnapshot(
            hourStartMillis: hour,
            tz: "Europe/Zurich",
            computedAtMillis: hour + anHour,
            screenOnMs: screenOnMs,
            pickups: 4,
            apps: apps
        )
    }

    func testKeepsTheReportedFieldsAsTheyAre() {
        let row = snapshot(apps: [SnapshotApp(bundleId: "com.a", name: "App A", durationMs: 600_000, launchCount: 2)])
            .pendingHour()

        XCTAssertEqual(row.hourStartMillis, hour)
        XCTAssertEqual(row.tz, "Europe/Zurich")
        XCTAssertEqual(row.computedAtMillis, hour + anHour)
        XCTAssertEqual(row.unlockCount, 4, "iOS has no unlock count, so pickups stand in for it")
        XCTAssertEqual(row.apps, [PendingApp(package: "com.a", label: "App A", foregroundMs: 600_000, launchCount: 2)])
    }

    func testAppsWithNoTimeAreDropped() {
        let row = snapshot(apps: [
            SnapshotApp(bundleId: "com.a", name: "App A", durationMs: 600_000, launchCount: 1),
            SnapshotApp(bundleId: "com.b", name: "App B", durationMs: 0, launchCount: 0),
        ]).pendingHour()

        XCTAssertEqual(row.apps.map(\.package), ["com.a"])
    }

    func testAppsComeBackLongestFirstAndAreCapped() {
        let many = (0..<30).map {
            SnapshotApp(bundleId: "com.app\($0)", name: "App \($0)", durationMs: Int64($0) * 1_000, launchCount: 1)
        }
        let row = snapshot(apps: many).pendingHour(maxApps: 3)

        XCTAssertEqual(row.apps.map(\.package), ["com.app29", "com.app28", "com.app27"])
    }

    func testAMissingDisplayNameFallsBackToTheBundleId() {
        let row = snapshot(apps: [SnapshotApp(bundleId: "com.a", name: nil, durationMs: 1_000, launchCount: 0)])
            .pendingHour()
        XCTAssertEqual(row.apps.first?.label, "com.a")

        let empty = snapshot(apps: [SnapshotApp(bundleId: "com.a", name: "", durationMs: 1_000, launchCount: 0)])
            .pendingHour()
        XCTAssertEqual(empty.apps.first?.label, "com.a")
    }

    /// Split view puts two apps on screen at once, so the per-app sum can exceed
    /// the reported total. Believing the smaller number under-reports the day.
    func testScreenOnTimeIsNeverLessThanTheAppsAddUpTo() {
        let row = snapshot(
            apps: [
                SnapshotApp(bundleId: "com.a", name: "A", durationMs: 400_000, launchCount: 1),
                SnapshotApp(bundleId: "com.b", name: "B", durationMs: 400_000, launchCount: 1),
            ],
            screenOnMs: 500_000
        ).pendingHour()

        XCTAssertEqual(row.screenOnMs, 800_000)
    }

    func testAReportedTotalLargerThanTheAppsIsKept() {
        let row = snapshot(
            apps: [SnapshotApp(bundleId: "com.a", name: "A", durationMs: 60_000, launchCount: 1)],
            screenOnMs: 900_000
        ).pendingHour()

        XCTAssertEqual(row.screenOnMs, 900_000, "time in apps iOS withholds still counts as screen on")
    }
}
