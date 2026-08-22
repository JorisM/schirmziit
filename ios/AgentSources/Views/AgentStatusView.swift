import SwiftUI

/// The child's own view of what is being reported. Deliberately the same numbers
/// the parent sees: nothing here is hidden from the person carrying the phone.
struct AgentStatusView: View {
    let model: AgentModel
    @State private var unlocking = false
    @State private var password = ""

    var body: some View {
        List {
            Section { stateCard.listRowInsets(.init(top: 16, leading: 16, bottom: 16, trailing: 16)) }

            if case .reporting(let pending, let lastSync) = model.status {
                Section(header: L("agent.status.queue")) {
                    // Through L(), not String(localized:): the string-based
                    // initialiser resolves in the process locale, which is right
                    // on a phone and wrong in a snapshot — so a German screen
                    // came out with two English rows in it.
                    LabeledContent {
                        Text(verbatim: "\(pending)")
                    } label: {
                        L("agent.status.pending")
                    }
                    LabeledContent {
                        if let lastSync {
                            Text(lastSync, format: .dateTime.hour().minute())
                        } else {
                            L("agent.status.never")
                        }
                    } label: {
                        L("agent.status.lastsync")
                    }
                }
            }

            if !model.sharedContainerAvailable {
                Section {
                    Label(title: { L("agent.status.shared.warning") }, icon: { Image(systemName: "exclamationmark.triangle") })
                        .foregroundStyle(Palette.warn)
                        .font(.footnote)
                }
            }

            Section {
                Button {
                    Task { await model.syncNow() }
                } label: {
                    L(model.isBusy ? "agent.status.syncing" : "agent.status.sync.now")
                }
                .disabled(model.isBusy)

                Button(role: .destructive) { unlocking = true } label: {
                    L("agent.status.unpair")
                }
            }

            if let error = model.lastError {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(Palette.urgent)
                        .font(.footnote)
                }
            }

            if case .reporting = model.status {
                // Invisible on purpose — see UsageProbeView.
                Section { UsageProbeView() }
                    .listRowBackground(Color.clear)
            }
        }
        .scrollContentBackground(.hidden)
        // A password, not a confirmation dialog: child mode a child can tap out of
        // is decoration. Checked against the server, so it is the parent's real
        // password and not something stored on this phone.
        .alert(L("agent.unlock.title"), isPresented: $unlocking) {
            SecureField(S("signin.password"), text: $password)
            Button(S("agent.unlock.cancel"), role: .cancel) { password = "" }
            Button(S("agent.unlock.submit")) {
                let entered = password
                password = ""
                Task { _ = await model.leaveChildMode(password: entered) }
            }
        } message: {
            L("agent.unlock.body")
        }
    }

    @ViewBuilder private var stateCard: some View {
        switch model.status {
        case .reporting:
            card(icon: "checkmark.seal.fill", tint: Palette.ok,
                 title: "agent.status.reporting.title", body: "agent.status.reporting.body")

        case .needsScreenTimePermission:
            VStack(alignment: .leading, spacing: 12) {
                card(icon: "hourglass", tint: Palette.warn,
                     title: "agent.status.permission.title", body: "agent.status.permission.body")
                Button {
                    Task { await model.requestScreenTime() }
                } label: {
                    L("agent.status.permission.button").frame(maxWidth: .infinity)
                }
                .primaryAction()
            }

        case .screenTimeDenied:
            card(icon: "hand.raised.fill", tint: Palette.urgent,
                 title: "agent.status.denied.title", body: "agent.status.denied.body")

        case .screenTimeUnavailable:
            card(icon: "wrench.and.screwdriver.fill", tint: Palette.inkMuted,
                 title: "agent.status.unavailable.title", body: "agent.status.unavailable.body")

        case .needsPairing:
            card(icon: "link", tint: Palette.accent,
                 title: "agent.pairing.title", body: "agent.pairing.intro")
        }
    }

    private func card(icon: String, tint: Color, title: LocalizedStringKey, body: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 6) {
                L(title).font(.headline).foregroundStyle(Palette.ink)
                L(body).font(.callout).foregroundStyle(Palette.inkMuted)
            }
        }
    }
}
