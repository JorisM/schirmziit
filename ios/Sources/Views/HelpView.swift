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
                    Text("help.intro").font(.subheadline)
                }

                Section("help.measures.title") {
                    ForEach(1...3, id: \.self) { index in
                        Label {
                            Text(LocalizedStringKey("help.measures.\(index)"))
                        } icon: {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(Palette.ok)
                        }
                    }
                }

                Section("help.never.title") {
                    ForEach(1...5, id: \.self) { index in
                        Label {
                            Text(LocalizedStringKey("help.never.\(index)"))
                        } icon: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(Palette.urgent)
                        }
                    }
                }

                Section("help.how.title") {
                    ForEach(1...4, id: \.self) { index in
                        Text(LocalizedStringKey("help.how.\(index)"))
                    }
                }

                Section("help.where.title") { Text("help.where") }
                Section("help.retention.title") { Text("help.retention") }
                Section("help.notacontrol.title") { Text("help.notacontrol") }

                Section {
                    Text("help.swiss")
                        .font(.footnote)
                        .foregroundStyle(Palette.inkMuted)
                }
            }
            .nestlingList()
            .navigationTitle("help.title")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("help.close") { dismiss() }
                }
            }
        }
    }
}
