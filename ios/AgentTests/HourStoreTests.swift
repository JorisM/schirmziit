import XCTest
@testable import SchirmziitKit

final class HourStoreTests: XCTestCase {
    private func store(maxRows: Int = 100) -> FileHourStore {
        FileHourStore(directory: temporaryDirectory(), maxRows: maxRows)
    }

    func testAnEmptyStoreHasNoPendingHours() throws {
        XCTAssertEqual(try store().pending(), [])
    }

    func testMergeThenReadRoundTrips() throws {
        let store = store()
        try store.merge([pendingHour()])
        XCTAssertEqual(try store.pending(), [pendingHour()])
    }

    /// The bug the Android agent shipped once: a later, thinner recompute of the
    /// same hour overwrote a fuller one and screen time went *down*.
    func testAThinnerVersionOfAnHourNeverOverwritesAFullerOne() throws {
        let store = store()
        let full = pendingHour(screenOn: 600_000, computedAt: hour + 1_000)
        let thin = pendingHour(
            screenOn: 120_000,
            computedAt: hour + 2_000,
            apps: [PendingApp(package: "com.a", label: "App A", foregroundMs: 120_000, launchCount: 1)]
        )

        try store.merge([full])
        try store.merge([thin])

        XCTAssertEqual(try store.pending(), [full], "the fuller version of the hour must win")
    }

    func testAFullerVersionOfAnHourReplacesTheStoredOne() throws {
        let store = store()
        let thin = pendingHour(screenOn: 120_000)
        let full = pendingHour(screenOn: 900_000, computedAt: hour + 2_000,
                               apps: [PendingApp(package: "com.a", label: "App A",
                                                 foregroundMs: 900_000, launchCount: 4)])

        try store.merge([thin])
        try store.merge([full])

        XCTAssertEqual(try store.pending(), [full])
    }

    func testHoursAreStoredOldestFirst() throws {
        let store = store()
        try store.merge([pendingHour(at: hour + anHour), pendingHour(at: hour)])
        XCTAssertEqual(try store.pending().map(\.hourStartMillis), [hour, hour + anHour])
    }

    func testTheOldestHoursAreDroppedOnceTheStoreIsFull() throws {
        let store = store(maxRows: 3)
        try store.merge((0..<5).map { pendingHour(at: hour + Int64($0) * anHour) })

        XCTAssertEqual(
            try store.pending().map(\.hourStartMillis),
            [hour + 2 * anHour, hour + 3 * anHour, hour + 4 * anHour]
        )
    }

    func testReplaceDropsEverythingElse() throws {
        let store = store()
        try store.merge([pendingHour(at: hour), pendingHour(at: hour + anHour)])
        try store.replace(with: [pendingHour(at: hour + anHour)])
        XCTAssertEqual(try store.pending().map(\.hourStartMillis), [hour + anHour])
    }
}
