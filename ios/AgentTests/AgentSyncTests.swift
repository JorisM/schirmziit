import XCTest
@testable import SchirmziitKit

final class AgentSyncTests: XCTestCase {
    private let credentials = AgentCredentials(
        baseURL: URL(string: "https://schirmziit.example.ch")!,
        deviceId: "dev",
        token: "tok"
    )

    private func makeSync(
        transport: Transport,
        directory: URL = temporaryDirectory(),
        paired: Bool = true
    ) -> (AgentSync, FileHourStore, SnapshotInbox) {
        let store = FileHourStore(directory: directory)
        let inbox = SnapshotInbox(directory: directory)
        let sync = AgentSync(
            store: store,
            inbox: inbox,
            credentials: InMemoryCredentialStore(paired ? credentials : nil),
            transport: transport,
            now: { Date(timeIntervalSince1970: 1_787_997_600) }
        )
        return (sync, store, inbox)
    }

    private func accepted(_ hours: [Int64]) -> String {
        let stamps = hours.map { millis in
            let date = Date(timeIntervalSince1970: Double(millis) / 1000)
            return "\"\(ISO8601DateFormatter().string(from: date))\""
        }
        return #"{"accepted":[\#(stamps.joined(separator: ","))],"rejected":[]}"#
    }

    func testCollectFoldsSnapshotsIntoTheQueue() throws {
        let (sync, store, inbox) = makeSync(transport: StubTransport(status: 200, body: "{}"))
        try inbox.write(UsageSnapshot(hourStartMillis: hour, tz: "Europe/Zurich",
                                      computedAtMillis: hour + anHour, screenOnMs: 60_000, pickups: 1,
                                      apps: [SnapshotApp(bundleId: "com.a", name: "A", durationMs: 60_000, launchCount: 1)]))

        try sync.collect()

        XCTAssertEqual(try store.pending().map(\.hourStartMillis), [hour])
    }

    func testAnAcceptedHourLeavesTheQueue() async throws {
        let transport = StubTransport(status: 200, body: accepted([hour]))
        let (sync, store, _) = makeSync(transport: transport)
        try store.merge([pendingHour()])

        let outcome = try await sync.run()

        XCTAssertEqual(outcome, SyncOutcome(sent: 1, remaining: 0))
        XCTAssertEqual(try store.pending(), [])
        XCTAssertEqual(transport.sent.count, 1)
    }

    /// The body has to be built by the core, or iOS and Android would send two
    /// different shapes to the same endpoint.
    func testTheUploadedBodyIsTheCoresWireFormat() async throws {
        let transport = StubTransport(status: 200, body: accepted([hour]))
        let (sync, store, _) = makeSync(transport: transport)
        try store.merge([pendingHour()])

        _ = try await sync.run()

        let body = try XCTUnwrap(transport.sent.first?.body)
        let json = try XCTUnwrap(try JSONSerialization.jsonObject(with: body) as? [String: Any])
        XCTAssertEqual(json["schema"] as? Int, 1)
        XCTAssertNotNil(json["device_time"] as? String)
        let hours = try XCTUnwrap(json["hours"] as? [[String: Any]])
        XCTAssertEqual(hours.first?["tz"] as? String, "Europe/Zurich")
        XCTAssertEqual(hours.first?["screen_on_ms"] as? Int, 600_000)
        XCTAssertEqual((hours.first?["apps"] as? [[String: Any]])?.first?["package"] as? String, "com.a")
    }

    func testATransientRejectionStaysQueued() async throws {
        let date = ISO8601DateFormatter().string(from: Date(timeIntervalSince1970: Double(hour) / 1000))
        let body = #"{"accepted":[],"rejected":[{"hour_start":"\#(date)","reason":"db down","permanent":false}]}"#
        let (sync, store, _) = makeSync(transport: StubTransport(status: 200, body: body))
        try store.merge([pendingHour()])

        let outcome = try await sync.run()

        XCTAssertEqual(outcome, SyncOutcome(sent: 0, remaining: 1))
        XCTAssertEqual(try store.pending().map(\.hourStartMillis), [hour])
    }

    func testAPermanentRejectionIsDroppedSoTheQueueCanDrain() async throws {
        let date = ISO8601DateFormatter().string(from: Date(timeIntervalSince1970: Double(hour) / 1000))
        let body = #"{"accepted":[],"rejected":[{"hour_start":"\#(date)","reason":"bad tz","permanent":true}]}"#
        let (sync, store, _) = makeSync(transport: StubTransport(status: 200, body: body))
        try store.merge([pendingHour()])

        _ = try await sync.run()

        XCTAssertEqual(try store.pending(), [])
    }

    /// A captcha page or a proxy error must not read as "everything accepted".
    func testAnUnparseableResponseKeepsTheQueue() async throws {
        let (sync, store, _) = makeSync(transport: StubTransport(status: 200, body: "<html>captcha</html>"))
        try store.merge([pendingHour()])

        do {
            _ = try await sync.run()
            XCTFail("expected the core to reject the body")
        } catch {
            XCTAssertEqual(try store.pending().map(\.hourStartMillis), [hour])
        }
    }

    func testARevokedTokenKeepsTheQueue() async throws {
        let (sync, store, _) = makeSync(transport: StubTransport(status: 401, body: ""))
        try store.merge([pendingHour()])

        do {
            _ = try await sync.run()
            XCTFail("expected unauthorized")
        } catch {
            XCTAssertEqual(error as? AgentClientError, .unauthorized)
            XCTAssertEqual(try store.pending().map(\.hourStartMillis), [hour])
        }
    }

    func testNothingIsSentWhenTheQueueIsEmpty() async throws {
        let transport = StubTransport(status: 200, body: "{}")
        let (sync, _, _) = makeSync(transport: transport)

        let outcome = try await sync.run()

        XCTAssertEqual(outcome, .idle)
        XCTAssertEqual(transport.sent, [], "an empty queue must not cost a request")
    }

    func testAnUnpairedDeviceDoesNotReachTheNetwork() async throws {
        let transport = StubTransport(status: 200, body: "{}")
        let (sync, store, _) = makeSync(transport: transport, paired: false)
        try store.merge([pendingHour()])

        do {
            _ = try await sync.run()
            XCTFail("expected notPaired")
        } catch {
            XCTAssertEqual(error as? AgentSyncError, .notPaired)
            XCTAssertEqual(transport.sent, [])
        }
    }

    func testHoursBeyondOneRequestAreKeptForTheNextRun() async throws {
        let rows = (0..<60).map { pendingHour(at: hour + Int64($0) * anHour) }
        let sentHours = rows.prefix(Int(AgentSync.maxRows)).map(\.hourStartMillis)
        let (sync, store, _) = makeSync(transport: StubTransport(status: 200, body: accepted(sentHours)))
        try store.merge(rows)

        let outcome = try await sync.run()

        XCTAssertEqual(outcome.sent, Int(AgentSync.maxRows))
        XCTAssertEqual(try store.pending().count, rows.count - Int(AgentSync.maxRows))
    }
}
