import Foundation

/// One hour of usage as the device stores it, mirroring the Rust core's
/// `PendingHourFfi`.
///
/// This is deliberately *not* a second definition of the wire format: the JSON
/// that goes to the server is built by the core (`ingestBody`), so Android and
/// iOS cannot drift apart. This type exists only because the report extension
/// and the app hand rows to each other through a file, and `PendingHourFfi` is
/// generated code without `Codable`.
struct PendingHour: Codable, Equatable, Sendable {
    var hourStartMillis: Int64
    var tz: String
    var computedAtMillis: Int64
    var screenOnMs: Int64
    var unlockCount: Int32
    var apps: [PendingApp]

    /// How much of this hour is filled in. Used to decide which of two versions
    /// of the same hour to keep — see `HourStore.merge`.
    var weight: Int64 { max(screenOnMs, apps.reduce(0) { $0 + $1.foregroundMs }) }
}

struct PendingApp: Codable, Equatable, Sendable {
    var package: String
    var label: String
    var foregroundMs: Int64
    var launchCount: Int32
}
