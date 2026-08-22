import SwiftUI

/// The dashboard's palette, expressed as iOS semantic colours.
///
/// Deliberately not Material widgets ported over: the layout and controls follow
/// Apple's HIG (grouped lists, navigation stacks, SF Symbols, SF Pro) while the
/// colours, wording and structure carry across from the web so the phone and the
/// browser read as one product.
enum Palette {
    static let paper = Color(light: 0xF2F0EA, dark: 0x1C1A17)
    static let card = Color(light: 0xFBFAF7, dark: 0x252320)
    static let ink = Color(light: 0x232622, dark: 0xF0EDE6)
    static let inkMuted = Color(light: 0x5B5F59, dark: 0xA7A29A)
    static let inkFaint = Color(light: 0x8A8D85, dark: 0x7C776F)
    static let hairline = Color(light: 0xDFDCD2, dark: 0x3A3733)

    static let accent = Color(light: 0x00707E, dark: 0x46B3BF)
    static let ok = Color(light: 0x2F6B4C, dark: 0x4F9D72)
    static let warn = Color(light: 0xC87C2C, dark: 0xD69A4A)
    static let urgent = Color(light: 0xB4472C, dark: 0xDD7154)

    /// Sequential ramp for the day ribbon: one hue, light to dark (inverted on
    /// dark backgrounds so "more" always means "more contrast against the page").
    static let ribbon: [Color] = [
        Color(light: 0xE7E4DC, dark: 0x262B27),
        Color(light: 0xCFDCD2, dark: 0x2F4A3A),
        Color(light: 0xA3C2AD, dark: 0x3E6B50),
        Color(light: 0x6F9F83, dark: 0x559072),
        Color(light: 0x3F7D5C, dark: 0x6FAE8C),
        Color(light: 0x2F6B4C, dark: 0x8CC7A5),
    ]

    /// Fixed order, follows the app and never its rank. Validated for CVD and
    /// contrast against both surfaces; see the design doc before changing one.
    static let series: [Color] = [
        Color(light: 0x008C9E, dark: 0x1B98AB),
        Color(light: 0xD96A4E, dark: 0xD4664A),
        Color(light: 0x4757C4, dark: 0x6A79D6),
        Color(light: 0xE8933F, dark: 0xC87C2C),
        Color(light: 0x9B4F7E, dark: 0xA85E8A),
        Color(light: 0x1F6B3F, dark: 0x2F9159),
    ]
}

extension Color {
    /// One colour, two appearances — resolved by UIKit at draw time so it also
    /// follows a mid-session appearance switch.
    init(light: UInt32, dark: UInt32) {
        self = Color(UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
