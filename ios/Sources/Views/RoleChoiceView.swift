import SwiftUI

/// The first question the app asks. Deliberately not a settings toggle buried
/// later: what a phone is decides everything else it does.
struct RoleChoiceView: View {
    let onParent: () -> Void
    let onChild: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("role.intro")
                        .font(.callout)
                        .foregroundStyle(Palette.inkMuted)
                }

                Section {
                    choice(
                        icon: "chart.bar.doc.horizontal",
                        title: "role.parent.title",
                        body: "role.parent.body",
                        action: onParent
                    )
                    choice(
                        icon: "iphone.gen3",
                        title: "role.child.title",
                        body: "role.child.body",
                        action: onChild
                    )
                }

                Section {
                    Text("role.later")
                        .font(.footnote)
                        .foregroundStyle(Palette.inkFaint)
                }
            }
            .schirmziitList()
            .navigationTitle("app.name")
        }
    }

    private func choice(
        icon: String,
        title: LocalizedStringKey,
        body: LocalizedStringKey,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundStyle(Palette.accent)
                    .frame(width: 30)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline).foregroundStyle(Palette.ink)
                    Text(body).font(.callout).foregroundStyle(Palette.inkMuted)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.footnote)
                    .foregroundStyle(Palette.inkFaint)
            }
            .padding(.vertical, 6)
        }
        .buttonStyle(.plain)
    }
}
