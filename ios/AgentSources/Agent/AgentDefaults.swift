import Foundation

public enum AgentDefaults {
    /// The instance this build points at by default. A family running their own
    /// server types their address over it; prefilling the common case saves a
    /// child typing a URL on a phone keyboard, which is where pairing usually
    /// goes wrong.
    public static let server = "https://api.schirmziit.ch"
}
