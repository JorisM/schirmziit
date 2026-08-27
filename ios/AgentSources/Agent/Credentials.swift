import Foundation

struct AgentCredentials: Codable, Equatable, Sendable {
    var baseURL: URL
    var deviceId: String
    var token: String
    /// Whose password unlocks child mode. Kept in the keychain, never shown:
    /// without it a child's phone cannot check an unlock against the server, and
    /// child mode would either be trivially reversible or permanent.
    var parentEmail: String?

    init(baseURL: URL, deviceId: String, token: String, parentEmail: String? = nil) {
        self.baseURL = baseURL
        self.deviceId = deviceId
        self.token = token
        self.parentEmail = parentEmail
    }
}

protocol CredentialStore: Sendable {
    func load() -> AgentCredentials?
    func save(_ credentials: AgentCredentials) throws
    func clear() throws
}

enum CredentialStoreError: Error, Equatable {
    case keychain(OSStatus)
}

/// The device token is a long-lived write credential, so it lives in the
/// keychain rather than in UserDefaults. `ThisDeviceOnly` because a restored
/// backup on a different phone must re-pair instead of silently reporting as the
/// old device.
struct KeychainCredentialStore: CredentialStore {
    let service: String

    init(service: String = "ch.jorisda.schirmziit.agent") {
        self.service = service
    }

    private var query: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "device",
        ]
    }

    func load() -> AgentCredentials? {
        var lookup = query
        lookup[kSecReturnData as String] = true
        lookup[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        guard SecItemCopyMatching(lookup as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return try? JSONDecoder().decode(AgentCredentials.self, from: data)
    }

    func save(_ credentials: AgentCredentials) throws {
        let data = try JSONEncoder().encode(credentials)
        SecItemDelete(query as CFDictionary)

        var insert = query
        insert[kSecValueData as String] = data
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(insert as CFDictionary, nil)
        guard status == errSecSuccess else { throw CredentialStoreError.keychain(status) }
    }

    func clear() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw CredentialStoreError.keychain(status)
        }
    }
}

/// Used by the tests and by SwiftUI previews.
final class InMemoryCredentialStore: CredentialStore, @unchecked Sendable {
    private let lock = NSLock()
    private var stored: AgentCredentials?

    init(_ credentials: AgentCredentials? = nil) {
        stored = credentials
    }

    func load() -> AgentCredentials? {
        lock.withLock { stored }
    }

    func save(_ credentials: AgentCredentials) throws {
        lock.withLock { stored = credentials }
    }

    func clear() throws {
        lock.withLock { stored = nil }
    }
}

extension CredentialStoreError {
    /// A keychain failure is not a network failure, and a parent shown "check
    /// your connection" for one will go and check a connection that works.
    ///
    /// `save` and `clear` are both writes. A read failure surfaces as `nil`
    /// credentials instead, and is handled where the absence is noticed —
    /// see `AgentModel.leaveChildMode`.
    var code: ErrorCode {
        switch self {
        case .keychain: .keychainWriteFailed
        }
    }
}
