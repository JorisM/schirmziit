package ch.jorisda.schirmziit.agent.playback

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
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

    /**
     * Call [onChange] whenever the answer to [active] may have changed. Two
     * different things have to trigger it: a session appearing or going away,
     * and a session already in the list starting or stopping playback. The
     * second is the one that matters — an app is opened long before a child
     * presses play, so by then the session list has not changed for minutes.
     */
    fun watch(onChange: () -> Unit)

    /** Release everything [watch] registered. */
    fun unwatch()
}

/**
 * Turns "something changed" into a snapshot, and a snapshot into events. The
 * whole rule lives here rather than in the service so it can be tested without
 * a device; what cannot be tested off-device is whether Android delivers the
 * change at all, which is why [MediaSessionPlaybackReader] registers for both
 * kinds and the phone is checked afterwards.
 */
class PlaybackWatcher(
    private val reader: PlaybackReader,
    private val handler: PlaybackHandler,
) {
    fun start() {
        reader.watch { handler.onSnapshot(reader.active()) }
        // Whatever is already playing when the service binds: a rebind in the
        // middle of a night of listening has to re-open the stretch.
        handler.onSnapshot(reader.active())
    }

    fun stop() = reader.unwatch()
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

    private val watched = mutableListOf<MediaController>()
    private var onChange: (() -> Unit)? = null

    private val sessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            follow(controllers.orEmpty())
            onChange?.invoke()
        }

    /**
     * The callback the session list cannot give us: a controller already in the
     * list flipping between playing and paused.
     */
    private val stateChanged = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            onChange?.invoke()
        }

        override fun onSessionDestroyed() {
            onChange?.invoke()
        }
    }

    override fun watch(onChange: () -> Unit) {
        if (!hasPermission()) return
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return
        this.onChange = onChange
        val component = ComponentName(context, PlaybackListener::class.java)
        manager.addOnActiveSessionsChangedListener(sessionsChanged, component)
        follow(runCatching { manager.getActiveSessions(component) }.getOrDefault(emptyList()))
    }

    override fun unwatch() {
        context.getSystemService(MediaSessionManager::class.java)
            ?.removeOnActiveSessionsChangedListener(sessionsChanged)
        follow(emptyList())
        onChange = null
    }

    /** Registers on exactly the controllers that exist now, and no others. */
    private fun follow(controllers: List<MediaController>) {
        watched.forEach { runCatching { it.unregisterCallback(stateChanged) } }
        watched.clear()
        controllers.forEach {
            runCatching { it.registerCallback(stateChanged) }.onSuccess { _ -> watched += it }
        }
    }

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
