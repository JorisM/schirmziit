import SwiftUI

/// Ranked app rows, with the sub-minute glances folded behind a disclosure.
///
/// A day has a handful of apps a parent actually wants to talk about, and a
/// long tail of launcher/clock/keyboard glances that would otherwise push
/// them off the screen. Extracted so it can be recorded as its own snapshot,
/// the way `DayRibbonView`/`DayStripView` are.
struct AppRowsView: View {
    let series: [UsageSeries]
    /// False is "no phone reporting this day could observe it", so the rows say
    /// nothing at all rather than a zero. See `UsageResponse.backgroundMeasured`.
    let backgroundMeasured: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false

    private var split: (shown: [Formatting.AppEntry], brief: [Formatting.AppEntry]) {
        // Ranked and folded by foreground alone: background listening is a
        // second measure of the same app, never a reason to promote its row.
        let ranked = series
            .sorted { $0.totalMs > $1.totalMs }
            .map { Formatting.AppEntry(label: $0.label, ms: $0.totalMs, backgroundMs: $0.backgroundMs) }
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

    private func row(_ entry: Formatting.AppEntry, index: Int) -> some View {
        HStack {
            Circle()
                .fill(Palette.series[index % Palette.series.count])
                .frame(width: 10, height: 10)
            VStack(alignment: .leading, spacing: 1) {
                Text(verbatim: entry.label)
                // Its own line in its own colour, under the app it belongs to:
                // beside the foreground figure it would read as one total.
                if backgroundMeasured && entry.backgroundMs > 0 {
                    L("child.background.app \(Formatting.duration(entry.backgroundMs))")
                        .font(.caption)
                        .monospacedDigit()
                        .foregroundStyle(Palette.backgroundWave)
                }
            }
            Spacer()
            Text(verbatim: Formatting.duration(entry.ms))
                .monospacedDigit()
                .foregroundStyle(Palette.inkMuted)
        }
        .opacity(shown ? 1 : 0)
        .animation(
            Motion.animation(Motion.base, delay: Motion.staggerDelay(index), reduceMotion: reduceMotion),
            value: shown
        )
    }
}
