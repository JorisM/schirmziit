import SwiftUI

/// One screen, three fields. A child types this once, usually with a parent
/// reading the code off the dashboard.
struct AgentPairingView: View {
    let model: AgentModel

    @State private var server = AgentDefaults.server
    @State private var code = ""
    @State private var label = ""

    var body: some View {
        Form {
            Section {
                Text("pairing.intro")
                    .font(.callout)
                    .foregroundStyle(Palette.inkMuted)
            }

            Section(header: Text("pairing.server")) {
                TextField(String(localized: "pairing.server.placeholder"), text: $server)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
            }

            Section(header: Text("pairing.code"), footer: Text("pairing.code.hint")) {
                TextField(String(localized: "pairing.code.placeholder"), text: $code)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .font(.system(.body, design: .monospaced))
            }

            Section(header: Text("pairing.label")) {
                TextField(String(localized: "pairing.label.placeholder"), text: $label)
            }

            if let error = model.lastError {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(Palette.urgent)
                }
            }

            Section {
                Button {
                    Task { await model.pair(server: server, code: code, label: label) }
                } label: {
                    HStack {
                        Text(model.isBusy ? "pairing.working" : "pairing.submit")
                        if model.isBusy {
                            Spacer()
                            ProgressView()
                        }
                    }
                }
                .disabled(model.isBusy || server.isEmpty || code.count < 4)
            }
        }
        .scrollContentBackground(.hidden)
    }
}
