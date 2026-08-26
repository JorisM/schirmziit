import SwiftUI

struct ChildDetailView: View {
    let child: ChildResponse
    let client: ApiClient

    @State var usage: UsageResponse?
    @State var strip: UsageResponse?
    @State var stripError: String?
    @State private var selected = ISO8601DateFormatter.dayOnly.string(from: Date())
    @State private var errorText: String?
    // Owned here, not by DayRibbonView, so a List row recycle during scroll
    // doesn't replay the fill flourish — see DayRibbonView.filledOverride.
    @State private var ribbonFilled = false
    /// The phone a swipe has proposed disconnecting, named in the dialog.
    @State private var pendingRevoke: DeviceStatus?

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

            // Independent of `usage`: it depends only on `strip`/`stripError`, never
            // on the selected day's data, so it must stay on screen — selection
            // outline included — while a newly picked day's own sections skeleton.
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
                    StripSkeleton()
                }
            }

            if let usage {
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
                    // This screen's one flourish is the ribbon fill; on web the
                    // equivalent slot is the background-listening wave
                    // (web/src/components/DayRibbon.tsx explains that side) —
                    // whoever brings background listening to iOS should not
                    // also animate this ribbon at the same time, or both lose.
                    DayRibbonView(totals: usage.deviceTotals, filledOverride: $ribbonFilled)
                        .padding(.vertical, 4)
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
                        // Same reasoning as the children list: no full swipe, so
                        // the gesture opens the question rather than answering
                        // it. A disconnected phone cannot be reconnected without
                        // enrolling it again.
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) { pendingRevoke = device } label: {
                                Label {
                                    L("devices.revoke")
                                } icon: {
                                    Image(systemName: "bolt.horizontal.circle")
                                }
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
                Section { RowsSkeleton() }
                Section { RibbonSkeleton() }
            }
        }
        .navigationTitle(child.displayName)
        .confirmationDialog(
            Text(verbatim: pendingRevoke?.label ?? ""),
            isPresented: Binding(
                get: { pendingRevoke != nil },
                set: { if !$0 { pendingRevoke = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button(S("devices.revoke.confirm"), role: .destructive) {
                if let device = pendingRevoke { Task { await revoke(device) } }
            }
            Button(S("app.cancel"), role: .cancel) { pendingRevoke = nil }
        } message: {
            L("devices.revoke.body")
        }
        // resetting: false — pull-to-refresh must keep the loaded numbers on
        // screen while it re-fetches, not blank a loaded day back to skeletons.
        .refreshable { await load(resetting: false) }
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
    func load(resetting: Bool = true) async {
        // The previous day's numbers must not sit under a new day's heading while
        // the request is in flight: tapping Tuesday and reading Monday's total is
        // a wrong number on screen, not merely a slow one. Pull-to-refresh is a
        // different case — it re-fetches the same day, so blanking here would
        // reset a loaded day back to skeletons for no reason.
        if resetting { usage = nil }
        switch await Self.fetchUsage(client: client, childId: child.id, from: selected, to: selected, bucket: "hour") {
        case .success(let response):
            usage = response
            errorText = nil
        case .failure(let message):
            errorText = message
        }
    }

    @MainActor
    private func revoke(_ device: DeviceStatus) async {
        pendingRevoke = nil
        switch await Self.revokeDevice(client: client, deviceId: device.id) {
        case .ok:
            errorText = nil
            // A revoked device drops out of the usage response, so re-reading
            // the day is what removes the row — there is no local list to keep
            // in step with the server.
            await load(resetting: false)
        case .failed(let message):
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
    /// A phone is disconnected by its own id, never through the child's route:
    /// `DELETE /v1/children/{id}` removes the child, which is a different and
    /// much larger act than dropping one of their devices.
    static func revokeDevice(client: ApiClient, deviceId: String) async -> WriteOutcome {
        await WriteOutcome.of { try await client.delete("v1/devices/\(deviceId)") }
    }

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
