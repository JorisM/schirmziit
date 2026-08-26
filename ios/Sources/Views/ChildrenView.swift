import SwiftUI

struct ChildrenView: View {
    let client: ApiClient
    let onSignOut: () -> Void

    @State private var children: [ChildResponse] = []
    @State private var errorText: String?
    @State private var showHelp = false
    @State private var path: [ChildResponse] = []

    var body: some View {
        NavigationStack(path: $path) {
            List {
                if let errorText {
                    Label(errorText, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(Palette.urgent)
                }

                if children.isEmpty && errorText == nil {
                    VStack(alignment: .leading, spacing: 4) {
                        L("children.empty").font(.headline)
                        L("children.empty.hint")
                            .font(.footnote)
                            .foregroundStyle(Palette.inkMuted)
                    }
                }

                ForEach(children) { child in
                    NavigationLink(value: child) {
                        HStack {
                            Text(verbatim: child.displayName)
                            Spacer()
                            CountingTotal(targetMs: child.todayMs)
                        }
                    }
                }
            }
            .schirmziitList()
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
            }
            .sheet(isPresented: $showHelp) { HelpView() }
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

    private func load() async {
        do {
            let zone = TimeZone.current.identifier
            children = try await client.get("v1/children?tz=\(zone)", as: [ChildResponse].self)
            errorText = nil
        } catch let ApiError.problem(problem) {
            errorText = problem.detail
        } catch {
            errorText = S("error.offline")
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
