import XCTest
@testable import SchirmziitKit

/// One app, two roles: the tests that matter are about what is left behind on a
/// child's phone, and about not being able to walk out of child mode.
@MainActor
final class RoleSetupTests: XCTestCase {
    private let cookie = "schirmziit_session=abc123"

    private func model(
        transport: Transport,
        credentials: CredentialStore = InMemoryCredentialStore(),
        roles: RoleStore = InMemoryRoleStore(),
        monitoring: SpyMonitoring = SpyMonitoring()
    ) -> AgentModel {
        let directory = temporaryDirectory()
        return AgentModel(
            store: FileHourStore(directory: directory),
            inbox: SnapshotInbox(directory: directory),
            credentials: credentials,
            transport: transport,
            authorizer: StubAuthorizer(state: .approved),
            monitoring: monitoring,
            roles: roles
        )
    }

    private func childrenBody() -> String {
        #"[{"id":"c8a19dc2-892d-4895-a82f-a80633152679","display_name":"Emma"}]"#
    }

    func testChoosingParentModeRecordsNothingAndStartsNoMonitoring() {
        let monitoring = SpyMonitoring()
        let roles = InMemoryRoleStore()
        let model = model(transport: StubTransport(status: 200, body: "{}"),
                          roles: roles, monitoring: monitoring)

        model.becomeParentDevice()

        XCTAssertEqual(roles.load(), .parent)
        XCTAssertEqual(model.role, .parent, "the view switches on this, not on the store")
        XCTAssertEqual(monitoring.started, 0, "a parent's own phone must never start reporting")
    }

