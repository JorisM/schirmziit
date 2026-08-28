package ch.jorisda.schirmziit.agent.ui.parent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.parent.QrMatrix
import kotlin.math.floor

/**
 * Draws the matrix the server sent — the Android half of the dashboard's
 * `QrCode` and of iOS's `QrMatrixView`.
 *
 * Dark on light in both themes, deliberately. A camera finds a code by its
 * contrast and expects dark modules on a light ground; an inverted QR is
 * refused outright by some scanners and read slowly by the rest. A square that
 * looks at home in dark mode and will not scan is worse than one that looks
 * like a sticker stuck on the card.
 */
@Composable
fun QrMatrixImage(matrix: QrMatrix, description: String, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            // A fixed side, not `fillMaxWidth`: past this a QR gains nothing a
            // camera can use, and a square the width of a phone reads as the
            // subject of the card rather than one step of it.
            .size(200.dp)
            .background(PAPER)
            .semantics { contentDescription = description },
    ) {
        drawMatrix(matrix)
    }
}

/** Not `Color.White`/`Color.Black`: pure black modules on pure white bloom
 *  under a phone camera's exposure. These are the same two the other two
 *  surfaces draw with. */
private val PAPER = Color(0xFFFFFFFF)
private val INK = Color(0xFF101014)

private fun DrawScope.drawMatrix(matrix: QrMatrix) {
    // Floored to whole pixels: a fractional module edge is antialiased grey on
    // both sides, and grey edges are what a scanner reads as noise. The
    // leftover is centred, so the quiet zone stays even.
    val module = floor(size.minDimension / matrix.size)
    if (module < 1f) return
    val drawn = module * matrix.size
    val left = (size.width - drawn) / 2f
    val top = (size.height - drawn) / 2f

    for (y in 0 until matrix.size) {
        var x = 0
        while (x < matrix.size) {
            if (!matrix.isDark(x, y)) {
                x++
                continue
            }
            // Runs, not modules: a version-4 code is ~1700 draw calls a frame
            // otherwise, on a card that also holds a list and a chart.
            var end = x
            while (end + 1 < matrix.size && matrix.isDark(end + 1, y)) end++
            drawRect(
                color = INK,
                topLeft = androidx.compose.ui.geometry.Offset(left + x * module, top + y * module),
                size = androidx.compose.ui.geometry.Size(module * (end - x + 1), module),
            )
            x = end + 1
        }
    }
}
