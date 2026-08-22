import SwiftUI

/// Setting up a child's phone: the parent signs in here once, picks the child,
/// and the app keeps a device token instead of their session.
struct ChildSetupView: View {
    let model: AgentModel
    let onCancel: () -> Void

    @State private var server = AgentDefaults.server
    @State private var email = ""
    @State private var password = ""
    @State private var label = ""
    @State private var chosen: String?

    var body: some View {
        NavigationStack {
            Form {
                if model.setupChildren.isEmpty {
                    signInSection
                } else {
                    childSection
                }

                if let error = model.lastError {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(Palette.urgent)
                            .font(.footnote)
                    }
                }
            }
            .schirmziitList()
            .navigationTitle(L("agent.setup.title"))
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(S("agent.setup.cancel")) {
                        Task {
                            await model.cancelChildSetup()
                            onCancel()
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder private var signInSection: some View {
        Section { L("agent.setup.intro").font(.callout).foregroundStyle(Palette.inkMuted) }

        Section(header: L("signin.server")) {
            TextField(S("signin.server.placeholder"), text: $server)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .textContentType(.URL)
        }

        Section(header: L("agent.setup.parentaccount")) {
            TextField(S("signin.email"), text: $email)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.emailAddress)
                .textContentType(.username)
            SecureField(S("signin.password"), text: $password)
                .textContentType(.password)
        }

        Section {
            Button {
                Task { _ = await model.signInForChildSetup(server: server, email: email, password: password) }
            } label: {
                HStack {
                    Spacer()
                    L(model.isBusy ? "signin.working" : "agent.setup.continue").fontWeight(.semibold)
                    if model.isBusy { ProgressView().padding(.leading, 6) }
                    Spacer()
                }
            }
            .primaryAction()
            .disabled(model.isBusy || email.isEmpty || password.isEmpty)
        }
        .listRowBackground(Color.clear)
    }

    @ViewBuilder private var childSection: some View {
        Section(header: L("agent.setup.whose"), footer: L("agent.setup.whose.hint")) {
            ForEach(model.setupChildren) { child in
                Button {
                    chosen = child.id
                } label: {
                    HStack {
                        Text(verbatim: child.displayName).foregroundStyle(Palette.ink)
                        Spacer()
                        if chosen == child.id {
                            Image(systemName: "checkmark").foregroundStyle(Palette.accent)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
        }

        Section(header: L("agent.pairing.label")) {
            TextField(S("agent.pairing.label.placeholder"), text: $label)
        }

        Section {
            Button {
                guard let chosen else { return }
                Task { _ = await model.finishChildSetup(childId: chosen, label: label) }
            } label: {
                HStack {
                    Spacer()
                    L(model.isBusy ? "signin.working" : "agent.setup.finish").fontWeight(.semibold)
                    if model.isBusy { ProgressView().padding(.leading, 6) }
                    Spacer()
                }
            }
            .primaryAction()
            .disabled(model.isBusy || chosen == nil)
        }
        .listRowBackground(Color.clear)
    }
}
