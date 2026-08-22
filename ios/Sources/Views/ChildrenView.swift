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
                        Text(verbatim: child.displayName)
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
            children = try await client.get("v1/children", as: [ChildResponse].self)
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
