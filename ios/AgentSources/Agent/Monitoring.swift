@preconcurrency import DeviceActivity
import Foundation

protocol UsageMonitoring: Sendable {
    func start() throws
    func stop()
}

/// Asks iOS to wake the monitor extension on every hour boundary.
///
/// The schedule is what makes the pipeline run at all: the report extension only
/// computes totals while a report view exists, and this is what tells the app
/// there is a finished hour worth rendering.
struct DeviceActivityMonitoring: UsageMonitoring {
    static let activity = DeviceActivityName("ch.jorisda.schirmziit.hourly")

    func start() throws {
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(minute: 0),
            intervalEnd: DateComponents(minute: 59, second: 59),
            repeats: true
        )
        try DeviceActivityCenter().startMonitoring(Self.activity, during: schedule)
    }

    func stop() {
        DeviceActivityCenter().stopMonitoring([Self.activity])
    }
}
