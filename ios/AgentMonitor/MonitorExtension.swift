import DeviceActivity
import Foundation

/// Wakes up on every hour boundary and records which hour just finished.
///
/// This extension cannot see per-app durations and cannot reach the network —
/// that is what the report extension and the app are for. All it does is leave a
/// marker so the app knows there is a completed hour to render and upload.
final class SchirmziitMonitor: DeviceActivityMonitor {
    private let marker = HourMarker()

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        marker.write(hourStartMillis: Self.startOfCurrentHourMillis())
    }

    static func startOfCurrentHourMillis(now: Date = Date()) -> Int64 {
        // The interval that just ended is the hour containing `now` minus a
        // second — at 09:59:59 the finished hour started at 09:00.
        let inside = now.addingTimeInterval(-1)
        let start = Calendar.current.dateInterval(of: .hour, for: inside)?.start ?? inside
        return Int64(start.timeIntervalSince1970 * 1000)
    }
}
