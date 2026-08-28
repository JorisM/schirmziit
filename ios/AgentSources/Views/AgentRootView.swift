import SwiftUI

public struct AgentRootView: View {
    let model: AgentModel
    /// A link this phone was opened with. Only the pairing screen has anything
    /// to do with it, and only when this phone is asking for a code.
    let scanned: EnrollLink?

    public init(model: AgentModel, scanned: EnrollLink? = nil) {
        self.model = model
        self.scanned = scanned
    }
    @Environment(\.scenePhase) private var scenePhase
    @State private var showingHelp = false

    public var body: some View {
        NavigationStack {
            ZStack {
                Palette.paper.ignoresSafeArea()
                content
            }
            .navigationTitle(L("agent.title"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showingHelp = true } label: {
                        Label(title: { L("agent.help") }, icon: { Image(systemName: "questionmark.circle") })
                    }
                }
            }
            .sheet(isPresented: $showingHelp) { AgentHelpView() }
        }
        .tint(Palette.accent)
        .onChange(of: scenePhase) { _, phase in
            // The child may have granted or revoked Screen Time access in
            // Settings while we were in the background.
            if phase == .active { model.refresh() }
        }
    }

    @ViewBuilder private var content: some View {
        switch model.status {
        case .needsPairing:
            AgentPairingView(model: model, scanned: scanned)
        default:
            AgentStatusView(model: model)
        }
    }
}
