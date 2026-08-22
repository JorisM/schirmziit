import FamilyControls
import Foundation

enum ScreenTimeAuthorization: Equatable, Sendable {
    case notDetermined
    case approved
    case denied
    /// Screen Time reporting is not available to this build at all — the usual
    /// reason is a missing Family Controls entitlement, which Apple grants per
    /// app. The UI says so instead of looping the child through a prompt that
    /// cannot succeed.
    case unavailable(String)
}

protocol ScreenTimeAuthorizing: Sendable {
    var current: ScreenTimeAuthorization { get }
    func request() async -> ScreenTimeAuthorization
}

struct FamilyControlsAuthorizer: ScreenTimeAuthorizing {
    var current: ScreenTimeAuthorization {
        switch AuthorizationCenter.shared.authorizationStatus {
        case .notDetermined: return .notDetermined
        case .denied: return .denied
        case .approved: return .approved
        @unknown default: return .notDetermined
        }
    }

    func request() async -> ScreenTimeAuthorization {
        do {
            // `.child` is the right role: this app runs on the child's phone and
            // the parent approves it with their own passcode.
            try await AuthorizationCenter.shared.requestAuthorization(for: .child)
            return current
        } catch {
            if let error = error as? FamilyControlsError, error == .authorizationCanceled {
                return .denied
            }
            return .unavailable(String(describing: error))
        }
    }
}

/// Test double. The real authorizer cannot be driven from a unit test: it talks
/// to a system daemon and needs an entitlement.
struct StubAuthorizer: ScreenTimeAuthorizing {
    var state: ScreenTimeAuthorization
    var afterRequest: ScreenTimeAuthorization?

    var current: ScreenTimeAuthorization { state }

    func request() async -> ScreenTimeAuthorization { afterRequest ?? state }
}
