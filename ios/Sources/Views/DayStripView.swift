import SwiftUI

/// Fourteen days as bars. The ribbon answers "when in the day"; this answers
/// "was today unusual", which one day on its own cannot.
struct DayStripView: View {
    let days: [(day: String, ms: Int)]
    let selected: String
    let onSelect: (String) -> Void

    private var busiest: Int { days.map(\.ms).max() ?? 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            L("child.history.title").font(.headline)

            HStack(alignment: .bottom, spacing: 3) {
                ForEach(days, id: \.day) { entry in
                    let share = busiest > 0 ? Double(entry.ms) / Double(busiest) : 0
                    Button { onSelect(entry.day) } label: {
                        VStack(spacing: 4) {
                            RoundedRectangle(cornerRadius: 3, style: .continuous)
                                .fill(entry.ms > 0 ? Palette.accent : Palette.hairline)
                                // A floor, not a zero: an empty day is still a
                                // day, and a bar of no height reads as a hole.
                                .frame(height: 8 + CGFloat(share) * 52)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 3, style: .continuous)
                                        .strokeBorder(entry.day == selected ? Palette.inkMuted : .clear, lineWidth: 2)
                                )
                            Text(verbatim: String(entry.day.suffix(2)))
                                .font(.caption2.monospaced())
                                .foregroundStyle(Palette.inkFaint)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(verbatim: entry.day))
                    .accessibilityValue(Text(verbatim: Formatting.duration(entry.ms)))
                    .accessibilityAddTraits(entry.day == selected ? [.isSelected] : [])
                }
            }

            L("child.history.help").font(.caption).foregroundStyle(Palette.inkMuted)
        }
    }
}
