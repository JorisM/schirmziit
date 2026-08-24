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
}
