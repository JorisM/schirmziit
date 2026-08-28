import SwiftUI

/// Mints the one-shot code a child's phone is enrolled with — the iPhone half of
/// the dashboard's `PairDevice`.
///
/// Minted on press, never on appearance: a code lives fifteen minutes and can be
/// claimed once, so a screen that mints when a parent opens it hands out — and
/// burns — a code nobody asked for.
///
/// The server address is shown next to the code because that is the half of the
/// pairing whose failure is silent: a phone enrolled against the wrong host
/// enrols exactly once and then never reports again.
///
/// No flourish here on purpose. `ChildDetailView`'s one flourish is the ribbon
/// fill; this card rises in and its button presses, and that is all it gets —
/// two flourishes on one screen compete and both lose.
struct PairDeviceView: View {
    let client: ApiClient
    let childId: String

    /// Non-private for the same reason `ChildrenView.children` is: a snapshot
    /// test needs the minted card without a network round trip, and this view
    /// owns no `@Observable` model a stub transport could drive.
    @State var enrollment: EnrollmentResponse?
    @State private var error: AppError?
    @State private var busy = false
    /// Recomputed when the code changes and again when its window closes, rather
    /// than derived at render time: nothing else redraws this screen in between,
    /// so a card left alone for fifteen minutes would go on claiming a code the
    /// server has already stopped accepting.
    ///
    /// Non-private for the same reason `enrollment` is — a golden of the expired
    /// card must not depend on the snapshot host getting round to running
    /// `.task`. Whatever is passed in is what `watchExpiry` computes for the
    /// same fixture, so the image is the same either way.
    @State var expired = false

    var body: some View {
        Section(header: L("devices.pair.title")) {
            if let error {
                // A failed mint keeps the code that is already on screen and
                // says the *new* one did not arrive: the old one may well still
                // be valid, and blanking it would take away the only thing the
                // parent can act on.
                ErrorView(error: error, placement: enrollment == nil ? .inline : .banner) {
                    Task { await mint() }
                }
            }

            if let enrollment {
                VStack(alignment: .leading, spacing: 12) {
                    // Numbered, not three stacked sentences: unnumbered they run
                    // together as one paragraph, and this is an errand a parent
                    // does while walking to another room with a phone in hand.
                    VStack(alignment: .leading, spacing: 6) {
                        step(1, "devices.pair.step1")
                        step(2, "devices.pair.step2")
                        step(3, "devices.pair.step3")
                    }
                    .font(.footnote)
                    .foregroundStyle(Palette.inkMuted)

                    // Only when the server drew one, and only when what it drew
                    // is genuinely a square: the code and the address below are
                    // the whole pairing on their own, and an empty frame here
                    // reads as a broken screen.
                    if let qr = enrollment.qr, qr.isDrawable {
                        QrMatrixView(matrix: qr, label: "devices.pair.qr")
                            .frame(maxWidth: .infinity, alignment: .center)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        L("devices.pair.code")
                            .font(.caption)
                            .foregroundStyle(Palette.inkFaint)
                        // Tracked wide and monospaced: these six characters get
                        // read out loud and typed on a phone one at a time.
                        Text(verbatim: enrollment.code)
                            .font(.system(size: 34, weight: .bold, design: .monospaced))
                            .tracking(4)
                            .textSelection(.enabled)
                            // An expired code is still worth showing — it is
                            // what the parent has half-typed on the other phone
                            // — but it must stop being the loudest thing here,
                            // or the red line under it is arguing with it.
                            .foregroundStyle(expired ? Palette.inkFaint : Palette.ink)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        L("devices.pair.server")
                            .font(.caption)
                            .foregroundStyle(Palette.inkFaint)
                        Text(verbatim: Self.serverAddress(from: enrollment.qrPayload))
                            .textSelection(.enabled)
                        L("devices.pair.server.hint")
                            .font(.footnote)
                            .foregroundStyle(Palette.inkMuted)
                    }

                    if expired {
                        // Not a styling variant of the same line: a code shown as
                        // usable after it expired sends a parent to a phone that
                        // will refuse it.
                        L("devices.pair.expired")
                            .font(.footnote.weight(.medium))
                            .foregroundStyle(Palette.urgent)
                    } else {
                        HStack(spacing: 4) {
                            L("devices.pair.expires")
                            Text(enrollment.expiresAt, format: .dateTime.hour().minute())
                        }
                        .font(.footnote)
                        .foregroundStyle(Palette.inkMuted)
                    }
                }
                .padding(.vertical, 4)
            }

            Button(action: { Task { await mint() } }) {
                Label {
                    L(busy ? "devices.pair.working" : (enrollment == nil ? "devices.pair.create" : "devices.pair.new"))
                } icon: {
                    Image(systemName: "iphone.badge.plus")
                }
            }
            // Prominent, and tinted the same way `ChildrenView`'s empty-state
            // Add is: `schirmziitList()` sets `foregroundStyle(Palette.ink)` for
            // the whole list, so a plain button here reads as another line of
            // text — and near-black on the teal fill is about 2.7:1, under AA.
            .buttonStyle(.borderedProminent)
            .tint(Palette.accent)
            .foregroundStyle(Palette.card)
            .disabled(busy)
        }
        // A card arriving is the section's own change, so it moves rather than
        // blinks. `Motion.animation` returns nil under reduced motion, which
        // lands on the finished card instantly.
        .motion(Motion.base, value: enrollment?.code)
        .task(id: enrollment?.code) { await watchExpiry() }
    }

