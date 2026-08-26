import Foundation

/// Mirrors of the server's response types. Hand-written on purpose: the parent
/// read endpoints are not part of `core::wire`, so there is nothing to generate
/// from. `ContractTests` decodes captured responses to catch drift.
struct MeResponse: Codable, Sendable {
    let id: String
    let email: String
    let familyId: String
}

struct ChildResponse: Codable, Sendable, Identifiable {
    let id: String
    let displayName: String
    let todayMs: Int64
}

struct DeviceStatus: Codable, Sendable, Identifiable {
    let id: String
    let label: String
    let lastSeenAt: Date?
    let stale: Bool
}

struct UsagePoint: Codable, Sendable {
    /// RFC3339 for hourly buckets, `YYYY-MM-DD` for daily ones.
    let start: String
    let foregroundMs: Int
    let launchCount: Int
}

struct UsageSeries: Codable, Sendable, Identifiable {
    let package: String
    let label: String
    let points: [UsagePoint]

    var id: String { package }
    var totalMs: Int { points.reduce(0) { $0 + $1.foregroundMs } }
    var launches: Int { points.reduce(0) { $0 + $1.launchCount } }
}

struct DeviceTotal: Codable, Sendable {
    let start: String
    let screenOnMs: Int
    let unlockCount: Int
}

struct UsageResponse: Codable, Sendable {
    let childId: String
    let from: String
    let to: String
    let bucket: String
    let tz: String
    let devices: [DeviceStatus]
    let series: [UsageSeries]
    let deviceTotals: [DeviceTotal]

    var screenTimeMs: Int { series.reduce(0) { $0 + $1.totalMs } }
    var unlocks: Int { deviceTotals.reduce(0) { $0 + $1.unlockCount } }
}

/// RFC 9457 problem+json, as the server sends it.
struct ApiProblem: Codable, Sendable, Error {
    let type: String
    let title: String
    let status: Int
    let detail: String
}
