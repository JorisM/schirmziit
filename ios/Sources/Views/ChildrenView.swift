import SwiftUI

struct ChildrenView: View {
    let client: ApiClient
    let onSignOut: () -> Void

    /// Non-private, like `ChildDetailView.usage`, so a snapshot test can build
    /// the loaded list directly: this view fetches in `.task`, which the
    /// off-screen snapshot host does not reliably run to completion inside the
    /// settle wait, and a golden of a spinner proves nothing about the screen.
    @State var children: [ChildResponse] = []
    @State private var error: AppError?
    @State private var showHelp = false
    @State private var path: [ChildResponse] = []
    @State private var addingChild = false
    @State private var newChildName = ""
    /// The child a swipe has proposed removing. Optional rather than a bool
    /// beside an id: the dialog names the child it is about, and a bool can go
    /// true while the id it belongs to is stale.
    @State private var pendingRemoval: ChildResponse?
    @State private var busy = false

    var body: some View {
        NavigationStack(path: $path) {
            List {
                if let error {
                    ErrorView(error: error) { Task { await load() } }
                }

                if children.isEmpty && error == nil {
                    VStack(alignment: .leading, spacing: 8) {
                        L("children.empty").font(.headline)
                        L("children.empty.hint")
                            .font(.footnote)
                            .foregroundStyle(Palette.inkMuted)
                        // The empty state carries the action it is asking for.
                        // The toolbar button is there too, but a parent reading
                        // "add a child" should not have to go looking for where.
                        //
                        // `L(…)`, not `S(…)`: this label is on screen, and
                        // `String(localized:)` follows the *process* locale —
                        // which rendered "Add a child" inside the German
                        // snapshot until this was changed.
                        Button(action: { addingChild = true }) {
                            Label {
                                L("children.add")
                            } icon: {
                                Image(systemName: "plus")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Palette.accent)
                        // `schirmziitList()` sets `foregroundStyle(Palette.ink)`
                        // for the whole list, which a prominent button inherits
                        // — near-black on the teal fill, about 2.7:1 and under
                        // AA. Card-on-accent is what the dashboard's own primary
                        // button uses, and passes in both appearances.
                        .foregroundStyle(Palette.card)
                        .disabled(busy)
                    }
                    .padding(.vertical, 4)
                }

                ForEach(children) { child in
                    NavigationLink(value: child) {
                        HStack {
                            Text(verbatim: child.displayName)
                            Spacer()
                            CountingTotal(targetMs: child.todayMs)
                        }
                    }
                    // `allowsFullSwipe: false` deliberately: a full swipe would
                    // fire the destructive action from the gesture alone, and
                    // this list is the screen a parent opens every day. The
                    // swipe only ever opens the question.
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) { pendingRemoval = child } label: {
                            Label {
                                L("children.remove")
                            } icon: {
                                Image(systemName: "trash")
                            }
                        }
                    }
                }
            }
            .schirmziitList()
            // A child appearing or disappearing is the list's own change, so it
            // moves rather than blinks. `Motion.animation` returns nil under
            // reduced motion, which lands on the new list instantly.
            .motion(Motion.base, value: children.count)
            .navigationTitle(L("children.title"))
            .navigationDestination(for: ChildResponse.self) { child in
                ChildDetailView(child: child, client: client)
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("app.help", systemImage: "questionmark.circle") { showHelp = true }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("app.signout", systemImage: "rectangle.portrait.and.arrow.right", action: onSignOut)
                }
                // Where iOS puts Add. The bottom bar was tried and rejected: a
                // lone glyph at the foot of a mostly empty list reads as
                // decoration, and this list is mostly empty for most families.
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: { addingChild = true }) {
                        Label {
                            L("children.add")
                        } icon: {
                            Image(systemName: "plus")
                        }
                    }
                    .disabled(busy)
                }
            }
            .sheet(isPresented: $showHelp) { HelpView() }
            .alert(S("children.add.title"), isPresented: $addingChild) {
                TextField(S("children.add.placeholder"), text: $newChildName)
                    .textInputAutocapitalization(.words)
                Button(S("children.add.save")) { Task { await add() } }
                Button(S("app.cancel"), role: .cancel) { newChildName = "" }
            }
            .confirmationDialog(
                Text(verbatim: pendingRemoval?.displayName ?? ""),
                isPresented: Binding(
                    get: { pendingRemoval != nil },
                    set: { if !$0 { pendingRemoval = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button(S("children.remove.confirm"), role: .destructive) {
                    if let child = pendingRemoval { Task { await remove(child) } }
                }
                Button(S("app.cancel"), role: .cancel) { pendingRemoval = nil }
            } message: {
                L("children.remove.body")
            }
            .refreshable { await load() }
            .task {
                await load()
#if DEBUG
                // Debug builds only: lets a screenshot run open the first child
                // without a tap. Absent from release builds.
                if ProcessInfo.processInfo.environment["SCHIRMZIIT_OPEN_FIRST_CHILD"] == "1",
                   let first = children.first {
                    path = [first]
                }
#endif
            }
        }
    }

    /// The two writes this screen makes, as plain `static func`s for the reason
    /// `ChildDetailViewTests` records: this view owns no `@Observable` model, and
    /// `@State` on a view SwiftUI has not installed silently loses writes — so
    /// the part worth testing is kept out of the view lifecycle entirely.
    static func create(client: ApiClient, name: String) async -> WriteOutcome {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        // The Add button is disabled on a blank field; this is the second line
        // of defence. It returns a failure rather than `.ok`, because a request
        // that was never sent must never read as a child that was created.
        // SZ-E301 is the catalog's "the server could not use that", which is
        // what an empty name would have got from the server anyway. The
        // disabled button is the line of defence a parent actually meets.
        guard !trimmed.isEmpty else { return .failed(AppError.local(.validationFailed)) }

        return await WriteOutcome.of {
            _ = try await client.post(
                "v1/children",
                body: ["display_name": trimmed],
                as: ChildResponse.self
            )
        }
    }

    static func remove(client: ApiClient, childId: String) async -> WriteOutcome {
        await WriteOutcome.of { try await client.delete("v1/children/\(childId)") }
    }

    @MainActor
    private func add() async {
        busy = true
        defer { busy = false }
        switch await Self.create(client: client, name: newChildName) {
        case .ok:
            newChildName = ""
            error = nil
            // Re-read rather than append the created child: the list carries
            // today's total per child, and a locally-appended row would sit
            // there at zero even for a child whose phone is already reporting.
            await load()
        case .failed(let failure):
            error = failure
        }
    }

    @MainActor
    private func remove(_ child: ChildResponse) async {
        busy = true
        defer { busy = false }
        pendingRemoval = nil
        switch await Self.remove(client: client, childId: child.id) {
        case .ok:
            error = nil
            await load()
        case .failed(let failure):
            // The row stays: a delete that failed must not leave the parent
            // looking at a list the server does not agree with.
            error = failure
        }
    }

    private func load() async {
        do {
            let zone = TimeZone.current.identifier
            children = try await client.get("v1/children?tz=\(zone)", as: [ChildResponse].self)
            error = nil
        } catch let caught as AppError {
            error = caught
        } catch {
            self.error = AppError.transport(error, endpoint: "v1/children")
        }
    }
}

