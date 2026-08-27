import SwiftUI

/// What an error says, in the phone's language.
///
/// Reads the generated `ErrorCopy.strings`, which `just gen-copy` writes from
/// `copy/errors.toml` — the same source the dashboard and the Android agent read.
/// Nothing here is hand-written: a sentence edited in this file would be a fifth
/// version of copy that already exists in four places.
enum ErrorCopy {
    /// The key to render, with the fallback already applied.
    ///
    /// Split from the rendering so the view can build a `Text` that resolves
    /// against the SwiftUI locale environment. `Bundle.localizedString` reads the
    /// *process* locale instead, which is correct on a real phone but renders
    /// English into every German snapshot — and the snapshots are the only place
    /// anyone reviews these translations before a parent reads them.
    static func titleKey(for code: ErrorCode) -> String { key(code, "title") }
    static func actionKey(for code: ErrorCode) -> String { key(code, "action") }

    /// Resolves against the environment locale, unlike the `String` accessors.
    static func text(_ key: String) -> Text {
        Text(LocalizedStringKey(key), tableName: table, bundle: .schirmziitKit)
    }

    /// The process-locale reading, for the places that need a plain `String`:
    /// the tests, and anything outside a view hierarchy.
    static func title(for code: ErrorCode) -> String { resolve(titleKey(for: code)) }
    static func action(for code: ErrorCode) -> String { resolve(actionKey(for: code)) }

    /// Not every failure deserves red. An offline phone in a Swiss valley
    /// painting the screen red teaches a parent to ignore the colour that means
    /// something actually broke.
    ///
    /// Must agree with `weight` in `copy/errors.toml`; `ErrorCopyTests` asserts
    /// exactly that, because two sources of truth for one fact is how they drift.
    static func isUrgent(_ code: ErrorCode) -> Bool {
        switch code {
        case .unauthenticated, .sessionExpired, .registrationDisabled, .notFound,
             .childNotFound, .pairingCodeExpired, .rateLimited, .offline, .timeout,
             .notificationPermissionMissing, .mediaNotificationAccessMissing,
             .backgroundRefreshDisabled:
            false
        default:
            true
        }
    }

    /// A code with no iOS copy means the catalog's `reach` and this app disagree.
    /// Falling back to the server-error wording keeps something readable and
    /// reportable on screen; falling back to the raw key would put
    /// `error.SZ-E603.title` in front of a parent.
    private static func key(_ code: ErrorCode, _ part: String) -> String {
        let candidate = "error.\(code.wire).\(part)"
        let text = Bundle.schirmziitKit.localizedString(forKey: candidate, value: missing, table: table)
        return text == missing ? "error.SZ-E901.\(part)" : candidate
    }

    private static func resolve(_ key: String) -> String {
        Bundle.schirmziitKit.localizedString(forKey: key, value: "", table: table)
    }

    private static let table = "ErrorCopy"
    private static let missing = "\u{0}missing"
}
