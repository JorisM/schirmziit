import DeviceActivity
import SwiftUI

/// The only place on iOS where per-app durations exist.
///
/// A report extension is a SwiftUI scene: the system hands it the usage data
/// while its view is on screen, and it has no network access. So the totals are
/// written to the shared container here and uploaded by the app.
@main
struct SchirmziitReport: DeviceActivityReportExtension {
    var body: some DeviceActivityReportScene {
        HourlyTotalsScene { snapshot in
            // Nothing to look at: the app renders this report off-screen purely
            // to make iOS compute the numbers.
            Text(verbatim: "\(snapshot.apps.count)")
        }
    }
}

struct HourlyTotalsScene: DeviceActivityReportScene {
    /// Must match `HourlyTotalsContext.hourly` in the app.
    static let contextName = DeviceActivityReport.Context("hourly")

    let context = HourlyTotalsScene.contextName
    let content: (UsageSnapshot) -> Text
    var inbox = SnapshotInbox()

    init(content: @escaping (UsageSnapshot) -> Text) {
        self.content = content
    }

    func makeConfiguration(representing data: DeviceActivityResults<DeviceActivityData>) async -> UsageSnapshot {
        // Read out flat and folded afterwards: what iOS reports is one entry
        // per app per category per segment, and deciding an app's identity
        // while reading means deciding it from the first entry that mentions
        // it. `SnapshotApp.fold` is where that judgement lives, because this
        // loop is the one part of the extension no test can reach.
        var reported: [ReportedApp] = []
        var screenOn: TimeInterval = 0
        var pickups = 0

        for await result in data {
            for await segment in result.activitySegments {
                screenOn += segment.totalActivityDuration
                pickups += segment.totalPickupsWithoutApplicationActivity
                for await category in segment.categories {
                    for await application in category.applications {
                        reported.append(ReportedApp(
                            bundleId: application.application.bundleIdentifier,
                            name: application.application.localizedDisplayName,
                            durationMs: Int64(application.totalActivityDuration * 1000),
                            launchCount: Int32(application.numberOfPickups)
                        ))
                    }
                }
            }
        }

        let snapshot = UsageSnapshot(
            hourStartMillis: HourMarker().read() ?? Self.currentHourStartMillis(),
            tz: TimeZone.current.identifier,
            computedAtMillis: Int64(Date().timeIntervalSince1970 * 1000),
            screenOnMs: Int64(screenOn * 1000),
            pickups: Int32(pickups),
            apps: SnapshotApp.fold(reported)
        )
        try? inbox.write(snapshot)
        return snapshot
    }

    static func currentHourStartMillis(now: Date = Date()) -> Int64 {
        let start = Calendar.current.dateInterval(of: .hour, for: now)?.start ?? now
        return Int64(start.timeIntervalSince1970 * 1000)
    }
}
