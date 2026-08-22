import SwiftUI

/// The plain-language explanation, in the child's language. Same content as the
/// parent app's help screen, written for the person being measured.
struct AgentHelpView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section { L("agent.help.intro").font(.callout) }

                Section(header: L("agent.help.measures.title")) {
                    bullet("agent.help.measures.1")
                    bullet("agent.help.measures.2")
                    bullet("agent.help.measures.3")
                }

                Section(header: L("agent.help.never.title")) {
                    bullet("agent.help.never.1")
                    bullet("agent.help.never.2")
                    bullet("agent.help.never.3")
                    bullet("agent.help.never.4")
                    bullet("agent.help.never.5")
                }

                Section(header: L("agent.help.how.title")) {
                    bullet("agent.help.how.1")
                    bullet("agent.help.how.2")
                    bullet("agent.help.how.3")
                }

                Section(header: L("agent.help.where.title")) { L("agent.help.where").font(.callout) }
                Section(header: L("agent.help.yousee.title")) { L("agent.help.yousee").font(.callout) }
                Section { L("agent.help.swiss").font(.footnote).foregroundStyle(Palette.inkMuted) }
            }
            .navigationTitle(L("agent.help.title"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(S("agent.help.done")) { dismiss() }
                }
            }
        }
    }

    private func bullet(_ key: LocalizedStringKey) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Circle().fill(Palette.accent).frame(width: 5, height: 5).offset(y: 6)
            L(key).font(.callout)
        }
    }
}
