import XCTest
@testable import SchirmziitKit

/// The three writes the parent app can make: add a child, remove a child,
/// disconnect a phone. Two of them are irreversible, so what matters is not
/// only that they reach the right route — it is that a *failure* comes back as a
/// failure. A delete that swallows its 502 and returns "done" is how a parent
/// ends up believing a phone has stopped reporting while it has not.
///
/// Targeted at the `static func`s the screens funnel through, for the reason
/// `ChildDetailViewTests` records: these views own no `@Observable` model, and
/// `@State` on a never-rendered view silently loses writes.
final class ChildWritesTests: XCTestCase {
    /// Records what went on the wire, so a test asserts the method and path
    /// rather than merely that something was sent.
    private static let sent = SentRequestLog()

    private func stubbedClient(
        status: Int,
        body: Data = Data("{}".utf8)
    ) async -> ApiClient {
        let log = Self.sent
        log.clear()
        StubURLProtocol.handler = { request in
            log.record(
                SentRequest(
                    method: request.httpMethod ?? "",
                    path: request.url?.path ?? ""
                )
            )
            return (status, body)
        }
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubURLProtocol.self]
        let client = ApiClient(session: URLSession(configuration: config))
        await client.configure(baseURL: URL(string: "https://parent.example.test"))
        return client
    }

    private let problemBody = Data(
        #"{"type":"about:blank","title":"error","status":502,"detail":"bad gateway"}"#.utf8
    )

    private let createdBody = Data(
        #"{"id":"kid","display_name":"Lena","today_ms":0}"#.utf8
    )

    // MARK: - Adding a child

    func testAddingAChildPostsTheTrimmedNameToTheChildrenRoute() async {
        let client = await stubbedClient(status: 201, body: createdBody)

        let outcome = await ChildrenView.create(client: client, name: "  Lena  ")

        XCTAssertEqual(outcome, .ok)
        XCTAssertEqual(Self.sent.all.map(\.method), ["POST"])
        XCTAssertEqual(Self.sent.all.first?.path, "/v1/children")
    }

    func testAddingAChildReportsTheServersOwnWords() async {
        let client = await stubbedClient(status: 502, body: problemBody)

        let outcome = await ChildrenView.create(client: client, name: "Lena")

        XCTAssertEqual(outcome, .failed("bad gateway"))
    }

    /// The button is disabled on a blank field, so this is the second line of
    /// defence — and the one that matters, because a request that was never
    /// sent must not read as a child that was created.
    func testABlankNameIsNeverSent() async {
        let client = await stubbedClient(status: 201, body: createdBody)

        let outcome = await ChildrenView.create(client: client, name: "   \n ")

        XCTAssertNotEqual(outcome, .ok, "a blank name must not report success")
        XCTAssertTrue(Self.sent.all.isEmpty, "a blank name must not reach the server")
    }

    // MARK: - Removing a child

    func testRemovingAChildDeletesThatChildsRoute() async {
        let client = await stubbedClient(status: 204, body: Data())

        let outcome = await ChildrenView.remove(client: client, childId: "kid")

        XCTAssertEqual(outcome, .ok)
        XCTAssertEqual(Self.sent.all.map(\.method), ["DELETE"])
        XCTAssertEqual(Self.sent.all.first?.path, "/v1/children/kid")
    }

    /// 204 carries no body. A delete implemented on top of the decoding path
    /// would throw here and report a failure for a delete that succeeded —
    /// which sends the parent back to press an irreversible button again.
    func testASuccessfulDeleteWithNoBodyIsNotReadAsAFailure() async {
        let client = await stubbedClient(status: 204, body: Data())

        let outcome = await ChildrenView.remove(client: client, childId: "kid")
        XCTAssertEqual(outcome, .ok)
    }

    func testAFailedRemoveCarriesTheProblemDetail() async {
        let client = await stubbedClient(status: 502, body: problemBody)

        let outcome = await ChildrenView.remove(client: client, childId: "kid")
        XCTAssertEqual(outcome, .failed("bad gateway"))
    }

    func testAnUnreachableServerIsAFailureNotASilentSuccess() async {
        let client = ApiClient(session: URLSession(configuration: .ephemeral))
        await client.configure(baseURL: nil)

        guard case .failed = await ChildrenView.remove(client: client, childId: "kid") else {
            return XCTFail("an unconfigured client must fail")
        }
    }

    // MARK: - Disconnecting a phone

    func testRevokingADeviceDeletesTheDeviceRoute() async {
        let client = await stubbedClient(status: 204, body: Data())

        let outcome = await ChildDetailView.revokeDevice(client: client, deviceId: "dev-1")

        XCTAssertEqual(outcome, .ok)
        XCTAssertEqual(Self.sent.all.map(\.method), ["DELETE"])
        XCTAssertEqual(
            Self.sent.all.first?.path, "/v1/devices/dev-1",
            "a device is revoked by its own id, never through the child's route"
        )
    }

    func testAFailedRevokeCarriesTheProblemDetail() async {
        let client = await stubbedClient(status: 502, body: problemBody)

        let outcome = await ChildDetailView.revokeDevice(client: client, deviceId: "dev-1")
        XCTAssertEqual(outcome, .failed("bad gateway"))
    }
}
