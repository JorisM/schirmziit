import XCTest
@testable import SchirmziitAgentKit

final class SnapshotInboxTests: XCTestCase {
    private func snapshot(at millis: Int64) -> UsageSnapshot {
        UsageSnapshot(hourStartMillis: millis, tz: "Europe/Zurich", computedAtMillis: millis + anHour,
                      screenOnMs: 60_000, pickups: 1,
                      apps: [SnapshotApp(bundleId: "com.a", name: "A", durationMs: 60_000, launchCount: 1)])
    }

    func testDrainReturnsWhatWasWrittenAndEmptiesTheInbox() throws {
        let inbox = SnapshotInbox(directory: temporaryDirectory())
        try inbox.write(snapshot(at: hour))
        try inbox.write(snapshot(at: hour + anHour))

        XCTAssertEqual(inbox.drain().map(\.hourStartMillis), [hour, hour + anHour])
        XCTAssertEqual(inbox.drain(), [], "a drained snapshot must not be uploaded twice")
    }

    func testWritingTheSameHourTwiceKeepsOnlyTheNewerFile() throws {
        let inbox = SnapshotInbox(directory: temporaryDirectory())
        try inbox.write(snapshot(at: hour))
        var later = snapshot(at: hour)
        later.screenOnMs = 900_000
        try inbox.write(later)

        XCTAssertEqual(inbox.drain(), [later])
    }

    func testAnUnreadableFileIsDroppedRatherThanBlockingTheQueue() throws {
        let directory = temporaryDirectory()
        let inbox = SnapshotInbox(directory: directory)
        try Data("not json".utf8).write(to: directory.appendingPathComponent("snapshot-broken.json"))
        try inbox.write(snapshot(at: hour))

        XCTAssertEqual(inbox.drain().map(\.hourStartMillis), [hour])
        XCTAssertEqual(inbox.drain(), [])
    }

    func testUnrelatedFilesAreLeftAlone() throws {
        let directory = temporaryDirectory()
        let inbox = SnapshotInbox(directory: directory)
        let other = directory.appendingPathComponent("pending-hours.json")
        try Data("[]".utf8).write(to: other)

        _ = inbox.drain()

        XCTAssertTrue(FileManager.default.fileExists(atPath: other.path))
    }
}
