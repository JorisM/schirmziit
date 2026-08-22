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

/// Stored in the App Group defaults rather than the app's own, so the role
/// survives what the extensions do and stays readable from them.
public struct DefaultsRoleStore: RoleStore {
    private static let key = "ch.jorisda.schirmziit.role"
    private let defaults: UserDefaults

    public init(defaults: UserDefaults? = nil) {
        self.defaults = defaults
            ?? UserDefaults(suiteName: GroupContainer.identifier)
            ?? .standard
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