extension ChildResponse: Hashable {
    static func == (lhs: ChildResponse, rhs: ChildResponse) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

/// Today's total, counting up. Read-only and self-contained so the list row stays
/// a list row.
private struct CountingTotal: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let targetMs: Int64
    @State private var start = Date()
    // Once the eased progress reaches 1, the timeline has nothing left to earn
    // its 60 fps redraws with — a `TimelineView(.animation)` never stops on its
    // own, and a list screen that keeps a display link alive forever after the
    // count-up finishes is a battery cost with no matching benefit.
    @State private var settled = false

    var body: some View {
        // Reduced motion never enters the timeline at all: an animation that
        // starts and is immediately finished is still an animation.
        if reduceMotion || settled {
            label(targetMs)
        } else {
            TimelineView(.animation(minimumInterval: 1.0 / 60.0, paused: false)) { context in
                let elapsed = context.date.timeIntervalSince(start) / Motion.hero
                // Assigned, not interpolated, at the end: a total that stops one
                // millisecond short formats as the wrong duration.
                let eased = elapsed >= 1 ? 1 : 1 - pow(1 - max(0, elapsed), 3)
                label(Int64(Double(targetMs) * eased))
                    .onChange(of: eased) { _, newValue in
                        if newValue >= 1 { settled = true }
                    }
            }
        }
    }

    private func label(_ ms: Int64) -> some View {
        VStack(alignment: .trailing) {
            Text(verbatim: Formatting.duration(Int(ms)))
                .monospacedDigit()
            L("children.todayTotal")
                .font(.caption)
                .foregroundStyle(Palette.inkFaint)
        }
    }
}
