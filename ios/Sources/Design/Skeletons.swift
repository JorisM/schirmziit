import SwiftUI

/// Shapes of the content that is coming. The counts and heights match the real
/// views, so nothing reflows when the data lands.
private struct Pulse: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dim = false

    func body(content: Content) -> some View {
        content
            .opacity(dim ? 0.45 : 1)
            .animation(
                reduceMotion ? nil : .easeInOut(duration: Motion.slow).repeatForever(autoreverses: true),
                value: dim
            )
            .onAppear { dim = true }
            .accessibilityHidden(true)
    }
}

struct StripSkeleton: View {
    var body: some View {
        HStack(alignment: .bottom, spacing: 3) {
            ForEach(0..<14, id: \.self) { _ in
                RoundedRectangle(cornerRadius: 3, style: .continuous)
                    .fill(Palette.hairline)
                    .frame(height: 36)
            }
        }
        .modifier(Pulse())
    }
}

struct RibbonSkeleton: View {
    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<24, id: \.self) { _ in
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(Palette.ribbon[0])
                    .frame(height: 56)
            }
        }
        .modifier(Pulse())
    }
}

struct RowsSkeleton: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Decreasing, because the real table is sorted biggest-first.
            ForEach([0.92, 0.68, 0.44, 0.26], id: \.self) { fraction in
                GeometryReader { geometry in
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .fill(Palette.hairline)
                        .frame(width: geometry.size.width * fraction, height: 12)
                }
                .frame(height: 12)
            }
        }
        .modifier(Pulse())
    }
}
