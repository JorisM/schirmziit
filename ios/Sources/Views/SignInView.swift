import SwiftUI

struct SignInView: View {
    let client: ApiClient
    let onSignedIn: (URL) -> Void

    @State private var server = Prefill.server
    @State private var email = Prefill.email
    @State private var password = Prefill.password
    @State private var busy = false
    @State private var error: AppError?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    L("signin.intro")
                        .font(.footnote)
                        .foregroundStyle(Palette.inkMuted)
                }

                Section(header: L("signin.server")) {
                    TextField(S("signin.server.placeholder"), text: $server)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .textContentType(.URL)
                }

                Section(header: L("signin.account")) {
                    TextField(S("signin.email"), text: $email)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.emailAddress)
                        .textContentType(.username)
                    SecureField(S("signin.password"), text: $password)
                        .textContentType(.password)
                }

                if let error {
                    Section { ErrorView(error: error) }
                }

                Section {
                    Button {
                        Task { await signIn() }
                    } label: {
                        HStack {
                            Spacer()
                            L(busy ? "signin.working" : "signin.submit").fontWeight(.semibold)
                            if busy { ProgressView().padding(.leading, 6) }
                            Spacer()
                        }
                    }
                    .primaryAction()
                    .disabled(busy || email.isEmpty || password.isEmpty)
                }
                .listRowBackground(Color.clear)
            }
            .schirmziitList()
            .navigationTitle(L("app.name"))
            .task {
                // Debug builds only: lets a screenshot run or a UI test sign in
                // without tapping. Absent in a release build and in production.
                if Prefill.autoSignIn { await signIn() }
            }
        }
    }

    private func signIn() async {
        guard let url = URL(string: server.trimmingCharacters(in: .whitespaces)), url.scheme != nil else {
            // Not a network failure: there is no address to reach yet.
            error = AppError.local(.baseUrlNotConfigured)
            return
        }
        busy = true
        defer { busy = false }

        await client.configure(baseURL: url)
        do {
            // The session cookie is stored by URLSession's shared cookie jar, so
            // later requests are authenticated without holding a token here.
            _ = try await client.post(
                "v1/auth/login",
                body: ["email": email, "password": password],
                as: LoginAck.self
            )
            error = nil
            onSignedIn(url)
        } catch let caught as AppError {
            // SZ-E101 already says "that email or password is wrong" in four
            // languages, so the view keeps no sentence of its own.
            error = caught
        } catch {
            self.error = AppError.transport(error, endpoint: "v1/auth/login")
        }
    }
}

/// The server answers `{"ok":true}`; the value is not used beyond "it worked".
private struct LoginAck: Codable, Sendable {
    let ok: Bool?
}


/// Values a debug run can inject through the environment, e.g.
/// `SIMCTL_CHILD_SCHIRMZIIT_SERVER=… xcrun simctl launch …`.
/// The instance this build points at by default. Self-hosters replace it in the
/// field; it is prefilled because typing a URL on a phone is where sign-in
/// usually goes wrong.
private let defaultServer = "https://api.schirmziit.ch"

private enum Prefill {
#if DEBUG
    private static func value(_ key: String) -> String? {
        ProcessInfo.processInfo.environment[key]
    }

    static var server: String { value("SCHIRMZIIT_SERVER") ?? defaultServer }
    static var email: String { value("SCHIRMZIIT_EMAIL") ?? "" }
    static var password: String { value("SCHIRMZIIT_PASSWORD") ?? "" }
    static var autoSignIn: Bool { value("SCHIRMZIIT_AUTOLOGIN") == "1" }
#else
    static let server = defaultServer
    static let email = ""
    static let password = ""
    static let autoSignIn = false
#endif
}
