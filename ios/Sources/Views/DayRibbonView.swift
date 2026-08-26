import SwiftUI

/// The day as 24 cells, midnight to midnight.
///
/// A bar chart answers "how much"; a parent's real question is "when" — an hour
/// at 23:00 means something different from an hour after lunch. Tapping a cell
/// names it, so the detail is available without a hover state that a touch
/// screen does not have.
struct DayRibbonView: View {
    let totals: [DeviceTotal]
    @State private var selected: Int?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var filled = false

    private var perHour: [Int] { Formatting.hoursFromTotals(totals) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                L("child.ribbon.title").font(.headline)
                Spacer()
                if let selected {
                    Text(verbatim: "\(String(format: "%02d", selected)):00 · \(Formatting.duration(perHour[selected]))")
                        .font(.subheadline.monospacedDigit())
                        .foregroundStyle(Palette.inkMuted)
                }
            }

            let busiest = perHour.max() ?? 0
            HStack(spacing: 2) {
                ForEach(0..<24, id: \.self) { hour in
                    let step = Formatting.rampStep(ms: perHour[hour], busiest: busiest)
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .fill(Palette.ribbon[step])
                        .overlay(
                            RoundedRectangle(cornerRadius: 4, style: .continuous)
                                .strokeBorder(selected == hour ? Palette.accent : Palette.hairline,
                                              lineWidth: selected == hour ? 2 : 0.5)
                        )
                        .frame(height: 52)
                        .scaleEffect(y: filled ? 1 : 0.2, anchor: .bottom)
                        .opacity(filled ? 1 : 0)
                        .animation(
                            // The sweep IS the day passing. `Motion.slow / 24` spreads the
                            // whole flourish across the budget rather than per cell.
                            reduceMotion
                                ? nil
                                : .easeOut(duration: Motion.base).delay(Double(hour) * Motion.slow / 24),
                            value: filled
                        )
                        .accessibilityLabel(Text(verbatim: "\(hour):00"))
                        .accessibilityValue(Text(verbatim: Formatting.duration(perHour[hour])))
                        .onTapGesture { selected = selected == hour ? nil : hour }
                }
            }
            .onAppear { filled = true }

            HStack {
                ForEach([0, 6, 12, 18], id: \.self) { hour in
                    Text(verbatim: String(format: "%02d", hour))
                        .font(.caption2.monospaced())
                        .foregroundStyle(Palette.inkFaint)
                    if hour != 18 { Spacer() }
                }
            }

            HStack(spacing: 6) {
                L("child.ribbon.quiet").font(.caption).foregroundStyle(Palette.inkMuted)
                ForEach(0..<6, id: \.self) { step in
                    RoundedRectangle(cornerRadius: 3, style: .continuous)
                        .fill(Palette.ribbon[step])
                        .frame(width: 18, height: 10)
                }
                L("child.ribbon.busy").font(.caption).foregroundStyle(Palette.inkMuted)
            }

            L("child.ribbon.help")
                .font(.footnote)
                .foregroundStyle(Palette.inkMuted)
        }
    }
}
