import SchirmziitAgentKit
import SwiftUI

@main
struct SchirmziitAgentApp: App {
    @State private var model = AgentModel()

    init() {
        // Has to happen before the first scene appears, or iOS refuses the
        // registration for the whole process lifetime.
        BackgroundSync.register(model: model)
    }

    var body: some Scene {
        WindowGroup {
            AgentRootView(model: model)
        }
    }
}
