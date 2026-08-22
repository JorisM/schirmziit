import SwiftUI

/// One app, two roles.
///
/// The role is asked first, and then only the login that role needs: a parent
/// signing in for their own dashboard, or a parent signing in once on a child's
/// phone to enrol it. A phone in child mode never shows the dashboard again
/// without the parent password.
public struct RootView: View {
    @State private var agent = AgentModel()
    @State private var client = ApiClient()
    @AppStorage("serverURL") private var storedServer: String = ""
    @State private var signedIn = false
    @State private var settingUpChild = false

    public init() {}

    public var body: some View {
        Group {
            switch agent.role {
            case .child:
                AgentRootView(model: agent)

            case .parent:
                if signedIn {
                    ChildrenView(client: client) {
                        signedIn = false
                        storedServer = ""
                        agent.forgetRole()
                    }
                } else {
                    SignInView(client: client) { url in
                        storedServer = url.absoluteString
                        signedIn = true
                    }
                }

            case .none:
                if settingUpChild {
                    ChildSetupView(model: agent) { settingUpChild = false }
                } else {
                    RoleChoiceView(
                        onParent: { agent.becomeParentDevice() },
                        onChild: { settingUpChild = true }
                    )
                }
            }
        }
        .tint(Palette.accent)
        .task {
            // A remembered server plus a live session means straight into the
            // dashboard; anything else falls back to the form.
            guard agent.role == .parent,
                  let url = URL(string: storedServer), !storedServer.isEmpty else { return }
            await client.configure(baseURL: url)
            signedIn = (try? await client.get("v1/me", as: MeResponse.self)) != nil
        }
    }
}
