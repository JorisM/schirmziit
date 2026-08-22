import Foundation

/// What this phone is. Chosen once, at setup, by whoever is holding it — which
/// is a parent in both cases: a child's phone is set up by signing in with the
/// parent account, and the app then drops that session.
public enum AppRole: String, Codable, Sendable {
    case parent
    case child
}

public protocol RoleStore: Sendable {
    func load() -> AppRole?
    func save(_ role: AppRole)
    func clear()
}

/// Stored in the App Group defaults when that works, so the extensions can read
/// it too — and in the app's own defaults when it does not.
///
/// The distinction is not theoretical: a build signed without the App Group
/// entitlement still gets a non-nil suite from `UserDefaults(suiteName:)`, and
/// every write to it is silently dropped. The role then never persisted, so
/// finishing child setup left the app with no role and it fell straight back to
/// the setup screen — with the fields still filled, having already enrolled a
/// device. Three of them, before this was found.
public struct DefaultsRoleStore: RoleStore {
    private static let key = "ch.jorisda.schirmziit.role"
    private let defaults: UserDefaults

    public init(defaults: UserDefaults? = nil) {
        self.defaults = defaults ?? Self.persistentStore()
    }

    /// Trust nothing: write a probe and read it back.
    static func persistentStore(
        groupIdentifier: String = GroupContainer.identifier,
        fallback: UserDefaults = .standard
    ) -> UserDefaults {
        guard let suite = UserDefaults(suiteName: groupIdentifier) else { return fallback }
        let probe = "\(key).probe"
        suite.set(true, forKey: probe)
        let persisted = suite.bool(forKey: probe)
        suite.removeObject(forKey: probe)
        return persisted ? suite : fallback
    }

    public func load() -> AppRole? {
        defaults.string(forKey: Self.key).flatMap(AppRole.init(rawValue:))
    }

    public func save(_ role: AppRole) {
        defaults.set(role.rawValue, forKey: Self.key)
    }

    public func clear() {
        defaults.removeObject(forKey: Self.key)
    }
}

/// For tests and previews.
public final class InMemoryRoleStore: RoleStore, @unchecked Sendable {
    private let lock = NSLock()
    private var role: AppRole?

    public init(_ role: AppRole? = nil) {
        self.role = role
    }

    public func load() -> AppRole? { lock.withLock { role } }
    public func save(_ role: AppRole) { lock.withLock { self.role = role } }
    public func clear() { lock.withLock { role = nil } }
}
