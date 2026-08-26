package ch.jorisda.schirmziit.agent.playback

import ch.jorisda.schirmziit.agent.core.EventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackReaderTest {

    @Test
    fun `a started session becomes one event carrying only the package and the instant`() {
        val events = playbackEvents(
            previous = emptySet(),
            current = listOf(PlaybackState("com.audiobookshelf.app", playing = true)),
            atMillis = 1_000L,
        )
        assertEquals(1, events.size)
        val kind = events.first().kind
        assertTrue(kind is EventKind.PlaybackStarted)
        assertEquals("com.audiobookshelf.app", (kind as EventKind.PlaybackStarted).packageName)
        assertEquals(1_000L, events.first().atMillis)
    }

    @Test
    fun `a session that stops becomes a stop event`() {
        val events = playbackEvents(
            previous = setOf("com.audiobookshelf.app"),
            current = emptyList(),
            atMillis = 2_000L,
        )
        assertEquals(1, events.size)
        assertTrue(events.first().kind is EventKind.PlaybackStopped)
    }

    @Test
    fun `a session that keeps playing emits nothing`() {
        val events = playbackEvents(
            previous = setOf("com.audiobookshelf.app"),
            current = listOf(PlaybackState("com.audiobookshelf.app", playing = true)),
            atMillis = 3_000L,
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a paused session counts as stopped`() {
        // Only STATE_PLAYING is playback. A session parked at STATE_PAUSED for
        // eight hours is not eight hours of listening.
        val events = playbackEvents(
            previous = setOf("com.spotify.music"),
            current = listOf(PlaybackState("com.spotify.music", playing = false)),
            atMillis = 4_000L,
        )
        assertEquals(1, events.size)
        assertTrue(events.first().kind is EventKind.PlaybackStopped)
    }

    @Test
    fun `two apps playing at once each get their own event`() {
        val events = playbackEvents(
            previous = emptySet(),
            current = listOf(
                PlaybackState("com.audiobookshelf.app", playing = true),
                PlaybackState("com.spotify.music", playing = true),
            ),
            atMillis = 5_000L,
        )
        assertEquals(2, events.size)
    }

    @Test
    fun `the emitted event has nowhere to put a track title`() {
        // MediaController exposes MediaMetadata — title, artist, artwork. The
        // seam's type surface is the guarantee that none of it can travel: a
        // PlaybackState is a package and a boolean, full stop.
        val fields = PlaybackState::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
        assertEquals(listOf("packageName", "playing"), fields)

        val rendered = playbackEvents(
            previous = emptySet(),
            current = listOf(PlaybackState("com.spotify.music", playing = true)),
            atMillis = 6_000L,
        ).joinToString { "${it.atMillis}:${it.kind}" }
        assertFalse(rendered.contains("Bohemian", ignoreCase = true))
        assertTrue(rendered.contains("com.spotify.music"))
    }
}
