import SwiftUI
import UIKit

/// Every error either role sees, in one view.
///
/// `inline` replaces the data that failed to load. `banner` sits above data that
/// is already on screen when a *refresh* failed — the numbers stay, the banner
/// says they are stale. Blanking a loaded day because a poll failed is the same
/// mistake as losing a day, one layer up.
///
/// Entry motion and press feedback, and no flourish: the flourish belongs to the
/// data. Animating a failure is the interface enjoying itself at the parent's
/// expense.
struct ErrorView: View {
    enum Placement {
        case inline
        case banner
    }

    let error: AppError
    var placement: Placement = .inline
    var onRetry: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var expanded = false
    @State private var copied = false

    private var urgent: Bool { ErrorCopy.isUrgent(error.code) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label {
                ErrorCopy.text(ErrorCopy.titleKey(for: error.code))
                    .font(.subheadline.weight(.medium))
            } icon: {
                Image(systemName: urgent ? "exclamationmark.triangle.fill" : "info.circle")
            }
            .foregroundStyle(urgent ? Palette.urgent : Palette.ink)

            ErrorCopy.text(ErrorCopy.actionKey(for: error.code))
                .font(.footnote)
                .foregroundStyle(Palette.inkMuted)

            if let onRetry {
                Button(action: onRetry) { L("errorpanel.retry") }
                    .buttonStyle(.bordered)
                    // The app's teal, not the system blue: an error panel is
                    // still part of this product.
                    .tint(Palette.accent)
                    .font(.footnote)
            }

            reference

            if expanded { detail }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(placement == .banner ? 12 : 0)
        .background(placement == .banner ? Palette.card : Color.clear, in: .rect(cornerRadius: 12))
        .animation(Motion.animation(Motion.base, reduceMotion: reduceMotion), value: expanded)
        .accessibilityElement(children: .contain)
    }

    /// The line a parent photographs. Dimmed with a colour token, never
    /// `.opacity(...)`: it has to stay legible after a messenger has
    /// re-compressed the screenshot twice.
    private var reference: some View {
        Button {
            expanded.toggle()
        } label: {
            HStack(spacing: 4) {
                Text(verbatim: "\(error.code.wire) · \(error.ref)")
                Image(systemName: expanded ? "chevron.up" : "chevron.down")
            }
            .font(.caption.monospaced())
            .foregroundStyle(Palette.inkMuted)
        }
        .buttonStyle(.plain)
        // VoiceOver would otherwise read SZ-E504 as a word.
        .accessibilityLabel(S("errorpanel.reference"))
        .accessibilityValue(error.code.wire.map { String($0) }.joined(separator: " "))
    }

    private var detail: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(verbatim: error.copyDetails)
                .font(.caption2.monospaced())
                .foregroundStyle(Palette.inkFaint)
                .textSelection(.enabled)

            Button {
                UIPasteboard.general.string = error.copyDetails
                copied = true
            } label: {
                copied ? L("errorpanel.copied") : L("errorpanel.copy")
            }
            .font(.caption)
        }
    }
}
