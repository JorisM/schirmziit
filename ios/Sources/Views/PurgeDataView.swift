import SwiftUI

/// Deletes a child's stored figures — the iPhone half of the dashboard's
/// `PurgeData` and of Android's `PurgeDataCard`.
///
/// The privacy page and all three help screens promise this. Until now it
/// existed on the phone as an API route only, which makes the promise true for
/// whoever can run curl and for nobody else.
///
/// Two presses, not one. The control sits at the foot of a screen a parent opens
/// daily, under numbers they came to read, and a single tap there is one mis-tap
/// away from an irreversible deletion — so the dialog names what will go and
/// says there is no archive, the same wording `ChildDetailView` asks a phone
/// disconnection with.
///
/// No flourish here on purpose. `ChildDetailView`'s one flourish is the ribbon
/// fill; this section gets entry motion on the receipt and a button that
/// presses. The *failure* path gets neither: an interface that animates a
/// failure is enjoying itself at the parent's expense.
struct PurgeDataView: View {
    let client: ApiClient
    let childId: String
    /// Re-reads what is on screen once the figures have gone. The fortnight and
    /// the day describe rows the server has just deleted, and leaving them up
    /// tells a parent the purge did not work.
    let onPurged: () async -> Void

    /// Non-private for the reason `PairDeviceView.enrollment` is: a snapshot
    /// test needs the receipt without a network round trip, and this view owns
    /// no `@Observable` model a stub transport could drive.
    @State var purged: PurgeResponse?
    @State private var error: AppError?
    @State private var asking = false
    @State private var busy = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Section(header: L("data.title")) {
            L("data.body")
                .font(.footnote)
                .foregroundStyle(Palette.inkMuted)

            if let error {
                // Inline, never a banner: a banner sits over data that is still
                // good, and there is no purge on screen for this to be stale
                // beside. No `onRetry` either — the button below is the retry,
                // and two controls meaning the same thing is a worse question.
                ErrorView(error: error)
            }

            if let purged { receipt(purged) }

            // `role: .destructive` for the semantics VoiceOver reads, and the
            // colour taken back explicitly: the role's system red — with the
            // system blue it leaves on the icon — is neither this app's palette
            // nor the right weight for a control that only *asks*. The loud one
            // is the confirm in the dialog. The dashboard's `DestructiveAction`
            // and `PurgeDataCard` make the same split.
            Button(role: .destructive) { asking = true } label: {
                Label {
                    L(busy ? "data.delete.working" : "data.delete")
                } icon: {
                    Image(systemName: "trash")
                }
                .foregroundStyle(Palette.inkMuted)
            }
            .disabled(busy)
        }
        .confirmationDialog(
            S("data.delete"),
            isPresented: $asking,
            titleVisibility: .visible
        ) {
            Button(S("data.delete.confirm"), role: .destructive) {
                Task { await purge() }
            }
            Button(S("app.cancel"), role: .cancel) { asking = false }
        } message: {
            L("data.delete.body")
        }
        // The receipt arriving is the section's own change, so it moves rather
        // than blinks. `Motion.animation` returns nil under reduced motion,
        // which lands on the finished block instantly.
        .motion(Motion.base, value: purged)
    }

    /// What actually went, in the server's own numbers — shown even when they
    /// are all zero, so a family whose phone has not reported yet can tell a
    /// purge that worked from one that found nothing.
    private func receipt(_ purged: PurgeResponse) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            L("data.deleted")
                .font(.subheadline.weight(.medium))
                // Announced when it arrives: the parent pressed a button and
                // the only proof it worked is this block.
                .accessibilityAddTraits(.updatesFrequently)
            HStack(alignment: .top, spacing: 16) {
                count("data.deleted.hours", purged.deletedUsageHours)
                count("data.deleted.devicehours", purged.deletedDeviceHours)
                count("data.deleted.days", purged.deletedUsageDays)
            }
        }
        .padding(.vertical, 4)
    }

    private func count(_ key: LocalizedStringKey, _ value: Int) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            L(key)
                .font(.caption)
                .foregroundStyle(Palette.inkFaint)
            Text(verbatim: "\(value)")
                .font(.title3.weight(.semibold))
                .monospacedDigit()
        }
    }

    @MainActor
    private func purge() async {
        busy = true
        defer { busy = false }
        switch await Self.purgeData(client: client, childId: childId) {
        case .success(let result):
            purged = result
            error = nil
            await onPurged()
        case .failure(let failure):
            // `purged` deliberately untouched by a failure — a receipt and a
            // failure must never be on screen together.
            error = failure
        }
    }

    /// The write, as a plain `static func` for the reason `ChildWritesTests`
    /// records: `@State` on a view SwiftUI has never installed silently loses
    /// writes, so the part worth testing stays out of the view lifecycle.
    ///
    /// The figures, not the child: `DELETE /v1/children/{id}` removes the child
    /// themselves, which is a different and much larger act.
    static func purgeData(client: ApiClient, childId: String) async -> PurgeOutcome {
        let endpoint = "v1/children/\(childId)/data"
        do {
            return .success(try await client.delete(endpoint, as: PurgeResponse.self))
        } catch let caught as AppError {
            return .failure(caught)
        } catch {
            return .failure(AppError.transport(error, endpoint: endpoint))
        }
    }
}

/// What went, or the reason nothing did. `WriteOutcome` carries no value, and
/// the counts are the whole point of this one: a purge that reports nothing is
/// a claim a family cannot check.
enum PurgeOutcome {
    case success(PurgeResponse)
    case failure(AppError)
}
