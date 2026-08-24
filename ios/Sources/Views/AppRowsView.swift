import SwiftUI

/// Ranked app rows, with the sub-minute glances folded behind a disclosure.
///
/// A day has a handful of apps a parent actually wants to talk about, and a
/// long tail of launcher/clock/keyboard glances that would otherwise push
/// them off the screen. Extracted so it can be recorded as its own snapshot,
/// the way `DayRibbonView`/`DayStripView` are.
struct AppRowsView: View {
    let series: [UsageSeries]

    /// Ranked rows shown on their own, past which the rest is still reachable
    /// but not worth a full screen of rows.
    private static let keep = 8

    private var split: (shown: [(label: String, ms: Int)], brief: [(label: String, ms: Int)]) {
        let ranked = series
            .sorted { $0.totalMs > $1.totalMs }
            .map { (label: $0.label, ms: $0.totalMs) }
        return Formatting.splitApps(ranked)
    }

    var body: some View {
        let (shown, brief) = split
        ForEach(Array(shown.prefix(Self.keep).enumerated()), id: \.offset) { index, entry in
            row(entry, index: index)
        }
        if !brief.isEmpty {
            DisclosureGroup {
                ForEach(Array(brief.enumerated()), id: \.offset) { index, entry in
                    row(entry, index: shown.count + index)
                }
            } label: {
                Text(verbatim: "\(S("child.apps.brief")) (\(brief.count))")
            }
        }
    }

    private func row(_ entry: (label: String, ms: Int), index: Int) -> some View {
        HStack {
            Circle()
                .fill(Palette.series[index % Palette.series.count])
                .frame(width: 10, height: 10)
            Text(verbatim: entry.label)
            Spacer()
            Text(verbatim: Formatting.duration(entry.ms))
                .monospacedDigit()
                .foregroundStyle(Palette.inkMuted)
        }
    }
}
