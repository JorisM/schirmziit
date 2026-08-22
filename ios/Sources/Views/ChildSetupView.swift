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
            .navigationTitle("agent.setup.title")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(String(localized: "agent.setup.cancel")) {
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
        Section { Text("agent.setup.intro").font(.callout).foregroundStyle(Palette.inkMuted) }

        Section("signin.server") {
            TextField("signin.server.placeholder", text: $server)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
        }

        Section("agent.setup.parentaccount") {
            TextField("signin.email", text: $email)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.emailAddress)
            SecureField("signin.password", text: $password)
        }

        Section {
            Button {
                Task { _ = await model.signInForChildSetup(server: server, email: email, password: password) }
            } label: {
                HStack {
                    Spacer()
                    Text(model.isBusy ? "signin.working" : "agent.setup.continue").fontWeight(.semibold)
                    if model.isBusy { ProgressView().padding(.leading, 6) }
                    Spacer()
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(model.isBusy || email.isEmpty || password.isEmpty)
        }
        .listRowBackground(Color.clear)
    }

    @ViewBuilder private var childSection: some View {
        Section(header: Text("agent.setup.whose"), footer: Text("agent.setup.whose.hint")) {
            ForEach(model.setupChildren) { child in
                Button {
                    chosen = child.id
                } label: {
                    HStack {
                        Text(child.displayName).foregroundStyle(Palette.ink)
                        Spacer()
                        if chosen == child.id {
                            Image(systemName: "checkmark").foregroundStyle(Palette.accent)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
        }

        Section("agent.pairing.label") {
            TextField("agent.pairing.label.placeholder", text: $label)
        }

        Section {
            Button {
                guard let chosen else { return }
                Task { _ = await model.finishChildSetup(childId: chosen, label: label) }
            } label: {
                HStack {
                    Spacer()
                    Text(model.isBusy ? "signin.working" : "agent.setup.finish").fontWeight(.semibold)
                    if model.isBusy { ProgressView().padding(.leading, 6) }
                    Spacer()
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(model.isBusy || chosen == nil)
        }
        .listRowBackground(Color.clear)
    }
}
