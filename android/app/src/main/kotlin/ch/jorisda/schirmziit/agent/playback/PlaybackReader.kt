package ch.jorisda.schirmziit.agent.playback

import ch.jorisda.schirmziit.agent.core.EventKind
import ch.jorisda.schirmziit.agent.core.RawEvent

/**
 * What a media session tells us, and nothing else.
 *
 * MediaController also exposes MediaMetadata — track title, artist, artwork.
 * This product measures how long and when, never what. The seam is this type:
 * there is no field a title could travel in, PlaybackReaderTest asserts the
 * field list, and scripts/check-no-content.sh fails the build if any main
 * source reaches for metadata or notification content.
 */
data class PlaybackState(val packageName: String, val playing: Boolean)

/** The one seam over Android's media-session APIs. Everything above it is testable. */
interface PlaybackReader {
    fun active(): List<PlaybackState>

    /**
     * Notification access, which is what MediaSessionManager requires. Optional:
     * a family that declines simply reports background_measured = false.
     */
    fun hasPermission(): Boolean
}

/**
 * Diff two snapshots into transitions. Pure, so the whole start/stop rule is
 * testable on the JVM; only the Android implementation needs a device.
 */
fun playbackEvents(
    previous: Set<String>,
    current: List<PlaybackState>,
    atMillis: Long,
): List<RawEvent> {
    val playing = current.filter { it.playing }.map { it.packageName }.toSet()
    val started = (playing - previous).map { RawEvent(atMillis, EventKind.PlaybackStarted(it)) }
    val stopped = (previous - playing).map { RawEvent(atMillis, EventKind.PlaybackStopped(it)) }
    return started + stopped
}
