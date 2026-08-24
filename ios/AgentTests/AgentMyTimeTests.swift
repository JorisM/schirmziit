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
}
