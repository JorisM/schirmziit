import BackgroundTasks
import Foundation

/// The 30-minute cadence, as far as iOS allows one.
///
/// iOS decides when a background task actually runs — the interval is a floor,
/// not a promise. Nothing is lost when it does not fire: hours queue on the
/// phone and go out on the next run or the next time the app is opened.
public enum BackgroundSync {
    public static let identifier = "ch.jorisda.schirmziit.sync"
    public static let interval: TimeInterval = 30 * 60

    /// Call once at launch, before the app finishes starting up.
    @MainActor
    public static func register(model: AgentModel) {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            schedule()
            let work = Task { @MainActor in
                await model.syncNow()
                task.setTaskCompleted(success: true)
            }
            task.expirationHandler = { work.cancel() }
        }
        schedule()
    }

    public static func schedule(after seconds: TimeInterval = interval) {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: seconds)
        // Throws when the identifier is not in the Info.plist or in a simulator
        // that has background refresh switched off; neither is worth crashing on.
        try? BGTaskScheduler.shared.submit(request)
    }
}
