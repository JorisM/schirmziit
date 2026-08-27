import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppVersionTest {
    @Test
    fun `encodes two digits per part`() {
        assertEquals(100, versionCodeOf("0.1.0"))
        assertEquals(10203, versionCodeOf("1.2.3"))
        assertEquals(10000, versionCodeOf("1.0.0"))
    }

    // The failure the whole function exists to prevent. Play accepts a
    // versionCode once, and refuses anything that is not higher than the last
    // one it saw — a release published under a code that later decreases can
    // never be replaced.
    @Test
    fun `the code rises with the version and never repeats`() {
        val versions = listOf("0.1.0", "0.1.1", "0.2.0", "0.9.99", "1.0.0", "1.0.1", "2.0.0")
        val codes = versions.map(::versionCodeOf)
        assertEquals(codes.sorted(), codes, "versionCode must increase with the version: $versions -> $codes")
        assertEquals(codes.distinct(), codes, "two versions must never share a code: $versions -> $codes")
    }

    @Test
    fun `refuses a version it cannot encode`() {
        assertFailsWith<IllegalArgumentException> { versionCodeOf("1.0") }
        assertFailsWith<IllegalArgumentException> { versionCodeOf("0.1.0-rc1") }
        assertFailsWith<IllegalArgumentException> { versionCodeOf("1.0.100") }
    }
}
