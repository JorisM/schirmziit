import DeviceActivity
import SwiftUI

/// The thing that makes the whole pipeline run.
///
/// A report extension only computes usage while a `DeviceActivityReport` view
/// exists — there is no "fetch usage" call on iOS. So the status screen keeps one
/// on screen, sized down to nothing: rendering it is what causes the extension to
/// write the snapshot the app then uploads.
struct UsageProbeView: View {
    /// Which hour to ask about. The finished hour by default, because the
    /// current one keeps changing while we look at it.
    var interval: DateInterval = UsageProbeView.lastFinishedHour()

    var body: some View {
        DeviceActivityReport(
            HourlyTotalsContext.hourly,
            filter: DeviceActivityFilter(
                segment: .hourly(during: interval),
                users: .all,
                devices: .init([.iPhone])
            )
        )
        .frame(width: 1, height: 1)
        .opacity(0.01)
        .accessibilityHidden(true)
    }

    static func lastFinishedHour(now: Date = Date()) -> DateInterval {
        let calendar = Calendar.current
        let currentHour = calendar.dateInterval(of: .hour, for: now)?.start ?? now
        let start = calendar.date(byAdding: .hour, value: -1, to: currentHour) ?? currentHour
        return DateInterval(start: start, end: currentHour)
    }
}

/// The context name has to match the report extension's scene. Kept in the
/// framework so the two sides cannot drift.
enum HourlyTotalsContext {
    static let hourly = DeviceActivityReport.Context("hourly")
}
