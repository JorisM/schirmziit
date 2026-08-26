import SwiftUI

/// Fourteen days as bars. The ribbon answers "when in the day"; this answers
/// "was today unusual", which one day on its own cannot.
struct DayStripView: View {
    let days: [(day: String, ms: Int)]
    let selected: String
    let onSelect: (String) -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var grown = false

    private var busiest: Int { days.map(\.ms).max() ?? 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            L("child.history.title").font(.headline)

            HStack(alignment: .bottom, spacing: 3) {
                ForEach(Array(days.enumerated()), id: \.element.day) { index, entry in
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
                                .scaleEffect(y: grown ? 1 : 0.2, anchor: .bottom)
                                .animation(
                                    Motion.animation(
                                        Motion.base,
                                        delay: Motion.staggerDelay(index),
                                        reduceMotion: reduceMotion
                                    ),
                                    value: grown
                                )
                            Text(verbatim: String(entry.day.suffix(2)))
                                .font(.caption2.monospaced())
                                .foregroundStyle(Palette.inkFaint)
                        }
                    }
                    .buttonStyle(PressableBarStyle())
                    .accessibilityLabel(Self.spokenDay(entry.day))
                    .accessibilityValue(Text(verbatim: Formatting.duration(entry.ms)))
                    .accessibilityAddTraits(entry.day == selected ? [.isSelected] : [])
                }
            }
            .onAppear { grown = true }

            L("child.history.help").font(.caption).foregroundStyle(Palette.inkMuted)
        }
    }

    /// VoiceOver spelled out the raw "2026-08-24" digit by digit — this is the
    /// headline new control on this branch, and that is a bad first
    /// impression of it. Falls back to the raw string only if the day somehow
    /// fails to parse, which is better than announcing nothing at all.
    private static func spokenDay(_ day: String) -> Text {
        guard let date = ISO8601DateFormatter.dayOnly.date(from: day) else {
            return Text(verbatim: day)
        }
        return Text(date, format: .dateTime.weekday(.wide).month(.wide).day())
    }
}
