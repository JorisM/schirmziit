import SwiftUI

/// `.buttonStyle(.plain)` removes SwiftUI's press feedback entirely, which left
/// the strip's bars feeling dead under a finger. This restores it in the app's
/// own vocabulary and honours the reduced-motion setting.
///
/// `.plain` also supplied a disabled dimming we do not get for free: the strip
/// is deliberately `.disabled` while a day switch is in flight (see
/// `AgentMyTimeView`'s comment on that modifier) so a tap on a slow connection
/// doesn't look like a broken button. Reading `isEnabled` here and applying a
/// fixed opacity — not a fade the snapshot host's missing display link can
/// freeze mid-transition — keeps that affordance without depending on
/// wherever a private system animation happened to stall.
struct PressableBarStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1, anchor: .bottom)
            .brightness(configuration.isPressed ? 0.06 : 0)
            .opacity(isEnabled ? 1 : 0.5)
            .animation(Motion.animation(Motion.fast, reduceMotion: reduceMotion), value: configuration.isPressed)
            .animation(Motion.animation(Motion.fast, reduceMotion: reduceMotion), value: isEnabled)
    }
}
