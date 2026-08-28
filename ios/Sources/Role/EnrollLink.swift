import Foundation

/// A scanned `schirmziit://enroll?url=…&code=…` link — the Swift half of
/// Android's `EnrollPayloadParser`, and the reason this phone can be pointed at
/// a camera at all: the app registers the scheme, so the system camera offers
/// to open Schirmziit and hands the link straight here.
///
/// Strict on purpose: the URL in this link decides where a child's usage data
/// goes. https only, except localhost while developing.
public struct EnrollLink: Equatable, Sendable {
    public let server: String
    public let code: String

    public init?(_ raw: String) {
        guard let components = URLComponents(string: raw.trimmingCharacters(in: .whitespaces)),
              components.scheme == "schirmziit",
              components.host == "enroll",
              let items = components.queryItems
        else { return nil }

        guard let url = items.first(where: { $0.name == "url" })?.value,
              let code = items.first(where: { $0.name == "code" })?.value,
              !code.isEmpty
        else { return nil }

        let server = url.hasSuffix("/") ? String(url.dropLast()) : url
        let local = server.hasPrefix("http://localhost") || server.hasPrefix("http://127.0.0.1")
        guard server.hasPrefix("https://") || local else { return nil }

        self.server = server
        self.code = code.uppercased()
    }

    public init?(_ url: URL) {
        self.init(url.absoluteString)
    }
}

/// What the app does with a scanned link, decided by what this phone already is.
///
/// A pure decision rather than a branch inside `onOpenURL`, because the wrong
/// answer here is the expensive one: a parent scanning a code with *their own*
/// phone must not turn it into a child's phone, and a phone already reporting
/// for a child must not be re-pointed at another one by a link.
public enum EnrollLinkRoute: Equatable, Sendable {
    /// Fill the code and the address in on the pairing screen this phone is
    /// already showing.
    case fillPairingForm(EnrollLink)
    /// No role yet: take the parent to child setup with the address filled in.
    /// The code is not usable there — this app enrols a fresh phone through a
    /// parent sign-in — but the address is the half that gets typed wrong.
    case startChildSetup(EnrollLink)
    case ignore

    public static func decide(role: AppRole?, link: EnrollLink?) -> EnrollLinkRoute {
        guard let link else { return .ignore }
        switch role {
        case .child: return .fillPairingForm(link)
        case .none: return .startChildSetup(link)
        // A parent's own phone scanning a pairing code is a parent looking at
        // the code they just minted. Repurposing the phone they read the
        // dashboard on would be the worst possible reading of that.
        case .parent: return .ignore
        }
    }
}
