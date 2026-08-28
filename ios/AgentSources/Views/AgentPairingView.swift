import SwiftUI

/// One screen, three fields. A child types this once, usually with a parent
/// reading the code off the dashboard.
struct AgentPairingView: View {
    let model: AgentModel
    /// A `schirmziit://enroll` link the phone was opened with — the system
    /// camera reading the square the parent has on screen. It fills the two
    /// fields that are otherwise typed off another screen, and fills nothing
    /// else: the phone still gets a name and the parent still presses pair.
    var scanned: EnrollLink?

    @State private var server = AgentDefaults.server
    @State private var code = ""
    @State private var label = ""
    @State private var scanning = false

    var body: some View {
        Form {
            Section {
                L("agent.pairing.intro")
                    .font(.callout)
                    .foregroundStyle(Palette.inkMuted)
            }

            // Above the fields, because it is the way past them: the address and
            // the code are the two things typed off another screen, and both are
            // in the square the parent already has open.
            Section {
                Button {
                    scanning = true
                } label: {
                    Label(title: { L("agent.pairing.scan") }, icon: { Image(systemName: "qrcode.viewfinder") })
                }
            }

            Section(header: L("agent.pairing.server")) {
                TextField(S("agent.pairing.server.placeholder"), text: $server)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
            }

            Section(header: L("agent.pairing.code"), footer: L("agent.pairing.code.hint")) {
                TextField(S("agent.pairing.code.placeholder"), text: $code)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .font(.system(.body, design: .monospaced))
            }

            Section(header: L("agent.pairing.label")) {
                TextField(S("agent.pairing.label.placeholder"), text: $label)
            }

            if let error = model.lastError {
                Section { ErrorView(error: error) }
            }

            Section {
                Button {
                    Task { await model.pair(server: server, code: code, label: label) }
                } label: {
                    HStack {
                        L(model.isBusy ? "agent.pairing.working" : "agent.pairing.submit")
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
        // Both, not one: the link can arrive before this screen exists (a cold
        // launch from the camera) or while it is already up (the app was
        // running behind the camera).
        .onAppear { fill(from: scanned) }
        .onChange(of: scanned) { _, link in fill(from: link) }
        .sheet(isPresented: $scanning) {
            QrScannerSheet(
                onFound: { link in
                    fill(from: link)
                    scanning = false
                },
                onCancel: { scanning = false }
            )
        }
    }

    /// Filled in, never submitted. Pairing consumes a one-shot code, and a
    /// screen that pairs on arrival would burn the code on a mis-scan — and
    /// this phone still has no name until someone gives it one. The camera and
    /// the system's own scan arrive here alike, for the same reason.
    private func fill(from link: EnrollLink?) {
        guard let link else { return }
        server = link.server
        code = link.code
    }
}
