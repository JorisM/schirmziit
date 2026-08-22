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
    public private(set) var sharedContainerAvailable = GroupContainer.isShared()

    private let store: HourStore
    private let inbox: SnapshotInbox
    private let credentials: CredentialStore
    private let transport: Transport
    private let authorizer: ScreenTimeAuthorizing
    private let monitoring: UsageMonitoring
    private var lastSyncAt: Date?

    /// Production wiring: the shared container, the keychain, the real Screen
    /// Time API. The app uses this; the tests use the initialiser below.
    public convenience init() {
        self.init(
            store: FileHourStore(),
            inbox: SnapshotInbox(),
            credentials: KeychainCredentialStore(),
            transport: URLSessionTransport(),
            authorizer: FamilyControlsAuthorizer(),
            monitoring: DeviceActivityMonitoring()
        )
    }

    init(
        store: HourStore,
        inbox: SnapshotInbox,
        credentials: CredentialStore,
        transport: Transport,
        authorizer: ScreenTimeAuthorizing,
        monitoring: UsageMonitoring
    ) {
        self.store = store
        self.inbox = inbox
        self.credentials = credentials
        self.transport = transport
        self.authorizer = authorizer
        self.monitoring = monitoring
        refresh()
    }

    public var pendingCount: Int { (try? store.pending().count) ?? 0 }

    public func refresh() {
        try? sync.collect()
        status = AgentStatus.derive(
            credentials: credentials.load(),
            authorization: authorizer.current,
            pendingHours: pendingCount,
            lastSyncAt: lastSyncAt
        )
        sharedContainerAvailable = GroupContainer.isShared()
    }

    public func requestScreenTime() async {
        let result = await authorizer.request()
        if result == .approved {
            // Nothing is recorded until the schedule exists, so start it the
            // moment permission lands rather than at the next launch.
            try? monitoring.start()
        }
        refresh()
    }

    public func pair(server: String, code: String, label: String) async {
        guard let url = Self.normalisedServer(server) else {
            lastError = String(localized: "pairing.badserver")
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
            lastError = String(localized: "pairing.badcode")
        } catch {
            lastError = String(localized: "pairing.failed")
        }
        refresh()
    }

    public func syncNow() async {
        isBusy = true
        lastError = nil
        defer { isBusy = false }
        do {
            _ = try await sync.run()
            lastSyncAt = Date()
        } catch {
            lastError = String(localized: "status.sync.failed")
        }
        refresh()
    }

    public func unpair() {
        try? credentials.clear()
        monitoring.stop()
        refresh()
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
