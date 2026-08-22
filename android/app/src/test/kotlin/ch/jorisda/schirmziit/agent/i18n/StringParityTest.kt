package ch.jorisda.schirmziit.agent.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A missing translation shows an English sentence in the middle of a German
 * screen, which is exactly the kind of thing nobody notices until a child does.
 * This reads the resource files directly so the check costs no device.
 */
class StringParityTest {
    private val res = File("src/main/res")
    private val locales = listOf("values-de", "values-fr", "values-it")

    private fun keys(dir: String): Set<String> =
        Regex("""<string name="([^"]+)"""")
            .findAll(File(res, "$dir/strings.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

    private fun values(dir: String): Map<String, String> =
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File(res, "$dir/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun `every locale has every key`() {
        val reference = keys("values")
        for (locale in locales) {
            val missing = reference - keys(locale)
            assertTrue("$locale is missing: $missing", missing.isEmpty())
            val extra = keys(locale) - reference
            assertTrue("$locale has keys the default does not: $extra", extra.isEmpty())
        }
    }

    @Test
    fun `format placeholders match the default`() {
        // A translation that drops %1$s crashes at format time, not at build time.
        val reference = values("values")
        val placeholder = Regex("""%\d\$[sd]""")
        for (locale in locales) {
            values(locale).forEach { (key, text) ->
                assertEquals(
                    "$key has different placeholders in $locale",
                    placeholder.findAll(reference.getValue(key)).map { it.value }.toSet(),
                    placeholder.findAll(text).map { it.value }.toSet(),
                )
            }
        }
    }

    @Test
    fun `no locale left a value empty`() {
        for (locale in locales + "values") {
            values(locale).forEach { (key, text) ->
                assertTrue("$locale/$key is empty", text.isNotBlank())
            }
        }
    }

    /** Only the strings themselves — comments are not shipped to anyone. */
    private fun germanText(): String = values("values-de").values.joinToString(" ")

    @Test
    fun `german uses swiss spelling`() {
        assertTrue("Schweizer Hochdeutsch has no ß", !germanText().contains("ß"))
    }

    @Test
    fun `german addresses the child informally`() {
        val text = germanText()
        assertTrue("expected du-form", Regex("""\b[Dd]u\b|\bdein""").containsMatchIn(text))
        assertTrue(
            "no formal Sie-form",
            !Regex("""\bIhre[rmns]?\b|\bIhnen\b|\bSie (können|sehen|müssen)\b""").containsMatchIn(text),
        )
    }

    @Test
    fun `every locale is declared in locales_config`() {
        val declared = Regex("""android:name="([a-z]{2})"""")
            .findAll(File(res, "xml/locales_config.xml").readText())
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(setOf("en", "de", "fr", "it"), declared)
    }
}
