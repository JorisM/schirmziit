import SwiftUI

/// The child's own fourteen days and the detail of one — the same shapes
/// `ChildDetailView` draws for a parent, fetched here over this phone's own
/// device token instead of a parent's session. The product's whole promise is
/// that this screen and the parent's dashboard never disagree.
struct AgentMyTimeView: View {
    let model: AgentModel

    private var today: String { ISO8601DateFormatter.dayOnly.string(from: Date()) }

    /// `DayDetailFfi.hours` is already the per-hour ribbon the core computed.
    /// Wrapping it as synthetic `DeviceTotal`s lets the existing ribbon view —
    /// which only knows how to bucket raw device totals — draw it, rather than
    /// a second implementation of the same chart existing here.
    private var hourlyTotals: [DeviceTotal] {
        let hours = model.myDay?.hours ?? Array(repeating: 0, count: 24)
        return hours.enumerated().map { hour, ms in
            DeviceTotal(
                start: String(format: "2000-01-01T%02d:00:00+00:00", hour),
                screenOnMs: Int(truncatingIfNeeded: ms),
                unlockCount: 0
            )
        }
    }

    var body: some View {
        List {
            if let error = model.myTimeError {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(Palette.urgent)
                        .font(.footnote)
                    Button(S("agent.mytime.retry")) { Task { await load() } }
                }
            }

            Section {
                DayStripView(
                    days: model.myDays.map { (day: $0.day, ms: Int(truncatingIfNeeded: $0.foregroundMs)) },
                    selected: model.mySelectedDay,
                    onSelect: { day in Task { await model.selectMyDay(day) } }
                )
                .padding(.vertical, 4)
            }

            if let day = model.myDay {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        L(model.mySelectedDay == today ? "child.total" : "child.selected")
                            .font(.subheadline)
                            .foregroundStyle(Palette.inkMuted)
                        Text(verbatim: Formatting.duration(Int(truncatingIfNeeded: day.totalMs)))
                            .font(.system(size: 40, weight: .bold, design: .rounded))
                            .monospacedDigit()
                    }
                    .padding(.vertical, 4)
                }

                if day.totalMs == 0 {
                    Section {
                        L("agent.mytime.empty").font(.footnote).foregroundStyle(Palette.inkMuted)
                    }
                }

                Section {
                    DayRibbonView(totals: hourlyTotals).padding(.vertical, 4)
                }

                if !day.apps.isEmpty {
                    Section(header: L("child.apps")) {
                        ForEach(Array(day.apps.prefix(8).enumerated()), id: \.element.package) { index, app in
                            HStack {
                                Circle()
                                    .fill(Palette.series[index % Palette.series.count])
                                    .frame(width: 10, height: 10)
                                Text(verbatim: app.label)
                                Spacer()
                                Text(verbatim: Formatting.duration(Int(truncatingIfNeeded: app.foregroundMs)))
                                    .monospacedDigit()
                                    .foregroundStyle(Palette.inkMuted)
                            }
                        }
                    }
                }
            } else if model.myTimeError == nil {
                Section { ProgressView() }
            }

            Section {
                L("agent.mytime.help").font(.footnote).foregroundStyle(Palette.inkMuted)
            }
        }
        .navigationTitle(L("agent.mytime.title"))
        // Sequenced, not two concurrent `.task`s: both write the shared
        // `myTimeError`, so if they raced, a day fetch failing and the strip
        // succeeding afterwards would clear the error while `myDay` stayed
        // nil — a spinner with nothing behind it and no way to know why.
        // Sequencing restores the all-or-nothing error semantics the original
        // combined call had, while `selectMyDay` alone (picking a day) still
        // costs exactly one request.
        .task { await load() }
    }

    private func load() async {
        await model.loadMyTimeStrip()
        await model.selectMyDay(model.mySelectedDay)
    }
}
