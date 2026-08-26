import SwiftUI

/// The child's own fourteen days and the detail of one — the same shapes
/// `ChildDetailView` draws for a parent, fetched here over this phone's own
/// device token instead of a parent's session. The product's whole promise is
/// that this screen and the parent's dashboard never disagree.
struct AgentMyTimeView: View {
    let model: AgentModel

    private var today: String { ISO8601DateFormatter.dayOnly.string(from: Date()) }

    /// `AppTotalFfi.foregroundMs` is an `Int64`; `Formatting.splitApps` takes
    /// the same `(label, ms: Int)` tuple `AppRowsView` maps `UsageSeries`
    /// into, so this is the one place the type gets narrowed for it.
    private func appSplit(_ apps: [AppTotalFfi]) -> (shown: [(label: String, ms: Int)], brief: [(label: String, ms: Int)]) {
        Formatting.splitApps(apps.map { (label: $0.label, ms: Int(truncatingIfNeeded: $0.foregroundMs)) })
    }

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
                        .disabled(model.myTimeBusy)
                }
            }

            Section {
                DayStripView(
                    days: model.myDays.map { (day: $0.day, ms: Int(truncatingIfNeeded: $0.foregroundMs)) },
                    selected: model.mySelectedDay,
                    onSelect: { day in Task { await model.selectMyDay(day) } }
                )
                .padding(.vertical, 4)
                // `selectMyDay` no-ops while a load is already in flight, so
                // without this a tap on a slow connection did nothing at all
                // and looked like a broken button, not a busy one.
                .disabled(model.myTimeBusy)
                if model.myTimeBusy {
                    StripSkeleton().padding(.vertical, 4)
                }
            }

            if let day = model.myDay {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        L(model.mySelectedDay == today ? "child.total" : "child.selected")
                            .font(.subheadline)
                            .foregroundStyle(Palette.inkMuted)
                        // Deliberately not a count-up: the parent's list celebrates
                        // the habit of looking, but a number racing upward reads as a
                        // score to the child it describes.
                        Text(verbatim: Formatting.duration(Int(truncatingIfNeeded: day.totalMs)))
                            .font(.system(size: 40, weight: .bold, design: .rounded))
                            .monospacedDigit()
                        // The core already parses this for us (`day.unlockCount`);
                        // both parent surfaces and Android's child screen show it,
                        // and the site now claims all four show the same numbers.
                        // `LocalizedStringKey` interpolation needs an exact format-specifier
                        // match with the string table's `%lld` key — `Int32`
                        // formats as `%d` and silently misses the table, falling
                        // back to the raw, untranslated key text.
                        L("child.unlocks \(Int(day.unlockCount))")
                            .font(.subheadline)
                            .foregroundStyle(Palette.inkMuted)
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
                    // `Formatting.visibleApps` is the tested seam: the cap
                    // applies to `shown` alone, after the split, so a brief
                    // app already folded behind the disclosure can never be
                    // the thing the cap pushes out.
                    let visible = Formatting.visibleApps(appSplit(day.apps), cap: Formatting.appRowCap)
                    Section(header: L("child.apps")) {
                        ForEach(Array(visible.shown.enumerated()), id: \.offset) { index, entry in
                            appRow(entry, index: index)
                        }
                        if !visible.brief.isEmpty {
                            DisclosureGroup {
                                ForEach(Array(visible.brief.enumerated()), id: \.offset) { index, entry in
                                    appRow(entry, index: visible.shown.count + index)
                                }
                            } label: {
                                Text(verbatim: "\(S("child.apps.brief")) (\(visible.brief.count))")
                            }
                        }
                    }
                }
            } else if model.myTimeError == nil {
                Section { RowsSkeleton() }
                Section { RibbonSkeleton() }
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

    private func appRow(_ entry: (label: String, ms: Int), index: Int) -> some View {
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
