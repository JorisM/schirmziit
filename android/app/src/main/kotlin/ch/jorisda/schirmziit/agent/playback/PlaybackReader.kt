package ch.jorisda.schirmziit.agent.playback

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.provider.Settings
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

/**
 * The Android implementation. Reads exactly two things off each controller —
 * the package and whether it is playing — and nothing else. `MediaMetadata` is
 * never touched; see [PlaybackState].
 */
class MediaSessionPlaybackReader(private val context: Context) : PlaybackReader {

    override fun active(): List<PlaybackState> {
        if (!hasPermission()) return emptyList()
        val manager = context.getSystemService(MediaSessionManager::class.java)
            ?: return emptyList()
        val component = ComponentName(context, PlaybackListener::class.java)
        // Throws if the grant was revoked between the check and the call, which
        // is a race a family can cause from Settings at any moment.
        return runCatching { manager.getActiveSessions(component) }
            .getOrDefault(emptyList())
            .map {
                PlaybackState(
                    packageName = it.packageName,
                    // STATE_PLAYING only. A session parked at STATE_PAUSED for
                    // eight hours is not eight hours of listening.
                    playing = it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING,
                )
            }
    }

    /**
     * Notification access is an AppOps-style grant made in Settings, not a
     * runtime permission, so it is read out of the enabled-listeners setting.
     */
    override fun hasPermission(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it)?.packageName == context.packageName
        }
    }
}
