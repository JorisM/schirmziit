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
    /// The last `schirmziit://enroll` link this phone was handed, if it was one
    /// this phone can act on. Held here rather than in the screen that uses it:
    /// the link arrives before that screen exists.
    @State private var scanned: EnrollLink?

    public init() {}

    public var body: some View {
        Group {
            switch agent.role {
            case .child:
                AgentRootView(model: agent, scanned: scanned)

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
                    ChildSetupView(model: agent, server: scanned?.server) { settingUpChild = false }
                } else {
                    RoleChoiceView(
                        onParent: { agent.becomeParentDevice() },
                        onChild: { settingUpChild = true }
                    )
                }
            }
        }
        .tint(Palette.accent)
        // The system camera reads the square a parent minted and offers to open
        // this app with it. What happens next depends on what this phone
        // already is — `EnrollLinkRoute` decides, and a link that means nothing
        // here changes nothing here.
        .onOpenURL { url in
            switch EnrollLinkRoute.decide(role: agent.role, link: EnrollLink(url)) {
            case .fillPairingForm(let link):
                scanned = link
            case .startChildSetup(let link):
                scanned = link
                settingUpChild = true
            case .ignore:
                break
            }
        }
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