    private func step(_ number: Int, _ key: LocalizedStringKey) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(verbatim: "\(number).")
                .monospacedDigit()
                .foregroundStyle(Palette.inkFaint)
            // Without this the `HStack` hands the sentence its ideal width and
            // the longest step truncates to one line — and the step that gets
            // cut is the one naming the server address, which is the half of
            // the pairing whose failure is silent.
            L(key).fixedSize(horizontal: false, vertical: true)
        }
    }

    @MainActor
    private func mint() async {
        busy = true
        defer { busy = false }
        switch await Self.mintCode(client: client, childId: childId) {
        case .success(let minted):
            enrollment = minted
            error = nil
        case .failure(let failure):
            // `enrollment` deliberately untouched — see the banner above.
            error = failure
        }
    }

    /// Sleeps to the expiry rather than polling for it: nothing else on this
    /// card changes in the meantime, and the one moment its copy has to change
    /// is the moment the server stops accepting the code.
    @MainActor
    private func watchExpiry() async {
        guard let expiresAt = enrollment?.expiresAt else {
            expired = false
            return
        }
        expired = Self.isExpired(expiresAt)
        guard !expired else { return }
        try? await Task.sleep(for: .seconds(expiresAt.timeIntervalSinceNow))
        // A cancelled sleep is a card that went away or a code that was
        // replaced, not a code that ran out.
        expired = !Task.isCancelled
    }

    /// The write, as a plain `static func` for the reason `ChildWritesTests`
    /// records: `@State` on a view SwiftUI has never installed silently loses
    /// writes, so the part worth testing stays out of the view lifecycle.
    static func mintCode(client: ApiClient, childId: String) async -> EnrollmentOutcome {
        let endpoint = "v1/children/\(childId)/enrollments"
        do {
            return .success(try await client.post(endpoint, as: EnrollmentResponse.self))
        } catch let caught as AppError {
            return .failure(caught)
        } catch {
            return .failure(AppError.transport(error, endpoint: endpoint))
        }
    }

    /// The address out of the deep link, which is meant for a camera rather than
    /// for a person. An unparseable payload falls back to the raw string: the
    /// parent still needs something to compare against what they typed, and a
    /// blank line beside the code is worse than a long one.
    static func serverAddress(from payload: String) -> String {
        URLComponents(string: payload)?.queryItems?.first { $0.name == "url" }?.value ?? payload
    }

    /// The server's window is exclusive (`expires_at > now()`), so the instant it
    /// names is already refused.
    static func isExpired(_ expiresAt: Date, now: Date = Date()) -> Bool { expiresAt <= now }
}

/// A minted code, or the reason there is none. `WriteOutcome` carries no value,
/// and this is the one parent write whose result is the thing on screen.
enum EnrollmentOutcome {
    case success(EnrollmentResponse)
    case failure(AppError)
}
