import SwiftUI

/// Ranked app rows, with the sub-minute glances folded behind a disclosure.
///
/// A day has a handful of apps a parent actually wants to talk about, and a
/// long tail of launcher/clock/keyboard glances that would otherwise push
/// them off the screen. Extracted so it can be recorded as its own snapshot,
/// the way `DayRibbonView`/`DayStripView` are.
struct AppRowsView: View {
    let series: [UsageSeries]
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false

    private var split: (shown: [(label: String, ms: Int)], brief: [(label: String, ms: Int)]) {
        let ranked = series
            .sorted { $0.totalMs > $1.totalMs }
            .map { (label: $0.label, ms: $0.totalMs) }
        return Formatting.splitApps(ranked)
    }

    var body: some View {
        // `Formatting.visibleApps` — the same call `AgentMyTimeView` makes —
        // rather than `shown.prefix` inlined here with its own comment
        // claiming the same cap: a comment is not a constant.
        let visible = Formatting.visibleApps(split, cap: Formatting.appRowCap)
        Group {
            ForEach(Array(visible.shown.enumerated()), id: \.offset) { index, entry in
                row(entry, index: index)
            }
            if !visible.brief.isEmpty {
                DisclosureGroup {
                    ForEach(Array(visible.brief.enumerated()), id: \.offset) { index, entry in
                        row(entry, index: visible.shown.count + index)
                    }
                } label: {
                    Text(verbatim: "\(S("child.apps.brief")) (\(visible.brief.count))")
                }
            }
        }
        .onAppear { shown = true }
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
        .opacity(shown ? 1 : 0)
        .animation(
            reduceMotion ? nil : .easeOut(duration: Motion.base).delay(Motion.staggerDelay(index)),
            value: shown
        )
    }
}
