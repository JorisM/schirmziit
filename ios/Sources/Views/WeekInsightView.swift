import SwiftUI

/// Last full week against the one before it — the only thing on this screen
/// that answers "was this week unusual", which a single day never can.
///
/// Every number is the server's. This view compares nothing, so the iPhone, the
/// dashboard and an Android parent phone cannot end up saying different things
/// about the same week. Nothing here judges the child: it reports what moved,
/// in both directions, against no target and with no streak to keep.
struct WeekInsightView: View {
    let week: WeekComparison
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var risen = false

    private var eveningLabel: String {
        String(format: "%02d:00", week.eveningFromHour)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                L("week.title").font(.headline)
                Spacer()
                Text(verbatim: Self.range(week.from, week.to))
                    .font(.caption.monospaced())
                    .foregroundStyle(Palette.inkFaint)
            }

            HStack(alignment: .top, spacing: 24) {
                figure(
                    label: L("week.total"),
                    value: Formatting.duration(week.totalMs),
                    delta: week.previousMeasured ? week.deltaMs : nil,
                    index: 0
                )
                // The hour is interpolated into the key rather than joined to a
                // translated fragment: "Evenings from" and "21:00" do not sit in
                // that order in every language this app speaks.
                figure(
                    label: L("week.evening.from \(eveningLabel)"),
                    value: Formatting.duration(week.eveningMs),
                    delta: week.previousMeasured ? week.eveningDeltaMs : nil,
                    index: 1
                )
            }

            if week.previousMeasured {
                L("week.movers").font(.subheadline).foregroundStyle(Palette.inkFaint)
                if week.movers.isEmpty {
                    L("week.movers.none").font(.footnote).foregroundStyle(Palette.inkMuted)
                } else {
                    ForEach(Array(week.movers.enumerated()), id: \.element.id) { index, mover in
                        HStack(alignment: .firstTextBaseline) {
                            Text(verbatim: mover.label)
                            Spacer()
                            delta(mover.deltaMs)
                        }
                        .opacity(risen ? 1 : 0)
                        .animation(
                            Motion.animation(
                                Motion.base,
                                delay: Motion.staggerDelay(index + 2),
                                reduceMotion: reduceMotion
                            ),
                            value: risen
                        )
                    }
                }
            } else {
                // Not a rise of a hundred per cent, and not a blank card: no
                // phone reported the week before, and a comparison against
                // silence is the lost day this app promises never to show.
                L("week.first").font(.footnote).foregroundStyle(Palette.inkMuted)
            }
        }
        .padding(.vertical, 4)
        .onAppear { risen = true }
    }

    @ViewBuilder
    private func figure(label: Text, value: String, delta deltaMs: Int?, index: Int) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            label.font(.subheadline).foregroundStyle(Palette.inkFaint)
            Text(verbatim: value)
                .font(.system(size: 24, weight: .semibold, design: .rounded))
                .monospacedDigit()
            if let deltaMs { delta(deltaMs) }
        }
        .opacity(risen ? 1 : 0)
        .animation(
            Motion.animation(Motion.base, delay: Motion.staggerDelay(index), reduceMotion: reduceMotion),
            value: risen
        )
    }

    /// Direction in words as well as an arrow: an arrow alone is nothing to a
    /// screen reader, and a colour alone is nothing to the readers who cannot
    /// separate these two.
    @ViewBuilder
    private func delta(_ ms: Int) -> some View {
        if ms == 0 {
            L("week.same").font(.footnote).foregroundStyle(Palette.inkFaint)
        } else {
            let amount = Formatting.duration(abs(ms))
            HStack(spacing: 4) {
                Image(systemName: ms > 0 ? "arrow.up" : "arrow.down")
                    .font(.caption2)
                    .accessibilityHidden(true)
                if ms > 0 { L("week.more \(amount)") } else { L("week.less \(amount)") }
            }
            .font(.footnote)
            .foregroundStyle(Palette.inkMuted)
            .accessibilityElement(children: .combine)
        }
    }

    /// "13 – 19 Aug" in the reader's own locale. The dates arrive as
    /// `YYYY-MM-DD`, which VoiceOver would otherwise read digit by digit.
    static func range(_ from: String, _ to: String) -> String {
        guard let start = ISO8601DateFormatter.dayOnly.date(from: from),
              let end = ISO8601DateFormatter.dayOnly.date(from: to) else {
            return "\(from) – \(to)"
        }
        let format = Date.FormatStyle.dateTime.day().month(.abbreviated)
        return "\(start.formatted(format)) – \(end.formatted(format))"
    }
}
