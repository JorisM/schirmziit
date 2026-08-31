import XCTest
@testable import SchirmziitKit

final class ReportedAppTests: XCTestCase {
    func testABundleIdIsTheRowAndTheNameIsTheLabel() {
        let rows = SnapshotApp.fold([
            ReportedApp(bundleId: "com.burbn.instagram", name: "Instagram", durationMs: 60_000, launchCount: 2)
        ])

        XCTAssertEqual(rows, [
            SnapshotApp(bundleId: "com.burbn.instagram", name: "Instagram", durationMs: 60_000, launchCount: 2)
        ])
    }

    /// The same app arrives once per segment and per category, so the halves of
    /// an hour have to end up in one row rather than three.
    func testTheSameAppSeenTwiceIsOneRow() {
        let rows = SnapshotApp.fold([
            ReportedApp(bundleId: "com.a", name: "A", durationMs: 60_000, launchCount: 1),
            ReportedApp(bundleId: "com.a", name: "A", durationMs: 30_000, launchCount: 2),
        ])

        XCTAssertEqual(rows.count, 1)
        XCTAssertEqual(rows.first?.durationMs, 90_000)
        XCTAssertEqual(rows.first?.launchCount, 3)
    }

    /// The bug this file was written for. Two apps iOS named but would not
    /// identify used to share the key "unknown", so their time was summed into
    /// one row that carried whichever name arrived first — a parent read one
    /// app's name over two apps' minutes.
    func testTwoAppsWithoutABundleIdStayTwoRows() {
        let rows = SnapshotApp.fold([
            ReportedApp(bundleId: nil, name: "Instagram", durationMs: 60_000, launchCount: 1),
            ReportedApp(bundleId: nil, name: "YouTube", durationMs: 30_000, launchCount: 1),
        ])

        XCTAssertEqual(rows.count, 2)
        XCTAssertEqual(rows.map(\.name), ["Instagram", "YouTube"])
        XCTAssertEqual(rows.map(\.durationMs), [60_000, 30_000])
    }

    /// A name is enough to key a row: the same app has to land in the same row
    /// next hour, or a day fragments into an app per hour.
    func testANamedAppWithoutABundleIdKeepsOneKeyAcrossHours() {
        let first = SnapshotApp.fold([
            ReportedApp(bundleId: nil, name: "Instagram", durationMs: 60_000, launchCount: 1)
        ])
        let second = SnapshotApp.fold([
            ReportedApp(bundleId: nil, name: "Instagram", durationMs: 30_000, launchCount: 1)
        ])

        XCTAssertEqual(first.first?.bundleId, second.first?.bundleId)
        XCTAssertNotEqual(first.first?.bundleId, "unknown")
        XCTAssertEqual(first.first?.name, "Instagram")
    }

    /// An empty string is iOS saying nothing, in the way that reads as a value.
    func testAnEmptyNameCountsAsNoName() {
        let rows = SnapshotApp.fold([
            ReportedApp(bundleId: "com.a", name: "", durationMs: 60_000, launchCount: 1)
        ])

        XCTAssertNil(rows.first?.name)
    }

    /// iOS names an app on the second segment and not the first. Keeping the
    /// first `nil` would throw away the only name the hour contained.
    func testANameThatArrivesLateIsKept() {
        let rows = SnapshotApp.fold([
            ReportedApp(bundleId: "com.a", name: nil, durationMs: 60_000, launchCount: 1),
            ReportedApp(bundleId: "com.a", name: "App A", durationMs: 30_000, launchCount: 1),
        ])

        XCTAssertEqual(rows.first?.name, "App A")
    }

    /// Neither a name nor an id is not a row a parent can read, and inventing
    /// one word for all of them merges apps that have nothing to do with each
    /// other. The time is not lost: `screenOnMs` comes from the segment total,
    /// which counts these apps whether or not they get a row.
    func testAnAppIosWillNeitherNameNorIdentifyIsNotARow() {
        let rows = SnapshotApp.fold([
            ReportedApp(bundleId: nil, name: nil, durationMs: 60_000, launchCount: 1),
            ReportedApp(bundleId: "", name: "", durationMs: 30_000, launchCount: 1),
            ReportedApp(bundleId: "com.a", name: "A", durationMs: 10_000, launchCount: 1),
        ])

        XCTAssertEqual(rows.map(\.bundleId), ["com.a"])
    }

    /// A name that reduces to nothing is no better than no name: keying these
    /// to the bare prefix would rebuild the shared bucket this fold removed.
    func testANameThatIsOnlyPunctuationIsNotARow() {
        let rows = SnapshotApp.fold([
            ReportedApp(bundleId: nil, name: "…", durationMs: 60_000, launchCount: 1),
            ReportedApp(bundleId: nil, name: "!!!", durationMs: 30_000, launchCount: 1),
        ])

        XCTAssertEqual(rows, [])
    }

    /// Spacing and punctuation around a name are not what makes two apps two
    /// apps, and a row per spelling would split one app across a day.
    func testTheSameNameSpelledLooselyIsOneRow() {
        let rows = SnapshotApp.fold([
            ReportedApp(bundleId: nil, name: "My App", durationMs: 60_000, launchCount: 1),
            ReportedApp(bundleId: nil, name: " my  app! ", durationMs: 30_000, launchCount: 1),
        ])

        XCTAssertEqual(rows.count, 1)
        XCTAssertEqual(rows.first?.durationMs, 90_000)
    }

    func testRowsComeBackLongestFirst() {
        let rows = SnapshotApp.fold([
            ReportedApp(bundleId: "com.a", name: "A", durationMs: 10_000, launchCount: 1),
            ReportedApp(bundleId: "com.b", name: "B", durationMs: 90_000, launchCount: 1),
        ])

        XCTAssertEqual(rows.map(\.bundleId), ["com.b", "com.a"])
    }
}
