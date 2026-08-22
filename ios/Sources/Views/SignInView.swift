import SwiftUI

struct SignInView: View {
    let client: ApiClient
    let onSignedIn: (URL) -> Void

    @State private var server = Prefill.server
    @State private var email = Prefill.email
    @State private var password = Prefill.password
    @State private var busy = false
    @State private var errorText: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("signin.intro")
                        .font(.footnote)
                        .foregroundStyle(Palette.inkMuted)
                }

                Section("signin.server") {
                    TextField("signin.server.placeholder", text: $server)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                }

                Section("signin.account") {
                    TextField("signin.email", text: $email)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.emailAddress)
                        .textContentType(.username)
                    SecureField("signin.password", text: $password)
                        .textContentType(.password)
                }

                if let errorText {
                    Section {
                        Label(errorText, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(Palette.urgent)
                    }
                }

                Section {
                    Button {
                        Task { await signIn() }
                    } label: {
                        HStack {
                            Spacer()
                            Text(busy ? "signin.working" : "signin.submit").fontWeight(.semibold)
                            if busy { ProgressView().padding(.leading, 6) }
                            Spacer()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(busy || email.isEmpty || password.isEmpty)
                }
                .listRowBackground(Color.clear)
            }
            .schirmziitList()
            .navigationTitle("app.name")
            .task {
                // Debug builds only: lets a screenshot run or a UI test sign in
                // without tapping. Absent in a release build and in production.
                if Prefill.autoSignIn { await signIn() }
            }
        }
    }

    private func signIn() async {
        guard let url = URL(string: server.trimmingCharacters(in: .whitespaces)), url.scheme != nil else {
            errorText = String(localized: "signin.bad.server")
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
            errorText = nil
            onSignedIn(url)
        } catch let ApiError.problem(problem) {
            errorText = problem.status == 401
                ? String(localized: "signin.wrong")
                : problem.detail
        } catch {
            errorText = String(localized: "error.offline")
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
private let defaultServer = "https://schirmziit.jorisda.ch"

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