    func testSetupSignInReturnsTheChildrenAndSendsTheSession() async {
        let transport = StubTransport([
            (200, #"{"ok":true}"#, ["set-cookie": "\(cookie); Path=/; HttpOnly"]),
            (200, childrenBody(), [:]),
        ])
        let model = model(transport: transport)

        let ok = await model.signInForChildSetup(
            server: "schirmziit.example.ch", email: "anna@example.ch", password: "a long password"
        )

        XCTAssertTrue(ok)
        XCTAssertEqual(model.setupChildren.map(\.displayName), ["Emma"])
        XCTAssertEqual(transport.sent.count, 2)
        XCTAssertEqual(transport.sent[0].url.path, "/v1/auth/login")
        XCTAssertEqual(transport.sent[1].url.path, "/v1/children")
        XCTAssertEqual(
            transport.sent[1].headers["cookie"], cookie,
            "the children list must be read as the parent who just signed in"
        )
    }

    func testAWrongParentPasswordLeavesSetupWhereItWas() async {
        let model = model(transport: StubTransport(status: 401, body: ""))

        let ok = await model.signInForChildSetup(
            server: "schirmziit.example.ch", email: "anna@example.ch", password: "wrong password"
        )

        XCTAssertFalse(ok)
        XCTAssertTrue(model.setupChildren.isEmpty)
        XCTAssertNotNil(model.lastError)
    }

    func testFinishingSetupKeepsADeviceTokenAndEndsTheParentSession() async {
        let credentials = InMemoryCredentialStore()
        let roles = InMemoryRoleStore()
        let monitoring = SpyMonitoring()
        let transport = StubTransport([
            (200, #"{"ok":true}"#, ["set-cookie": "\(cookie); Path=/"]),
            (200, childrenBody(), [:]),
            (201, #"{"device_id":"dev-1","token":"tok-1"}"#, [:]),
            (204, "", [:]),
        ])
        let model = model(transport: transport, credentials: credentials,
                          roles: roles, monitoring: monitoring)

        _ = await model.signInForChildSetup(
            server: "schirmziit.example.ch", email: "anna@example.ch", password: "a long password"
        )
        let done = await model.finishChildSetup(
            childId: "c8a19dc2-892d-4895-a82f-a80633152679", label: "Emmas iPhone"
        )

        XCTAssertTrue(done)
        XCTAssertEqual(roles.load(), .child)
        XCTAssertEqual(
            model.role, .child,
            "without this the app bounced back to the setup screen after enrolling"
        )
        XCTAssertEqual(credentials.load()?.token, "tok-1")
        XCTAssertEqual(
            credentials.load()?.parentEmail, "anna@example.ch",
            "the account is needed later to check an unlock"
        )
        XCTAssertEqual(monitoring.started, 1)

        // The order is the point: claim, then end the session. A child's phone
        // must not be left holding a parent session either way.
        XCTAssertEqual(transport.sent.map(\.url.path).suffix(2),
                       ["/v1/children/c8a19dc2-892d-4895-a82f-a80633152679/devices",
                        "/v1/auth/logout"])
        XCTAssertTrue(model.setupChildren.isEmpty, "the children list must not linger")
    }

    func testAFailedClaimLeavesNothingBehind() async {
        let credentials = InMemoryCredentialStore()
        let roles = InMemoryRoleStore()
        let transport = StubTransport([
            (200, #"{"ok":true}"#, ["set-cookie": "\(cookie); Path=/"]),
            (200, childrenBody(), [:]),
            (500, "", [:]),
        ])
        let model = model(transport: transport, credentials: credentials, roles: roles)

        _ = await model.signInForChildSetup(
            server: "schirmziit.example.ch", email: "anna@example.ch", password: "a long password"
        )
        let done = await model.finishChildSetup(childId: "c8a19dc2-892d-4895-a82f-a80633152679", label: "x")

        XCTAssertFalse(done)
        XCTAssertNil(credentials.load(), "a failed claim must not leave a token")
        XCTAssertNil(roles.load(), "and must not put the phone into child mode")
    }

    func testLeavingChildModeNeedsTheParentPassword() async {
        let credentials = InMemoryCredentialStore(
            AgentCredentials(
                baseURL: URL(string: "https://schirmziit.example.ch")!,
                deviceId: "dev-1", token: "tok-1", parentEmail: "anna@example.ch"
            )
        )
        let roles = InMemoryRoleStore(.child)
        let monitoring = SpyMonitoring()
        let model = model(transport: StubTransport(status: 401, body: ""),
                          credentials: credentials, roles: roles, monitoring: monitoring)

        let left = await model.leaveChildMode(password: "a guess")

        XCTAssertFalse(left)
        XCTAssertEqual(roles.load(), .child, "child mode a child can tap out of is decoration")
        XCTAssertNotNil(credentials.load())
        XCTAssertEqual(monitoring.stopped, 0)
    }

    func testTheRightPasswordEndsChildMode() async {
        let credentials = InMemoryCredentialStore(
            AgentCredentials(
                baseURL: URL(string: "https://schirmziit.example.ch")!,
                deviceId: "dev-1", token: "tok-1", parentEmail: "anna@example.ch"
            )
        )
        let roles = InMemoryRoleStore(.child)
        let monitoring = SpyMonitoring()
        let transport = StubTransport([
            (200, #"{"ok":true}"#, ["set-cookie": "schirmziit_session=fresh; Path=/"]),
            (204, "", [:]),
        ])
        let model = model(transport: transport, credentials: credentials,
                          roles: roles, monitoring: monitoring)

        let left = await model.leaveChildMode(password: "the real password")

        XCTAssertTrue(left)
        XCTAssertNil(roles.load())
        XCTAssertNil(model.role)
        XCTAssertNil(credentials.load(), "the device token goes with it")
        XCTAssertEqual(monitoring.stopped, 1)
        XCTAssertEqual(
            transport.sent.last?.url.path, "/v1/auth/logout",
            "the session opened to check the password must be closed again"
        )
    }

    func testAnOlderEnrolmentWithoutAnAccountOnFileCanStillBeUndone() async {
        // Credentials from before parentEmail existed. Refusing would trap the
        // phone in child mode with no way out at all.
        let credentials = InMemoryCredentialStore(
            AgentCredentials(
                baseURL: URL(string: "https://schirmziit.example.ch")!,
                deviceId: "dev-1", token: "tok-1", parentEmail: nil
            )
        )
        let roles = InMemoryRoleStore(.child)
        let model = model(transport: StubTransport(status: 500, body: ""),
                          credentials: credentials, roles: roles)

        let left = await model.leaveChildMode(password: "")

        XCTAssertTrue(left)
        XCTAssertNil(roles.load())
        XCTAssertNil(credentials.load())
    }
}

/// The store the app actually uses, rather than the in-memory double.
///
/// A build signed without the App Group entitlement gets a non-nil suite whose
/// writes are dropped on the floor; the role then never persisted and finishing
/// child setup dropped the app back to the setup screen, having already enrolled
/// a device.
final class DefaultsRoleStoreTests: XCTestCase {
    private var suiteName = ""

    override func setUp() {
        super.setUp()
        suiteName = "ch.jorisda.schirmziit.tests.\(UUID().uuidString)"
    }

    override func tearDown() {
        UserDefaults().removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    func testARoleSurvivesANewStoreOverTheSameDefaults() {
        let defaults = UserDefaults(suiteName: suiteName)!
        DefaultsRoleStore(defaults: defaults).save(.child)

        // A fresh instance is what the next launch gets.
        XCTAssertEqual(DefaultsRoleStore(defaults: defaults).load(), .child)
    }

    func testClearingReallyClears() {
        let defaults = UserDefaults(suiteName: suiteName)!
        let store = DefaultsRoleStore(defaults: defaults)
        store.save(.parent)
        store.clear()
        XCTAssertNil(store.load())
    }

    func testTheChosenStoreIsOneThatActuallyPersists() {
        // Whatever it picks — the App Group suite or the app's own — a value must
        // survive a read. That round-trip is the whole point of the probe.
        let store = DefaultsRoleStore.persistentStore(groupIdentifier: "group.does.not.exist.\(UUID().uuidString)")
        store.set("probe", forKey: "ch.jorisda.schirmziit.tests.probe")
        XCTAssertEqual(store.string(forKey: "ch.jorisda.schirmziit.tests.probe"), "probe")
        store.removeObject(forKey: "ch.jorisda.schirmziit.tests.probe")
    }
}
