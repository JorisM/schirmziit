import SwiftUI

/// Durations, easing and stagger for every animated surface, in one place.
///
/// Beside `Palette` deliberately: the same reasoning applies. Four screens each
/// inventing their own 250 ms ease-out is the drift this file exists to stop.
enum Motion {
    static let fast: Double = 0.12
    static let base: Double = 0.24
    static let slow: Double = 0.40
    static let hero: Double = 0.60
    static let staggerStep: Double = 0.04

    static func staggerDelay(_ index: Int) -> Double { Double(index) * staggerStep }

    /// `nil` is SwiftUI's "apply instantly" — the reduced-motion path lands on
    /// the final state rather than running a shorter animation. `delay` defaults
    /// to zero so every existing call site (no stagger, no sweep) is unaffected;
    /// views that need a per-cell or per-row offset (the ribbon fill, the day
    /// strip and app row stagger) pass it instead of hand-rolling their own
    /// `reduceMotion ? nil : .easeOut(...).delay(...)` ternary — the point of
    /// this file is that the environment check happens in exactly one place.
    static func animation(_ duration: Double, delay: Double = 0, reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : .easeOut(duration: duration).delay(delay)
    }
}

private struct MotionModifier<Value: Equatable>: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let duration: Double
    let value: Value

    func body(content: Content) -> some View {
        content.animation(Motion.animation(duration, reduceMotion: reduceMotion), value: value)
    }
}

extension View {
    /// Animate on `value` changing. Screens call this, never `withAnimation`
    /// directly, so the environment check happens in exactly one place.
    func motion<Value: Equatable>(_ duration: Double, value: Value) -> some View {
        modifier(MotionModifier(duration: duration, value: value))
    }
}
