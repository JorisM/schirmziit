import SwiftUI

/// The plain-language explanation, in the child's language. Same content as the
/// parent app's help screen, written for the person being measured.
struct AgentHelpView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section { Text("help.intro").font(.callout) }

                Section(header: Text("help.measures.title")) {
                    bullet("help.measures.1")
                    bullet("help.measures.2")
                    bullet("help.measures.3")
                }

                Section(header: Text("help.never.title")) {
                    bullet("help.never.1")
                    bullet("help.never.2")
                    bullet("help.never.3")
                    bullet("help.never.4")
                    bullet("help.never.5")
                }

                Section(header: Text("help.how.title")) {
                    bullet("help.how.1")
                    bullet("help.how.2")
                    bullet("help.how.3")
                }

                Section(header: Text("help.where.title")) { Text("help.where").font(.callout) }
                Section(header: Text("help.yousee.title")) { Text("help.yousee").font(.callout) }
                Section { Text("help.swiss").font(.footnote).foregroundStyle(Palette.inkMuted) }
            }
            .navigationTitle(Text("help.title"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(String(localized: "help.done")) { dismiss() }
                }
            }
        }
    }

    private func bullet(_ key: LocalizedStringKey) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Circle().fill(Palette.accent).frame(width: 5, height: 5).offset(y: 6)
            Text(key).font(.callout)
        }
    }
}
