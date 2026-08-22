import Foundation

/// What the DeviceActivityReport extension hands to the app.
///
/// iOS gives no per-app foreground event stream — the only source of per-app
/// durations is a report extension, and that extension is sandboxed without
/// network access. So it writes snapshots here and the app uploads them. This is
/// the whole reason the iOS pipeline looks different from Android's.
struct UsageSnapshot: Codable, Equatable, Sendable {
    var hourStartMillis: Int64
    var tz: String
    var computedAtMillis: Int64
    /// `totalActivityDuration` for the hour, in milliseconds.
    var screenOnMs: Int64
    /// Device pickups if the report exposes them; iOS has no unlock count, so
    /// this is the closest honest equivalent.
    var pickups: Int32
    var apps: [SnapshotApp]

    /// Maximum apps kept per hour. A long tail of one-second background wakeups
    /// tells a parent nothing and makes every row bigger.
    static let maxApps = 25

    func pendingHour(maxApps: Int = UsageSnapshot.maxApps) -> PendingHour {
        let ranked = apps
            .filter { $0.durationMs > 0 }
            .sorted { ($0.durationMs, $0.bundleId) > ($1.durationMs, $1.bundleId) }
            .prefix(maxApps)
            .map {
                PendingApp(
                    package: $0.bundleId,
                    // iOS withholds the display name for some apps; the bundle
                    // id is worse to read but better than an empty row.
                    label: $0.name?.isEmpty == false ? $0.name! : $0.bundleId,
                    foregroundMs: $0.durationMs,
                    launchCount: $0.launchCount
                )
            }

        // Per-app durations can add up to more than the reported total (two apps
        // on screen, split view). Trusting the smaller number would under-report
        // the day, so take whichever is larger.
        let appTotal = ranked.reduce(Int64(0)) { $0 + $1.foregroundMs }

        return PendingHour(
            hourStartMillis: hourStartMillis,
            tz: tz,
            computedAtMillis: computedAtMillis,
            screenOnMs: max(screenOnMs, appTotal),
            unlockCount: pickups,
            apps: Array(ranked)
        )
    }
}

struct SnapshotApp: Codable, Equatable, Sendable {
    var bundleId: String
    var name: String?
    var durationMs: Int64
    var launchCount: Int32 = 0
}
