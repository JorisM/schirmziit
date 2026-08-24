import Foundation
import Observation

/// The one piece of app state. Reads everything fresh on `refresh()` so a
/// permission the child changed in Settings shows up the moment the app comes
/// back to the foreground.
@MainActor
@Observable
public final class AgentModel {
    public private(set) var status: AgentStatus = .needsPairing
    public private(set) var isBusy = false
    public private(set) var lastError: String?
    /// What the last "send now" did, so the button visibly answers.
    public private(set) var lastSyncNote: String?
    public private(set) var sharedContainerAvailable = GroupContainer.isShared()
    /// Mirrored from the store into observable state: `roles.load()` reads
    /// UserDefaults, which Observation cannot track, so a view switching on it
    /// never re-rendered when the role changed.
    public private(set) var role: AppRole?

    /// Children the signed-in parent can pick from, mid-setup. Cleared as soon
    /// as setup finishes.
    public private(set) var setupChildren: [SetupChild] = []

    public private(set) var myDays: [DayTotalFfi] = []
    public private(set) var myDay: DayDetailFfi?
    public private(set) var mySelectedDay: String = ISO8601DateFormatter.dayOnly.string(from: Date())
    /// Nil while it has never been loaded; set to a sentence the child can act
    /// on when a load failed. Never a silent zero — "you used nothing today" is
    /// the wrong thing to tell someone because the wifi was off.
    public private(set) var myTimeError: String?
    /// Guards `loadMyTimeStrip`/`selectMyDay` against overlapping calls — a
    /// double-tap on retry, or two bars tapped in quick succession. Both
    /// functions write the shared `myTimeError`, so letting two runs overlap
    /// can interleave their completions: one fails and sets the error, the
    /// other then succeeds and clears it while never having touched `myDay`
    /// — a spinner with nothing behind it and no error to explain why.
    public private(set) var myTimeBusy = false

    private let store: HourStore
    private let inbox: SnapshotInbox
    private let credentials: CredentialStore
    private let transport: Transport
    private let authorizer: ScreenTimeAuthorizing
    private let monitoring: UsageMonitoring
    private let roles: RoleStore
    private var lastSyncAt: Date?
    /// What the last authorization request actually answered.
    ///
    /// `AuthorizationCenter.authorizationStatus` stays `.notDetermined` for a
    /// build Apple has not approved for Family Controls, so re-reading it after a
    /// failed request produced the same screen and the button looked dead. The
    /// answer has to be remembered.
    private var requestedAuthorization: ScreenTimeAuthorization?
    /// In memory for the length of a setup, never written anywhere.
    private var setupSession: (baseURL: URL, cookie: String, email: String)?

    /// Production wiring: the shared container, the keychain, the real Screen
    /// Time API. The app uses this; the tests use the initialiser below.
    public convenience init() {
        self.init(
            store: FileHourStore(),
            inbox: SnapshotInbox(),
            credentials: KeychainCredentialStore(),
            transport: URLSessionTransport(),
            authorizer: FamilyControlsAuthorizer(),
            monitoring: DeviceActivityMonitoring(),
            roles: DefaultsRoleStore()
        )
    }

    init(
        store: HourStore,
        inbox: SnapshotInbox,
        credentials: CredentialStore,
        transport: Transport,
        authorizer: ScreenTimeAuthorizing,
        monitoring: UsageMonitoring,
        roles: RoleStore
    ) {
        self.store = store
        self.inbox = inbox
        self.credentials = credentials
        self.transport = transport
        self.authorizer = authorizer
        self.monitoring = monitoring
        self.roles = roles
        role = roles.load()
        refresh()
    }

    public var pendingCount: Int { (try? store.pending().count) ?? 0 }

    public func refresh() {
        role = roles.load()
        try? sync.collect()
        // A live `.approved` always wins: the parent may have granted it in
        // Settings since we last asked. Otherwise the remembered answer stands,
        // because the system status cannot express "this build may not ask".
        let live = authorizer.current
        let authorization = live == .approved ? live : (requestedAuthorization ?? live)

        status = AgentStatus.derive(
            credentials: credentials.load(),
            authorization: authorization,
            pendingHours: pendingCount,
            lastSyncAt: lastSyncAt
        )
        sharedContainerAvailable = GroupContainer.isShared()
    }

