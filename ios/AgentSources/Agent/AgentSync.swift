import Foundation

struct SyncOutcome: Equatable, Sendable {
    var sent: Int
    var remaining: Int

    static let idle = SyncOutcome(sent: 0, remaining: 0)
}

enum AgentSyncError: Error, Equatable {
    case notPaired
}

/// Drain snapshots, hand them to the core, upload what the core selects, keep
/// what the server did not accept.
///
/// Every decision that could lose a child's day — which hours to send, which to
/// keep after a partial rejection — is made by the shared Rust core, not here.
struct AgentSync: Sendable {
    /// One request carries at most two days of hours; the rest waits for the
    /// next run. Matches the server's per-device rate limit budget.
    static let maxRows: UInt32 = 48
    static let maxBytes: UInt32 = 256 * 1024

    let store: HourStore
    let inbox: SnapshotInbox
    let credentials: CredentialStore
    let transport: Transport
    var now: @Sendable () -> Date = { Date() }

    /// Folds anything the extension left behind into the queue. Safe to call
    /// without network.
    func collect() throws {
        let snapshots = inbox.drain()
        guard !snapshots.isEmpty else { return }
        try store.merge(snapshots.map { $0.pendingHour() })
    }

    @discardableResult
    func run() async throws -> SyncOutcome {
        try collect()

        guard let credentials = credentials.load() else { throw AgentSyncError.notPaired }

        let pending = try store.pending()
        guard !pending.isEmpty else { return .idle }

        let plan = planNextSync(
            pending: pending.map(\.ffi),
            maxRows: Self.maxRows,
            maxBytes: Self.maxBytes
        )
        guard !plan.send.isEmpty else { return SyncOutcome(sent: 0, remaining: pending.count) }

        let body = try ingestBody(
            hours: plan.send,
            deviceTimeMillis: Int64(now().timeIntervalSince1970 * 1000)
        )
        let client = AgentClient(baseURL: credentials.baseURL, transport: transport)
        let response = try await client.ingest(token: credentials.token, body: body)

        // Throws on a captcha page or a proxy error rather than treating an
        // unparseable body as "all accepted" and dropping the rows.
        let keptFromSend = try applyIngestResult(pending: plan.send, responseJson: response)

        let remaining = (keptFromSend + plan.deferred).map(PendingHour.init(ffi:))
        try store.replace(with: remaining)

        return SyncOutcome(sent: plan.send.count - keptFromSend.count, remaining: remaining.count)
    }
}

extension PendingHour {
    var ffi: PendingHourFfi {
        PendingHourFfi(
            hourStartMillis: hourStartMillis,
            tz: tz,
            computedAtMillis: computedAtMillis,
            screenOnMs: screenOnMs,
            unlockCount: unlockCount,
            apps: apps.map {
                PendingAppFfi(
                    package: $0.package,
                    label: $0.label,
                    foregroundMs: $0.foregroundMs,
                    launchCount: $0.launchCount
                )
            }
        )
    }

    init(ffi: PendingHourFfi) {
        self.init(
            hourStartMillis: ffi.hourStartMillis,
            tz: ffi.tz,
            computedAtMillis: ffi.computedAtMillis,
            screenOnMs: ffi.screenOnMs,
            unlockCount: ffi.unlockCount,
            apps: ffi.apps.map {
                PendingApp(
                    package: $0.package,
                    label: $0.label,
                    foregroundMs: $0.foregroundMs,
                    launchCount: $0.launchCount
                )
            }
        )
    }
}
