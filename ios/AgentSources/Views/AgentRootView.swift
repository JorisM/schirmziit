import SwiftUI

public struct AgentRootView: View {
    let model: AgentModel

    public init(model: AgentModel) {
        self.model = model
    }
    @Environment(\.scenePhase) private var scenePhase
    @State private var showingHelp = false

    public var body: some View {
        NavigationStack {
            ZStack {
                Palette.paper.ignoresSafeArea()
                content
            }
            .navigationTitle(Text("agent.title"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showingHelp = true } label: {
                        Label("agent.help", systemImage: "questionmark.circle")
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
            AgentPairingView(model: model)
        default:
            AgentStatusView(model: model)
        }
    }
}