    public func requestScreenTime() async {
        isBusy = true
        lastError = nil
        defer { isBusy = false }

        let result = await authorizer.request()
        requestedAuthorization = result
        if result == .approved {
            // Nothing is recorded until the schedule exists, so start it the
            // moment permission lands rather than at the next launch.
            try? monitoring.start()
        }
        refresh()
    }

    public func pair(server: String, code: String, label: String) async {
        guard let url = Self.normalisedServer(server) else {
            lastError = String(localized: "agent.pairing.badserver")
            return
        }

        isBusy = true
        lastError = nil
        defer { isBusy = false }

        let client = AgentClient(baseURL: url, transport: transport)
        do {
            let enrolled = try await client.enroll(
                code: code.trimmingCharacters(in: .whitespaces).uppercased(),
                platform: "ios",
                model: DeviceInfo.model,
                label: label.isEmpty ? DeviceInfo.name : label
            )
            try credentials.save(
                AgentCredentials(baseURL: url, deviceId: enrolled.deviceId, token: enrolled.token)
            )
            try? monitoring.start()
        } catch AgentClientError.unknownCode {
            lastError = String(localized: "agent.pairing.badcode")
        } catch {
            lastError = String(localized: "agent.pairing.failed")
        }
        refresh()
    }

    public func syncNow() async {
        isBusy = true
        lastError = nil
        lastSyncNote = nil
        defer { isBusy = false }
        do {
            let outcome = try await sync.run()
            lastSyncAt = Date()
            // "Send now" on a phone with nothing recorded did nothing visible,
            // which reads as a broken button rather than as an empty queue.
            lastSyncNote = outcome.sent == 0 && outcome.remaining == 0
                ? String(localized: "agent.status.sync.nothing")
                : String(localized: "agent.status.sync.done")
        } catch {
            lastError = String(localized: "agent.status.sync.failed")
        }
        refresh()
    }

    /// The fourteen-day strip for this phone's own child, over this phone's
    /// own device token. Called once when the screen appears — the strip is
    /// fourteen days of rows and must not be refetched every time a day is
    /// picked, same shape as the web dashboard and the iOS parent view.
    public func loadMyTimeStrip() async {
        guard !myTimeBusy else { return }
        myTimeBusy = true
        defer { myTimeBusy = false }
        guard let credentials = credentials.load() else { return }
        let client = AgentClient(baseURL: credentials.baseURL, transport: transport)
        let zone = TimeZone.current.identifier
        let from = ISO8601DateFormatter.dayOnly.string(
            from: Calendar.current.date(byAdding: .day, value: -13, to: Date()) ?? Date()
        )
        let today = ISO8601DateFormatter.dayOnly.string(from: Date())

        do {
            let stripBody = try await client.myUsage(
                token: credentials.token, from: from, to: today, bucket: "day", tz: zone
            )
            // Through the core: a captcha page throws here rather than becoming
            // an empty fortnight that tells a child they used nothing.
            myDays = try parseDayStrip(json: stripBody)
            myTimeError = nil
        } catch {
            // The previous numbers stay on screen; only the error line is new.
            myTimeError = S("agent.mytime.error")
        }
    }

    /// The ribbon and app list for one day — the one request a tap on the
    /// strip is allowed to cost. Never re-fetches the strip: a child's phone
    /// is the most likely of the three surfaces to be on a metered connection.
    public func selectMyDay(_ day: String) async {
        guard !myTimeBusy else { return }
        myTimeBusy = true
        defer { myTimeBusy = false }
        guard let credentials = credentials.load() else { return }
        let client = AgentClient(baseURL: credentials.baseURL, transport: transport)
        let zone = TimeZone.current.identifier

        do {
            let dayBody = try await client.myUsage(
                token: credentials.token, from: day, to: day, bucket: "hour", tz: zone
            )
            // Through the core: a captcha page throws here rather than becoming
            // an empty day that tells a child they used nothing.
            myDay = try parseDayDetail(json: dayBody)
            mySelectedDay = day
            myTimeError = nil
        } catch {
            // The previous numbers stay on screen; only the error line is new.
            myTimeError = S("agent.mytime.error")
        }
    }

    public func unpair() {
        try? credentials.clear()
        monitoring.stop()
        refresh()
    }

