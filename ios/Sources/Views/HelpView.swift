import SwiftUI

/// The same two lists as the dashboard and the Android app, in the same words.
/// Measured and not-collected sit next to each other because a promise is only
/// credible beside its limits.
struct HelpView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    L("help.intro").font(.subheadline)
                }

                Section(header: L("help.measures.title")) {
                    ForEach(1...3, id: \.self) { index in
                        Label {
                            L(LocalizedStringKey("help.measures.\(index)"))
                        } icon: {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(Palette.ok)
                        }
                    }
                }

                Section(header: L("help.never.title")) {
                    ForEach(1...5, id: \.self) { index in
                        Label {
                            L(LocalizedStringKey("help.never.\(index)"))
                        } icon: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(Palette.urgent)
                        }
                    }
                }

                Section(header: L("help.how.title")) {
                    ForEach(1...4, id: \.self) { index in
                        L(LocalizedStringKey("help.how.\(index)"))
                    }
                }

                Section(header: L("help.where.title")) { L("help.where") }
                Section(header: L("help.retention.title")) { L("help.retention") }
                Section(header: L("help.notacontrol.title")) { L("help.notacontrol") }

                Section {
                    L("help.swiss")
                        .font(.footnote)
                        .foregroundStyle(Palette.inkMuted)
                }
            }
            .schirmziitList()
            .navigationTitle(L("help.title"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("help.close") { dismiss() }
                }
            }
        }
    }
}
