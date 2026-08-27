import XCTest
@testable import SchirmziitKit

/// The four writes the parent app can make: add a child, remove a child,
/// disconnect a phone, mint a pairing code. Two of them are irreversible, so
/// what matters is not
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
        #"{"type":"about:blank","title":"error","status":502,"detail":"bad gateway","code":"SZ-E901","ref":"aa11bb"}"#.utf8
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

    /// The code, not the server's English `detail`: that sentence is for the log
    /// and the copy-details block, and the parent reads the catalog's wording in
    /// their own language.
    func testAddingAChildReportsTheServersCode() async {
        let client = await stubbedClient(status: 502, body: problemBody)

        let outcome = await ChildrenView.create(client: client, name: "Lena")

        guard case .failed(let error) = outcome else {
            return XCTFail("a 502 must be a failure, got \(outcome)")
        }
        XCTAssertEqual(error.code.wire, "SZ-E901")
        XCTAssertEqual(error.ref, "aa11bb")
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

    func testAFailedRemoveCarriesTheProblemCode() async {
        let client = await stubbedClient(status: 502, body: problemBody)

        let outcome = await ChildrenView.remove(client: client, childId: "kid")
        guard case .failed(let error) = outcome else {
            return XCTFail("a 502 must be a failure, got \(outcome)")
        }
        XCTAssertEqual(error.code.wire, "SZ-E901")
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

    func testAFailedRevokeCarriesTheProblemCode() async {
        let client = await stubbedClient(status: 502, body: problemBody)

        let outcome = await ChildDetailView.revokeDevice(client: client, deviceId: "dev-1")
        guard case .failed(let error) = outcome else {
            return XCTFail("a 502 must be a failure, got \(outcome)")
        }
        XCTAssertEqual(error.code.wire, "SZ-E901")
    }

    // MARK: - Deleting a child's stored figures

    private let purgedBody = Data(
        #"{"deleted_usage_hours":412,"deleted_device_hours":168,"deleted_usage_days":14}"#.utf8
    )

    /// The figures, not the child. `DELETE /v1/children/{id}` removes the child
    /// themselves, which is a different and much larger act — and the route
    /// that does the smaller one differs from it by a single path segment.
    func testPurgingDeletesTheChildsDataRouteNotTheChild() async {
        let client = await stubbedClient(status: 200, body: purgedBody)

        let outcome = await PurgeDataView.purgeData(client: client, childId: "kid")

        guard case .success(let purged) = outcome else {
            return XCTFail("a 200 must be a success, got \(outcome)")
        }
        XCTAssertEqual(purged.deletedUsageHours, 412)
        XCTAssertEqual(purged.deletedDeviceHours, 168)
        XCTAssertEqual(purged.deletedUsageDays, 14)
        XCTAssertEqual(Self.sent.all.map(\.method), ["DELETE"])
        XCTAssertEqual(Self.sent.all.first?.path, "/v1/children/kid/data")
    }

    /// A purge that matched nothing answers with zeros, and they have to survive
    /// as zeros: a family whose phone has not reported yet must be able to tell
    /// a purge that worked from one that found nothing.
    func testAPurgeThatMatchedNothingReportsZeroRatherThanNothing() async {
        let client = await stubbedClient(
            status: 200,
            body: Data(
                #"{"deleted_usage_hours":0,"deleted_device_hours":0,"deleted_usage_days":0}"#.utf8
            )
        )

        let outcome = await PurgeDataView.purgeData(client: client, childId: "kid")

        guard case .success(let purged) = outcome else {
            return XCTFail("a 200 must be a success, got \(outcome)")
        }
        XCTAssertEqual(purged.deletedUsageHours, 0)
    }

    func testAFailedPurgeCarriesTheProblemCode() async {
        let client = await stubbedClient(status: 502, body: problemBody)

        let outcome = await PurgeDataView.purgeData(client: client, childId: "kid")
        guard case .failure(let error) = outcome else {
            return XCTFail("a 502 must be a failure, got \(outcome)")
        }
        XCTAssertEqual(error.code.wire, "SZ-E901")
        XCTAssertEqual(error.ref, "aa11bb")
    }

    /// The finding this test exists for: a captive portal or a proxy answering
    /// 200 in the server's place must be a failure. Read as a purge it would
    /// tell a parent their child's figures are gone while every row is still
    /// there — the one lie an irreversible-looking button must never tell.
    func testABodyThatIsNotAPurgeIsNeverReadAsOne() async {
        let client = await stubbedClient(
            status: 200,
            body: Data("<html><body>Sign in to the guest network</body></html>".utf8)
        )

        let outcome = await PurgeDataView.purgeData(client: client, childId: "kid")

        guard case .failure = outcome else {
            return XCTFail("a proxy page must never be read as a purge, got \(outcome)")
        }
    }

    /// A 200 whose counts are missing is not a purge of zero rows either: it is
    /// a body this app cannot read, and a receipt built from defaults would be
    /// a number nobody counted.
    func testAPurgeMissingItsCountsIsAFailureNotThreeZeroes() async {
        let client = await stubbedClient(status: 200, body: Data(#"{"deleted_usage_hours":9}"#.utf8))

        let outcome = await PurgeDataView.purgeData(client: client, childId: "kid")

        guard case .failure = outcome else {
            return XCTFail("a partial body must not decode into a receipt, got \(outcome)")
        }
    }

    // MARK: - Minting a pairing code

    private let enrollmentBody = Data(
        #"""
        {"code":"A2B3C4","expires_at":"2026-08-27T09:15:00Z",
         "qr_payload":"schirmziit://enroll?url=https://api.schirmziit.ch&code=A2B3C4"}
        """#.utf8
    )

    func testMintingACodePostsToThatChildsEnrollmentsRoute() async {
        let client = await stubbedClient(status: 201, body: enrollmentBody)

        let outcome = await PairDeviceView.mintCode(client: client, childId: "kid")

        guard case .success(let enrollment) = outcome else {
            return XCTFail("a 201 must be a success, got \(outcome)")
        }
        XCTAssertEqual(enrollment.code, "A2B3C4")
        XCTAssertEqual(Self.sent.all.map(\.method), ["POST"])
        XCTAssertEqual(Self.sent.all.first?.path, "/v1/children/kid/enrollments")
    }

    /// One press, one code. Every mint burns a code the server keeps for fifteen
    /// minutes, so a card that quietly asked twice would hand out one the parent
    /// never sees and cannot use.
    func testMintingACodeSendsExactlyOneRequest() async {
        let client = await stubbedClient(status: 201, body: enrollmentBody)

        _ = await PairDeviceView.mintCode(client: client, childId: "kid")

        XCTAssertEqual(Self.sent.all.count, 1)
    }

    func testAFailedMintCarriesTheProblemCode() async {
        let client = await stubbedClient(status: 502, body: problemBody)

        let outcome = await PairDeviceView.mintCode(client: client, childId: "kid")
        guard case .failure(let error) = outcome else {
            return XCTFail("a 502 must be a failure, got \(outcome)")
        }
        XCTAssertEqual(error.code.wire, "SZ-E901")
        XCTAssertEqual(error.ref, "aa11bb")
    }

    /// A captive portal, a proxy page, a server one version behind: whatever
    /// came back, it is not a code. Reading it as one would put an empty card on
    /// screen for a parent to read six characters off.
    func testAMintThatDoesNotAnswerWithACodeIsAFailure() async {
        let client = await stubbedClient(status: 200, body: Data("<html>sign in to the guest wifi</html>".utf8))

        guard case .failure = await PairDeviceView.mintCode(client: client, childId: "kid") else {
            return XCTFail("a body that is not an enrollment must not read as one")
        }
    }
}
