import SwiftUI

struct ChildDetailView: View {
    let child: ChildResponse
    let client: ApiClient

    @State private var usage: UsageResponse?
    @State private var errorText: String?

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
                    VStack(alignment: .leading, spacing: 4) {
                        L("child.total")
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
        .task { await load() }
    }

    private func load() async {
        let day = ISO8601DateFormatter.dayOnly.string(from: Date())
        let zone = TimeZone.current.identifier
        do {
            usage = try await client.get(
                "v1/children/\(child.id)/usage?from=\(day)&to=\(day)&bucket=hour&tz=\(zone)",
                as: UsageResponse.self
            )
            errorText = nil
        } catch let ApiError.problem(problem) {
            errorText = problem.detail
        } catch {
            errorText = S("error.offline")
        }
    }
}

extension ISO8601DateFormatter {
    static let dayOnly: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        return formatter
    }()
}