    /// Chosen when a parent says "this is my own phone". Nothing is enrolled and
    /// no Screen Time access is asked for.
    /// A parent signing out of their own phone drops back to the role question:
    /// the phone might be handed on, and "which phone is this" is the first
    /// thing to re-ask.
    public func forgetRole() {
        roles.clear()
        refresh()
    }

    public func becomeParentDevice() {
        roles.save(.parent)
        refresh()
    }

    /// Step one of setting up a child's phone: the parent signs in here, and the
    /// session stays in memory only.
    public func signInForChildSetup(server: String, email: String, password: String) async -> Bool {
        guard let url = Self.normalisedServer(server) else {
            lastError = String(localized: "pairing.badserver")
            return false
        }

        isBusy = true
        lastError = nil
        defer { isBusy = false }

        guard let cookie = await ChildSetup.signIn(
            baseURL: url, transport: transport, email: email, password: password
        ) else {
            lastError = String(localized: "agent.setup.badlogin")
            return false
        }

        let setup = ChildSetup(baseURL: url, transport: transport, sessionCookie: cookie)
        do {
            setupChildren = try await setup.children()
        } catch {
            lastError = String(localized: "agent.setup.nochildren")
            await setup.endSession()
            return false
        }

        setupSession = (url, cookie, email)
        return true
    }

    /// Step two: claim this phone for the chosen child, then end the parent
    /// session. Order matters — a phone must never be left holding a parent
    /// session, and a failed claim must leave nothing behind at all.
    public func finishChildSetup(childId: String, label: String) async -> Bool {
        guard let session = setupSession else { return false }

        isBusy = true
        lastError = nil
        defer { isBusy = false }

        let setup = ChildSetup(
            baseURL: session.baseURL, transport: transport, sessionCookie: session.cookie
        )
        do {
            let enrolled = try await setup.claim(
                childId: childId,
                platform: "ios",
                model: DeviceInfo.model,
                label: label.isEmpty ? DeviceInfo.name : label
            )
            try credentials.save(
                AgentCredentials(
                    baseURL: session.baseURL,
                    deviceId: enrolled.deviceId,
                    token: enrolled.token,
                    parentEmail: session.email
                )
            )
        } catch {
            lastError = String(localized: "pairing.failed")
            return false
        }

        await setup.endSession()
        setupSession = nil
        setupChildren = []
        roles.save(.child)
        try? monitoring.start()
        refresh()
        return true
    }

    public func cancelChildSetup() async {
        if let session = setupSession {
            await ChildSetup(
                baseURL: session.baseURL, transport: transport, sessionCookie: session.cookie
            ).endSession()
        }
        setupSession = nil
        setupChildren = []
        refresh()
    }

    /// Leaving child mode needs the parent password, checked against the server.
    /// Otherwise "child mode" is a screen a child taps their way out of.
    public func leaveChildMode(password: String) async -> Bool {
        guard let stored = credentials.load(), let email = stored.parentEmail else {
            // No account on file to check against — an older enrolment. Refusing
            // outright would trap the phone, so fall back to unpairing, which
            // the parent can see happen in their dashboard.
            unpair()
            roles.clear()
            refresh()
            return true
        }

        isBusy = true
        lastError = nil
        defer { isBusy = false }

        guard let cookie = await ChildSetup.signIn(
            baseURL: stored.baseURL, transport: transport, email: email, password: password
        ) else {
            lastError = String(localized: "agent.unlock.wrong")
            return false
        }

        await ChildSetup(baseURL: stored.baseURL, transport: transport, sessionCookie: cookie)
            .endSession()
        unpair()
        roles.clear()
        refresh()
        return true
    }

    private var sync: AgentSync {
        AgentSync(store: store, inbox: inbox, credentials: credentials, transport: transport)
    }

    /// Accepts "server.example.ch", "https://server.example.ch" and a trailing
    /// slash, because that is what people type.
    public static func normalisedServer(_ input: String) -> URL? {
        let trimmed = input.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        let withScheme = trimmed.contains("://") ? trimmed : "https://\(trimmed)"
        guard let url = URL(string: withScheme),
              let host = url.host, host.contains("."),
              url.scheme == "https" || url.scheme == "http" else { return nil }
        return URL(string: withScheme.hasSuffix("/") ? String(withScheme.dropLast()) : withScheme)
    }
}
