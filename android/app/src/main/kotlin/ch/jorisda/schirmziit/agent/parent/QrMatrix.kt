package ch.jorisda.schirmziit.agent.parent

import org.json.JSONObject

/**
 * The pairing link drawn as modules, exactly as the server sent it — `1` dark,
 * `0` light, quiet zone included.
 *
 * Nothing here encodes anything. zxing is a dependency of this app already (the
 * child side scans with it) and could draw this locally, but then the dashboard,
 * this phone and an iPhone would each own an encoder, and a family reading a
 * square that scans as something else would have three places to look. The one
 * encoder lives in `crates/core`; this is a renderer.
 */
data class QrMatrix(val size: Int, val rows: List<String>) {
    fun isDark(x: Int, y: Int): Boolean = rows[y][x] == '1'
}

/**
 * Null unless the object really is a square of that size: a matrix rendered
 * from a truncated or ragged body is a QR that scans as nothing, and the code
 * and the address beside it pair the phone perfectly well without it.
 */
fun qrMatrixFrom(parsed: JSONObject?): QrMatrix? {
    if (parsed == null) return null
    val size = parsed.optInt("size", 0)
    if (size <= 0) return null

    val rows = parsed.optJSONArray("rows") ?: return null
    if (rows.length() != size) return null

    val lines = (0 until size).map { index -> rows.optString(index) }
    if (lines.any { line -> line.length != size || line.any { it != '0' && it != '1' } }) {
        return null
    }
    return QrMatrix(size, lines)
}
