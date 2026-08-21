package ch.jorisda.nestling.agent.pair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnrollPayloadTest {
    @Test
    fun `parses the dashboard payload`() {
        val parsed = EnrollPayloadParser.parse("nestling://enroll?url=https://nestling.jorisda.ch&code=9XWVQQKF")
        assertEquals("https://nestling.jorisda.ch", parsed?.baseUrl)
        assertEquals("9XWVQQKF", parsed?.code)
    }

    @Test
    fun `rejects a payload with no url`() {
        assertNull(EnrollPayloadParser.parse("nestling://enroll?code=9XWVQQKF"))
    }

    @Test
    fun `rejects a non-https server`() {
        // Otherwise a tampered QR could point the child's data at plain http.
        assertNull(EnrollPayloadParser.parse("nestling://enroll?url=http://evil.example&code=9XWVQQKF"))
    }

    @Test
    fun `allows http only for localhost development`() {
        val parsed = EnrollPayloadParser.parse("nestling://enroll?url=http://localhost:8099&code=9XWVQQKF")
        assertEquals("http://localhost:8099", parsed?.baseUrl)
    }

    @Test
    fun `rejects a foreign scheme`() {
        assertNull(EnrollPayloadParser.parse("https://nestling.jorisda.ch/?code=9XWVQQKF"))
    }

    @Test
    fun `uppercases a hand-typed code`() {
        assertEquals(
            "9XWVQQKF",
            EnrollPayloadParser.parse("nestling://enroll?url=https://x.test&code=9xwvqqkf")?.code,
        )
    }

    @Test
    fun `rejects an empty code`() {
        assertNull(EnrollPayloadParser.parse("nestling://enroll?url=https://x.test&code="))
    }
}
