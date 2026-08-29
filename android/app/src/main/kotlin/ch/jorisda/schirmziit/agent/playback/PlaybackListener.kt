package ch.jorisda.schirmziit.agent.playback

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ch.jorisda.schirmziit.agent.core.EventKind
import ch.jorisda.schirmziit.agent.store.AgentDatabase
import ch.jorisda.schirmziit.agent.store.PlaybackEventRow
import ch.jorisda.schirmziit.agent.store.QueueDao

/**
 * Turns media-session snapshots into raw events. Keeps only the set of packages
 * currently playing, so the whole start/stop rule stays testable on the JVM.
 */
class PlaybackHandler(
    private val dao: QueueDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var lastPlaying: Set<String> = emptySet()

    @Synchronized
    fun onSnapshot(current: List<PlaybackState>) {
        val events = playbackEvents(lastPlaying, current, nowMillis())
        lastPlaying = current.filter { it.playing }.map { it.packageName }.toSet()
        if (events.isEmpty()) return
        dao.appendPlayback(
            events.mapNotNull { event ->
                when (val kind = event.kind) {
                    is EventKind.PlaybackStarted -> PlaybackEventRow(
                        atMillis = event.atMillis,
                        packageName = kind.packageName,
                        started = true,
                    )
                    is EventKind.PlaybackStopped -> PlaybackEventRow(
                        atMillis = event.atMillis,
                        packageName = kind.packageName,
                        started = false,
                    )
                    else -> null
                }
            },
        )
    }
}

/**
 * Host for [MediaSessionManager], which is the only public route to attributing
 * background audio to an app. Notification access is what Android requires for
 * it; notifications themselves are never read — both callbacks below are empty
 * and `scripts/check-no-content.sh` fails the build if that ever changes.
 *
 * A NotificationListenerService is bound and restarted by the system, which is
 * exactly why it is the right host: no foreground service of ours, no battery
 * budget spent keeping a process alive.
 */
class PlaybackListener : NotificationListenerService() {

    private val reader by lazy { MediaSessionPlaybackReader(this) }
    private val handler by lazy { PlaybackHandler(AgentDatabase.get(this).queue()) }

    private val onSessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener { handler.onSnapshot(reader.active()) }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(this, PlaybackListener::class.java)
        // A rebind mid-playback must re-open the stretch, or a night that
        // started before the rebind is lost entirely.
        handler.onSnapshot(reader.active())
        manager.addOnActiveSessionsChangedListener(onSessionsChanged, component)
    }

    override fun onListenerDisconnected() {
        getSystemService(MediaSessionManager::class.java)
            ?.removeOnActiveSessionsChangedListener(onSessionsChanged)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit
}
