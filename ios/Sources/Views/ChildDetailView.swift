import SwiftUI

struct ChildDetailView: View {
    let child: ChildResponse
    let client: ApiClient

    @State var usage: UsageResponse?
    @State var strip: UsageResponse?
    @State var stripError: String?
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
                    if let stripError {
                        // Never zero-fill in place of a failed fetch: fourteen quiet bars
                        // read as a genuinely quiet fortnight, which is exactly the "lost
                        // day" this app promises never to show.
                        Label(stripError, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(Palette.urgent)
                    } else if let strip {
                        DayStripView(
                            days: Formatting.dailyTotals(strip.series, from: from, to: today),
                            selected: selected,
                            onSelect: { selected = $0 }
                        )
                        .padding(.vertical, 4)
                    } else {
                        ProgressView()
                    }
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
                        AppRowsView(series: usage.series)
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

    // Explicitly `@MainActor`, matching `.task`/`.refreshable` (both already
    // main-actor callers) rather than leaving the isolation inferred: both
    // methods assign into `@State`, which SwiftUI expects touched from there.
    @MainActor
    func load() async {
        switch await Self.fetchUsage(client: client, childId: child.id, from: selected, to: selected, bucket: "hour") {
        case .success(let response):
            usage = response
            errorText = nil
        case .failure(let message):
            errorText = message
        }
    }

    @MainActor
    func loadStrip() async {
        switch await Self.fetchUsage(client: client, childId: child.id, from: from, to: today, bucket: "day") {
        case .success(let response):
            strip = response
            stripError = nil
        case .failure(let message):
            strip = nil
            stripError = message
        }
    }

    /// Shared by both requests so a captcha page, a timeout or a 500 always becomes
    /// a `.failure` the caller must handle — never a silently-swallowed `nil` that a
    /// view can zero-fill into a fake quiet fortnight. Swift's `Result` needs its
    /// `Failure` to conform to `Error`, which plain `String` does not — a local
    /// two-case enum is simpler here than wrapping one.
    static func fetchUsage(
        client: ApiClient,
        childId: String,
        from: String,
        to: String,
        bucket: String
    ) async -> FetchOutcome {
        let zone = TimeZone.current.identifier
        do {
            let usage = try await client.get(
                "v1/children/\(childId)/usage?from=\(from)&to=\(to)&bucket=\(bucket)&tz=\(zone)",
                as: UsageResponse.self
            )
            return .success(usage)
        } catch let ApiError.problem(problem) {
            return .failure(problem.detail)
        } catch {
            return .failure(S("error.offline"))
        }
    }
}

enum FetchOutcome {
    case success(UsageResponse)
    case failure(String)
}

extension ISO8601DateFormatter {
    /// `ISO8601DateFormatter` defaults to GMT. Every caller pairs the string this
    /// produces with a `tz=` parameter built from `TimeZone.current` — left at
    /// GMT, this answers "yesterday" for the first hour or two after local
    /// midnight in Zurich, while the request it accompanies asks the server for
    /// the local zone's today. Shared by both iOS surfaces (`ChildDetailView`,
    /// `AgentModel`), so fixing it here fixes both at once.
    static let dayOnly: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        formatter.timeZone = .current
        return formatter
    }()
}
