import Foundation

/// What the one screen of this app shows. Derived, never stored: every input is
/// re-read when the app comes back to the foreground, because the child may have
/// changed a permission in Settings while we were away — the bug the Android
/// agent had on its battery-optimisation button.
public enum AgentStatus: Equatable, Sendable {
    case needsPairing
    case needsScreenTimePermission
    case screenTimeDenied
    case screenTimeUnavailable(String)
    case reporting(pendingHours: Int, lastSyncAt: Date?)
}

extension AgentStatus {
    static func derive(
        credentials: AgentCredentials?,
        authorization: ScreenTimeAuthorization,
        pendingHours: Int,
        lastSyncAt: Date?
    ) -> AgentStatus {
        // Pairing first: without a server there is nowhere to send anything, and
        // asking a child for Screen Time access before that is noise.
        guard credentials != nil else { return .needsPairing }

        switch authorization {
        case .notDetermined: return .needsScreenTimePermission
        case .denied: return .screenTimeDenied
        case .unavailable(let reason): return .screenTimeUnavailable(reason)
        case .approved: return .reporting(pendingHours: pendingHours, lastSyncAt: lastSyncAt)
        }
    }
}
