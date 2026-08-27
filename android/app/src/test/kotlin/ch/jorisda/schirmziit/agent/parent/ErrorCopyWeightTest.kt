package ch.jorisda.schirmziit.agent.parent

import ch.jorisda.schirmziit.core.ErrorCode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ErrorCode.isUrgent` against `copy/errors.toml`.
 *
 * Two sources of truth for one fact is how they drift: the catalog decides
 * whether a failure is worth red, and this is the only thing stopping the
 * Kotlin copy of that decision from quietly disagreeing. iOS holds the same
 * line in `ErrorCopyTests`.
 */
class ErrorCopyWeightTest {
    private val catalog = File("../../copy/errors.toml")

    /** `[SZ-E101]` … `weight = "urgent"` → "SZ-E101" to true. */
    private fun weights(): Map<String, Boolean> {
        val text = catalog.readText()
        return Regex("""^\[(SZ-E\d+)]\s*\n\s*weight\s*=\s*"(urgent|neutral)"""", RegexOption.MULTILINE)
            .findAll(text)
            .associate { it.groupValues[1] to (it.groupValues[2] == "urgent") }
    }

    @Test
    fun `the catalog is where it is expected to be`() {
        // A silently missing file would make every assertion below vacuous.
        assertTrue("copy/errors.toml not found from ${File(".").absolutePath}", catalog.isFile)
        assertTrue("no weights parsed out of the catalog", weights().size > 20)
    }

    @Test
    fun `every code weighs what the catalog says it weighs`() {
        val catalogWeights = weights()
        for (code in ErrorCode.entries) {
            val expected = catalogWeights[code.wire] ?: continue
            assertEquals(
                "${code.wire} disagrees with copy/errors.toml about being urgent",
                expected,
                code.isUrgent,
            )
        }
    }

    @Test
    fun `every wire string is distinct and shaped like a catalog code`() {
        val wires = ErrorCode.entries.map { it.wire }
        assertEquals("a duplicated code would mislabel a screenshot", wires.size, wires.toSet().size)
        wires.forEach { wire ->
            assertTrue("$wire is not SZ-Ennn", Regex("""^SZ-E\d{3}$""").matches(wire))
        }
    }

    @Test
    fun `a wire string round-trips back to its code`() {
        for (code in ErrorCode.entries) {
            assertEquals(code, errorCodeOf(code.wire))
        }
        assertEquals("a server newer than this app is null, not a wrong code", null, errorCodeOf("SZ-E999"))
    }

    @Test
    fun `every code the catalog reaches android for has copy on android`() {
        val declared = Regex("""<string name="error_(SZ_E\d+)_title">""")
            .findAll(File("src/main/res/values/error_copy.xml").readText())
            .map { it.groupValues[1].replace('_', '-') }
            .toSet()

        // The codes the parent screens can actually reach on their own. Each has
        // to have copy, or the panel falls back to SZ-E901 and a parent reads
        // "something went wrong on the server" about their own wifi.
        val reachable = listOf(
            ErrorCode.INVALID_CREDENTIALS, ErrorCode.UNAUTHENTICATED, ErrorCode.NOT_FOUND,
            ErrorCode.VALIDATION_FAILED, ErrorCode.RATE_LIMITED, ErrorCode.OFFLINE,
            ErrorCode.TIMEOUT, ErrorCode.TLS_FAILED, ErrorCode.BAD_RESPONSE_BODY,
            ErrorCode.SERVER_UNREACHABLE, ErrorCode.BASE_URL_NOT_CONFIGURED,
            ErrorCode.LOCAL_DECODE_FAILED, ErrorCode.INTERNAL,
        )
        for (code in reachable) {
            assertTrue(
                "${code.wire} is reachable from the parent screens but has no android copy",
                code.wire in declared,
            )
        }
    }

    @Test
    fun `the fallback code always has copy in every locale`() {
        // The panel's last resort. If this one is ever missing, a parent sees a
        // raw resource name.
        for (dir in listOf("values", "values-de", "values-fr", "values-it")) {
            val text = File("src/main/res/$dir/error_copy.xml").readText()
            assertTrue("$dir lost the SZ-E901 title", text.contains("error_SZ_E901_title"))
            assertTrue("$dir lost the SZ-E901 action", text.contains("error_SZ_E901_action"))
        }
    }
}
