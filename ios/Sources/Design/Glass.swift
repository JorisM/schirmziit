import SwiftUI

/// Liquid Glass where the OS has it, the warm card where it does not.
///
/// The deployment target is iOS 17, so every glass API is gated. The fallback is
/// not a lesser design: it is the same palette the dashboard uses, which is what
/// older phones showed all along.
// A `glassEffect` card is deliberately absent. Two attempts at one:
//
//   * untinted, it has nothing to refract on a flat page and disappears — the
//     status card read as floating text with no surface;
//   * tinted and backgrounded, it rendered as an empty white box inside a
//     Button label, with the labels gone entirely.
//
// Lists, forms, sheets and navigation bars built against the iOS 26 SDK already
// wear Liquid Glass; the material belongs to floating controls, not to every
// surface. So the glass here is on the controls, where Apple puts it.

/// The one prominent action on a screen. `.glassProminent` keeps Apple's tint
/// behaviour, so the accent stays ours without fighting the material.
struct PrimaryActionStyle: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.buttonStyle(.glassProminent)
        } else {
            content.buttonStyle(.borderedProminent)
        }
    }
}

/// A secondary action that should read as a control rather than a link.
struct SecondaryActionStyle: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.buttonStyle(.glass)
        } else {
            content.buttonStyle(.bordered)
        }
    }
}

extension View {
    func primaryAction() -> some View { modifier(PrimaryActionStyle()) }
    func secondaryAction() -> some View { modifier(SecondaryActionStyle()) }
}
