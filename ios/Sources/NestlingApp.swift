import SwiftUI

@main
struct NestlingApp: App {
    @State private var client = ApiClient()
    @AppStorage("serverURL") private var storedServer: String = ""
    @State private var signedIn = false

    var body: some Scene {
        WindowGroup {
            Group {
                if signedIn {
                    ChildrenView(client: client) {
                        signedIn = false
                        storedServer = ""
                    }
                } else {
                    SignInView(client: client) { url in
                        storedServer = url.absoluteString
                        signedIn = true
                    }
                }
            }
            .tint(Palette.accent)
            .task {
                // A remembered server plus a live session cookie means we can go
                // straight in; anything else falls back to the sign-in form.
                guard let url = URL(string: storedServer), !storedServer.isEmpty else { return }
                await client.configure(baseURL: url)
                signedIn = (try? await client.get("v1/me", as: MeResponse.self)) != nil
            }
        }
    }
}
