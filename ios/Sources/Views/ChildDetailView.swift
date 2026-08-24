import SwiftUI

struct ChildDetailView: View {
    let child: ChildResponse
    let client: ApiClient

    @State private var usage: UsageResponse?
    @State private var strip: UsageResponse?
    @State private var selected = ISO8601DateFormatter.dayOnly.string(from: Date())
    @State private var errorText: String?

    private static let stripDays = 14

    private var from: String {
        let start = Calendar.current.date(byAdding: .day, value: -(Self.stripDays - 1), to: Date()) ?? Date()
        return ISO8601DateFormatter.dayOnly.string(from: start)
    }

    private var today: String { ISO8601DateFormatter.dayOnly.string(from: Date()) }

    var body: some View {
        List {
            if let errorText {
                Section {
                    Label(errorText, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(Palette.urgent)
                }
            }

            if let usage {
                Section {
                    DayStripView(
                        days: Formatting.dailyTotals(strip?.series ?? [], from: from, to: today),
                        selected: selected,
                        onSelect: { selected = $0 }
                    )
                    .padding(.vertical, 4)
                }

                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        L(selected == today ? "child.total" : "child.selected")
                            .font(.subheadline)
                            .foregroundStyle(Palette.inkMuted)
                        Text(verbatim: Formatting.duration(usage.screenTimeMs))
                            .font(.system(size: 40, weight: .bold, design: .rounded))
                            .monospacedDigit()
                        L("child.unlocks \(usage.unlocks)")
                            .font(.subheadline)
                            .foregroundStyle(Palette.inkMuted)
                    }
                    .padding(.vertical, 4)
                }

                if usage.screenTimeMs == 0 {
                    Section {
                        VStack(alignment: .leading, spacing: 4) {
                            L("child.nodata").font(.headline)
                            L("child.nodata.hint")
                                .font(.footnote)
                                .foregroundStyle(Palette.inkMuted)
                        }
                    }
                }

                Section {
                    DayRibbonView(totals: usage.deviceTotals).padding(.vertical, 4)
                }

                if !usage.series.isEmpty {
                  Section(header: L("child.apps")) {
                    let ranked = usage.series.sorted { $0.totalMs > $1.totalMs }
                    ForEach(Array(ranked.prefix(8).enumerated()), id: \.element.id) { index, entry in
                        HStack {
                            Circle()
                                .fill(Palette.series[index % Palette.series.count])
                                .frame(width: 10, height: 10)
                            Text(verbatim: entry.label)
                            Spacer()
                            Text(verbatim: Formatting.duration(entry.totalMs))
                                .monospacedDigit()
                                .foregroundStyle(Palette.inkMuted)
                        }
                    }
                  }
                }

                Section(header: L("devices.title")) {
                    ForEach(usage.devices) { device in
                        VStack(alignment: .leading, spacing: 2) {
                            HStack {
                                Image(systemName: device.stale ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                                    .foregroundStyle(device.stale ? Palette.warn : Palette.ok)
                                Text(verbatim: device.label)
                                Spacer()
                                L(device.stale ? "devices.stale" : "devices.fresh")
                                    .font(.subheadline)
                                    .foregroundStyle(device.stale ? Palette.warn : Palette.ok)
                            }
                            if let lastSeen = device.lastSeenAt {
                                Text(lastSeen, format: .relative(presentation: .named))
                                    .font(.footnote)
                                    .foregroundStyle(Palette.inkFaint)
                            } else {
                                L("devices.never")
                                    .font(.footnote)
                                    .foregroundStyle(Palette.inkFaint)
                            }
                        }
                    }
                    if usage.devices.contains(where: \.stale) {
                        L("devices.stale.help")
                            .font(.footnote)
                            .foregroundStyle(Palette.inkMuted)
                    }
                }
            } else if errorText == nil {
                Section { ProgressView() }
            }
        }
        .navigationTitle(child.displayName)
        .refreshable { await load() }
        .task { await loadStrip() }
        // id: selected — selecting a day re-issues the day request and nothing
        // else. The strip is fourteen days of rows; re-fetching it on every tap
        // would be the expensive half of the screen doing the least work.
        .task(id: selected) { await load() }
    }

    private func load() async {
        let zone = TimeZone.current.identifier
        do {
            usage = try await client.get(
                "v1/children/\(child.id)/usage?from=\(selected)&to=\(selected)&bucket=hour&tz=\(zone)",
                as: UsageResponse.self
            )
            errorText = nil
        } catch let ApiError.problem(problem) {
            errorText = problem.detail
        } catch {
            errorText = S("error.offline")
        }
    }

    private func loadStrip() async {
        let zone = TimeZone.current.identifier
        strip = try? await client.get(
            "v1/children/\(child.id)/usage?from=\(from)&to=\(today)&bucket=day&tz=\(zone)",
            as: UsageResponse.self
        )
    }
}

extension ISO8601DateFormatter {
    static let dayOnly: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        return formatter
    }()
}
