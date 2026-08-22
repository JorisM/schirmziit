import Foundation
@testable import SchirmziitKit

/// 2026-08-22 10:00:00 UTC — a fixed hour so nothing in these tests depends on
/// the clock.
let hour: Int64 = 1_787_997_600_000
let anHour: Int64 = 3_600_000

func pendingHour(
    at millis: Int64 = hour,
    screenOn: Int64 = 600_000,
    computedAt: Int64 = hour + anHour,
    apps: [PendingApp] = [PendingApp(package: "com.a", label: "App A", foregroundMs: 600_000, launchCount: 2)]
) -> PendingHour {
    PendingHour(
        hourStartMillis: millis,
        tz: "Europe/Zurich",
        computedAtMillis: computedAt,
        screenOnMs: screenOn,
        unlockCount: 3,
        apps: apps
    )
}

func temporaryDirectory() -> URL {
    let url = FileManager.default.temporaryDirectory
        .appendingPathComponent("schirmziit-tests-\(UUID().uuidString)", isDirectory: true)
    try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
    return url
}

/// Records what was sent and replies with whatever the test scripted.
final class StubTransport: Transport, @unchecked Sendable {
    private let lock = NSLock()
    private var replies: [Result<HttpResponse, Error>]
    private(set) var sent: [HttpRequest] = []

    init(replies: [Result<HttpResponse, Error>]) {
        self.replies = replies
    }

    convenience init(status: Int, body: String, headers: [String: String] = [:]) {
        self.init(replies: [
            .success(HttpResponse(status: status, body: Data(body.utf8), headers: headers))
        ])
    }

    /// Scripted replies in order, as (status, body, headers).
    convenience init(_ replies: [(Int, String, [String: String])]) {
        self.init(replies: replies.map {
            .success(HttpResponse(status: $0.0, body: Data($0.1.utf8), headers: $0.2))
        })
    }

    func send(_ request: HttpRequest) async throws -> HttpResponse {
        try lock.withLock {
            sent.append(request)
            guard !replies.isEmpty else { return HttpResponse(status: 500, body: Data()) }
            return try replies.removeFirst().get()
        }
    }
}


/// Records whether monitoring was ever started — the thing that decides whether
/// a phone is recording at all.
final class SpyMonitoring: UsageMonitoring, @unchecked Sendable {
    private let lock = NSLock()
    private(set) var started = 0
    private(set) var stopped = 0

    func start() throws { lock.withLock { started += 1 } }
    func stop() { lock.withLock { stopped += 1 } }
}
