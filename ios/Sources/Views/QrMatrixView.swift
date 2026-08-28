import SwiftUI

/// Draws the matrix the server sent — the iPhone half of the dashboard's
/// `QrCode` and of Android's `QrMatrixImage`.
///
/// Dark on light in both themes, deliberately. A camera finds a code by its
/// contrast and expects dark modules on a light ground; an inverted QR is
/// refused outright by some scanners and read slowly by the rest. A square that
/// looks at home in dark mode and will not scan is worse than one that looks
/// like a sticker stuck on the card.
struct QrMatrixView: View {
    let matrix: QrMatrix
    let label: LocalizedStringKey

    /// A fixed side, not the section's full width: past this a QR gains nothing
    /// a camera can use, and a square the width of an iPhone reads as the
    /// subject of the screen rather than one step of a pairing.
    private let side: CGFloat = 200

    var body: some View {
        Canvas { context, size in
            // Floored to whole points: a fractional module edge is antialiased
            // grey on both sides, and grey edges are what a scanner reads as
            // noise. The remainder is centred, so the quiet zone stays even.
            let module = (size.width / CGFloat(matrix.size)).rounded(.down)
            guard module >= 1 else { return }
            let drawn = module * CGFloat(matrix.size)
            let origin = CGPoint(x: (size.width - drawn) / 2, y: (size.height - drawn) / 2)

            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.paper))

            for y in 0..<matrix.size {
                var x = 0
                while x < matrix.size {
                    guard matrix.isDark(x: x, y: y) else {
                        x += 1
                        continue
                    }
                    // Runs, not modules: a version-4 code is ~1700 fills a
                    // frame otherwise, on a screen that also holds a list.
                    var end = x
                    while end + 1 < matrix.size, matrix.isDark(x: end + 1, y: y) { end += 1 }
                    let rect = CGRect(
                        x: origin.x + CGFloat(x) * module,
                        y: origin.y + CGFloat(y) * module,
                        width: module * CGFloat(end - x + 1),
                        height: module
                    )
                    context.fill(Path(rect), with: .color(Self.ink))
                    x = end + 1
                }
            }
        }
        .frame(width: side, height: side)
        .accessibilityElement()
        .accessibilityLabel(label)
        // A QR is an image of a link, not a decoration: VoiceOver announcing it
        // as an image is what tells a parent there is something to point a
        // camera at.
        .accessibilityAddTraits(.isImage)
    }

    /// Not pure black on pure white: those bloom under a phone camera's
    /// exposure. The same two the other two surfaces draw with.
    private static let paper = Color(red: 1, green: 1, blue: 1)
    private static let ink = Color(red: 0x10 / 255, green: 0x10 / 255, blue: 0x14 / 255)
}
