import SwiftUI

/// HIG structure, Schirmziit colours.
///
/// The layout, controls and navigation stay native — grouped lists, form rows,
/// SF Symbols — while the surfaces take the dashboard's warm oat/ink palette so
/// the phone and the browser read as one product.
struct SchirmziitListStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .background(Palette.paper)
            .listRowBackground(Palette.card)
            .foregroundStyle(Palette.ink)
            .tint(Palette.accent)
    }
}

extension View {
    func schirmziitList() -> some View { modifier(SchirmziitListStyle()) }
}
