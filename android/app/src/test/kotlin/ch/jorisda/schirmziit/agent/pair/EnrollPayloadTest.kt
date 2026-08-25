package ch.jorisda.schirmziit.agent.pair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnrollPayloadTest {
    @Test
    fun `parses the dashboard payload`() {
        val parsed = EnrollPayloadParser.parse("schirmziit://enroll?url=https://api.schirmziit.ch&code=9XWVQQKF")
        assertEquals("https://api.schirmziit.ch", parsed?.baseUrl)
        assertEquals("9XWVQQKF", parsed?.code)
    }

    @Test
    fun `the shipped default server is one this build would accept`() {
        // The default is prefilled into the same field a scanned QR fills, so a
        // default the parser rejects (plain http, a typo'd scheme) would ship an
        // app that cannot pair until the parent retypes the URL by hand.
        val parsed = EnrollPayloadParser.parse("schirmziit://enroll?url=$DEFAULT_SERVER&code=9XWVQQKF")
        assertEquals(DEFAULT_SERVER, parsed?.baseUrl)
    }

    @Test
    fun `rejects a payload with no url`() {
        assertNull(EnrollPayloadParser.parse("schirmziit://enroll?code=9XWVQQKF"))
    }

    @Test
    fun `rejects a non-https server`() {
        // Otherwise a tampered QR could point the child's data at plain http.
        assertNull(EnrollPayloadParser.parse("schirmziit://enroll?url=http://evil.example&code=9XWVQQKF"))
    }

    @Test
    fun `allows http only for localhost development`() {
        val parsed = EnrollPayloadParser.parse("schirmziit://enroll?url=http://localhost:8099&code=9XWVQQKF")
        assertEquals("http://localhost:8099", parsed?.baseUrl)
    }

    @Test
    fun `rejects a foreign scheme`() {
        assertNull(EnrollPayloadParser.parse("https://api.schirmziit.ch/?code=9XWVQQKF"))
    }

    @Test
    fun `uppercases a hand-typed code`() {
        assertEquals(
            "9XWVQQKF",
            EnrollPayloadParser.parse("schirmziit://enroll?url=https://x.test&code=9xwvqqkf")?.code,
        )
    }

    @Test
    fun `rejects an empty code`() {
        assertNull(EnrollPayloadParser.parse("schirmziit://enroll?url=https://x.test&code="))
    }
}
