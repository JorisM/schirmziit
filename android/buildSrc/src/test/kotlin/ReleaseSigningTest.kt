import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReleaseSigningTest {
    @Test
    fun `a keystore and its password sign the build`() {
        val signing = releaseSigningOf("/keys/schirmziit-release.jks", "hunter2", null)
        assertEquals("/keys/schirmziit-release.jks", signing?.keystore)
        assertEquals("hunter2", signing?.password)
        assertEquals("schirmziit", signing?.alias, "the alias in the keystore we ship")
    }

    @Test
    fun `another keystore may name its key something else`() {
        assertEquals("upload", releaseSigningOf("/keys/other.jks", "hunter2", "upload")?.alias)
    }

    @Test
    fun `nothing configured leaves the build unsigned, as it was`() {
        assertNull(releaseSigningOf(null, null, null))
    }

    // GitHub expands a secret that is not set to the empty string, never to
    // nothing: a fork, or this repo before the keystore existed, hands every
    // one of these through as "". Reading that as "configured" turns a build
    // that should quietly stay unsigned into one that dies on a keystore
    // called "".
    @Test
    fun `an unset secret arrives as an empty string and still means unsigned`() {
        assertNull(releaseSigningOf("", "", ""))
        assertNull(releaseSigningOf("   ", "\n", null))
    }

    // The failure this function exists to prevent: half a keystore produces an
    // APK that is named for a release and cannot be installed. Loud here beats
    // discovering it on the releases page.
    @Test
    fun `half a keystore is a mistake, not an unsigned build`() {
        assertFailsWith<IllegalArgumentException> { releaseSigningOf("/keys/release.jks", null, null) }
        assertFailsWith<IllegalArgumentException> { releaseSigningOf(null, "hunter2", null) }
        assertFailsWith<IllegalArgumentException> { releaseSigningOf("/keys/release.jks", "  ", null) }
    }

    // An alias alone is not a configuration — it has a default, so it says
    // nothing about whether a keystore was meant to be there.
    @Test
    fun `an alias on its own does not ask for a signed build`() {
        assertNull(releaseSigningOf(null, null, "upload"))
    }
}
