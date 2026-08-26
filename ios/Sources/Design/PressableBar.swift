import SwiftUI

/// `.buttonStyle(.plain)` removes SwiftUI's press feedback entirely, which left
/// the strip's bars feeling dead under a finger. This restores it in the app's
/// own vocabulary and honours the reduced-motion setting.
struct PressableBarStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1, anchor: .bottom)
            .brightness(configuration.isPressed ? 0.06 : 0)
            .animation(Motion.animation(Motion.fast, reduceMotion: reduceMotion), value: configuration.isPressed)
    }
}
