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

    private var perHour: [Int] { Formatting.hoursFromTotals(totals) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text("child.ribbon.title").font(.headline)
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
                        .accessibilityLabel(Text(verbatim: "\(hour):00"))
                        .accessibilityValue(Text(verbatim: Formatting.duration(perHour[hour])))
                        .onTapGesture { selected = selected == hour ? nil : hour }
                }
            }

            HStack {
                ForEach([0, 6, 12, 18], id: \.self) { hour in
                    Text(verbatim: String(format: "%02d", hour))
                        .font(.caption2.monospaced())
                        .foregroundStyle(Palette.inkFaint)
                    if hour != 18 { Spacer() }
                }
            }

            HStack(spacing: 6) {
                Text("child.ribbon.quiet").font(.caption).foregroundStyle(Palette.inkMuted)
                ForEach(0..<6, id: \.self) { step in
                    RoundedRectangle(cornerRadius: 3, style: .continuous)
                        .fill(Palette.ribbon[step])
                        .frame(width: 18, height: 10)
                }
                Text("child.ribbon.busy").font(.caption).foregroundStyle(Palette.inkMuted)
            }

            Text("child.ribbon.help")
                .font(.footnote)
                .foregroundStyle(Palette.inkMuted)
        }
    }
}
