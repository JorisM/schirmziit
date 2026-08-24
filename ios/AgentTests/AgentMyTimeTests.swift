import XCTest
@testable import SchirmziitKit

/// The strip is fourteen days of rows; picking one of them must not refetch
/// the other thirteen. A child's phone is the surface of the three (web
/// dashboard, iOS parent, iOS child) most likely to be on a metered
/// connection, so this is pinned rather than left to code review.
@MainActor
final class AgentMyTimeTests: XCTestCase {
    private func model(transport: Transport) -> AgentModel {
        let directory = temporaryDirectory()
        return AgentModel(
            store: FileHourStore(directory: directory),
            inbox: SnapshotInbox(directory: directory),
            credentials: InMemoryCredentialStore(
                AgentCredentials(
                    baseURL: URL(string: "https://schirmziit.example.ch")!,
                    deviceId: "dev-1",
                    token: "tok-1"
                )
            ),
            transport: transport,
            authorizer: StubAuthorizer(state: .approved),
            monitoring: SpyMonitoring(),
            roles: InMemoryRoleStore(.child)
        )
    }

    private func stripBody() -> String {
        #"{"from":"2026-08-11","to":"2026-08-24","series":[],"device_totals":[]}"#
    }

    private func dayBody() -> String {
        #"{"from":"2026-08-20","to":"2026-08-20","series":[],"device_totals":[]}"#
    }

    func testSelectingADayIssuesExactlyOneMoreRequestAndItIsTheHourOne() async {
        let transport = StubTransport([(200, stripBody(), [:]), (200, dayBody(), [:])])
        let agent = model(transport: transport)

        await agent.loadMyTimeStrip()
        XCTAssertEqual(transport.sent.count, 1, "the strip is one request")
        XCTAssertTrue(transport.sent[0].url.absoluteString.contains("bucket=day"))

        await agent.selectMyDay("2026-08-20")

        XCTAssertEqual(
            transport.sent.count, 2,
            "picking a day must cost exactly one further request, not a re-fetch of the strip too"
        )
        let picked = transport.sent[1]
        XCTAssertTrue(picked.url.absoluteString.contains("bucket=hour"), "the day request, not the strip")
        XCTAssertEqual(agent.mySelectedDay, "2026-08-20")
    }

    /// The `catch` blocks in `AgentModel` simply never touch `myDays`/`myDay`,
    /// so nothing today would zero a child's screen on a failed load — but
    /// nothing would fail either if a future edit added `myDays = []` to a
    /// catch block "to be safe". Pinned so that regresses loudly.
    func testAFailedLoadNeverZeroesThePreviousNumbers() async {
        let transport = StubTransport(replies: [
            .success(HttpResponse(status: 200, body: Data(stripBody().utf8))),
            .success(HttpResponse(status: 200, body: Data(dayBody().utf8))),
            .failure(URLError(.timedOut)),
        ])
        let agent = model(transport: transport)

        await agent.loadMyTimeStrip()
        await agent.selectMyDay("2026-08-20")
        let daysBefore = agent.myDays
        let dayBefore = agent.myDay
        XCTAssertFalse(daysBefore.isEmpty, "sanity: the first load actually populated something")
        XCTAssertNotNil(dayBefore, "sanity: the first load actually populated something")

        // A captcha page, a timeout, a 500 — whatever it is, the third call fails.
        await agent.selectMyDay("2026-08-21")

        XCTAssertEqual(agent.myDays, daysBefore, "a failed day fetch must not touch the strip already on screen")
        XCTAssertEqual(agent.myDay, dayBefore, "the previous day's numbers stay on screen — never a silent zero")
        XCTAssertNotNil(agent.myTimeError, "the failure must still be visible, even though the numbers held")
    }

    /// A transport that blocks each `myUsage` call on a continuation the test
    /// controls, so two model calls can be forced to genuinely overlap in a
    /// known order — a double-tap on retry, deterministically, rather than
    /// hoping real timing reproduces it.
    private final class GatedTransport: Transport, @unchecked Sendable {
        private let lock = NSLock()
        private var continuations: [CheckedContinuation<HttpResponse, Error>] = []
        private(set) var sentCount = 0

        func send(_ request: HttpRequest) async throws -> HttpResponse {
            lock.withLock { sentCount += 1 }
            return try await withCheckedThrowingContinuation { continuation in
                lock.withLock { continuations.append(continuation) }
            }
        }

        /// Resolves the call at `index` (in arrival order) with `result`.
        func resolve(index: Int, result: Result<HttpResponse, Error>) {
            let continuation: CheckedContinuation<HttpResponse, Error>? = lock.withLock {
                index < continuations.count ? continuations[index] : nil
            }
            switch result {
            case .success(let response): continuation?.resume(returning: response)
            case .failure(let error): continuation?.resume(throwing: error)
            }
        }
    }

    /// Pins the bug the retry button re-opened: two overlapping loads must
    /// never leave the error cleared with no day loaded behind it — that
    /// reads as "nothing happened", not as a failure, and the child is stuck
    /// looking at a spinner forever.
    func testOverlappingLoadsMustNotClearTheErrorWithNoDayLoaded() async throws {
        let transport = GatedTransport()
        let agent = model(transport: transport)

        // Tap 1: pick a day. It will fail — held open until we say so.
        let task1 = Task { await agent.selectMyDay("2026-08-20") }
        var attempts = 0
        while transport.sentCount < 1 && attempts < 200 {
            await Task.yield()
            attempts += 1
        }
        XCTAssertEqual(transport.sentCount, 1, "sanity: the first call actually reached the network")

        // Tap 2, while tap 1 is still in flight: retry's strip fetch. It will
        // succeed. Give it every chance to also reach the network — if
        // nothing stops it, it will.
        let task2 = Task { await agent.loadMyTimeStrip() }
        attempts = 0
        while transport.sentCount < 2 && attempts < 200 {
            await Task.yield()
            attempts += 1
        }
        let secondCallWasBlocked = transport.sentCount == 1

        // Tap 1 fails now.
        transport.resolve(index: 0, result: .failure(URLError(.timedOut)))
        await task1.value

        if !secondCallWasBlocked {
            // Unguarded: tap 2 reached the network and is waiting on its own
            // response. Let it succeed, the way a real strip fetch would.
            transport.resolve(
                index: 1,
                result: .success(HttpResponse(
                    status: 200,
                    body: Data(#"{"from":"2026-08-11","to":"2026-08-24","series":[],"device_totals":[]}"#.utf8)
                ))
            )
        }
        await task2.value

        XCTAssertFalse(
            agent.myTimeError == nil && agent.myDay == nil,
            "a cleared error with no day loaded reads as nothing happened, not as a failure the child can act on"
        )
    }
}
