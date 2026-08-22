import SwiftUI

/// The child's own view of what is being reported. Deliberately the same numbers
/// the parent sees: nothing here is hidden from the person carrying the phone.
struct AgentStatusView: View {
    let model: AgentModel
    @State private var confirmingUnpair = false

    var body: some View {
        List {
            Section { stateCard.listRowInsets(.init(top: 16, leading: 16, bottom: 16, trailing: 16)) }

            if case .reporting(let pending, let lastSync) = model.status {
                Section(header: Text("status.queue")) {
                    LabeledContent(String(localized: "status.pending"),
                                   value: "\(pending)")
                    LabeledContent(String(localized: "status.lastsync"),
                                   value: lastSync.map { $0.formatted(date: .omitted, time: .shortened) }
                                       ?? String(localized: "status.never"))
                }
            }

            if !model.sharedContainerAvailable {
                Section {
                    Label("status.shared.warning", systemImage: "exclamationmark.triangle")
                        .foregroundStyle(Palette.warn)
                        .font(.footnote)
                }
            }

            Section {
                Button {
                    Task { await model.syncNow() }
                } label: {
                    Text(model.isBusy ? "status.syncing" : "status.sync.now")
                }
                .disabled(model.isBusy)

                Button(role: .destructive) { confirmingUnpair = true } label: {
                    Text("status.unpair")
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
        .confirmationDialog(Text("status.unpair.confirm"), isPresented: $confirmingUnpair) {
            Button(String(localized: "status.unpair"), role: .destructive) { model.unpair() }
        }
    }

    @ViewBuilder private var stateCard: some View {
        switch model.status {
        case .reporting:
            card(icon: "checkmark.seal.fill", tint: Palette.ok,
                 title: "status.reporting.title", body: "status.reporting.body")

        case .needsScreenTimePermission:
            VStack(alignment: .leading, spacing: 12) {
                card(icon: "hourglass", tint: Palette.warn,
                     title: "status.permission.title", body: "status.permission.body")
                Button {
                    Task { await model.requestScreenTime() }
                } label: {
                    Text("status.permission.button").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }

        case .screenTimeDenied:
            card(icon: "hand.raised.fill", tint: Palette.urgent,
                 title: "status.denied.title", body: "status.denied.body")

        case .screenTimeUnavailable:
            card(icon: "wrench.and.screwdriver.fill", tint: Palette.inkMuted,
                 title: "status.unavailable.title", body: "status.unavailable.body")

        case .needsPairing:
            card(icon: "link", tint: Palette.accent,
                 title: "pairing.title", body: "pairing.intro")
        }
    }

    private func card(icon: String, tint: Color, title: LocalizedStringKey, body: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 6) {
                Text(title).font(.headline).foregroundStyle(Palette.ink)
                Text(body).font(.callout).foregroundStyle(Palette.inkMuted)
            }
        }
    }
}
