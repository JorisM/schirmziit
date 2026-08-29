package ch.jorisda.schirmziit.agent.playback

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ch.jorisda.schirmziit.agent.core.EventKind
import ch.jorisda.schirmziit.agent.store.AgentDatabase
import ch.jorisda.schirmziit.agent.store.PlaybackEventRow
import ch.jorisda.schirmziit.agent.store.QueueDao
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Turns media-session snapshots into raw events. Keeps only the set of packages
 * currently playing, so the whole start/stop rule stays testable on the JVM.
 */
class PlaybackHandler(
    private val dao: QueueDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /**
     * NotificationListenerService delivers on the main looper, and Room throws
     * there rather than writing. Inserting inline killed the service on the
     * first thing that played, and the system then backed its restart off to
     * half an hour — a phone that had listened once recorded nothing after it.
     * One thread, so the events keep the order they were diffed in.
     */
    private val writer: Executor = Executors.newSingleThreadExecutor(),
) {
    private var lastPlaying: Set<String> = emptySet()

    @Synchronized
    fun onSnapshot(current: List<PlaybackState>) {
        val events = playbackEvents(lastPlaying, current, nowMillis())
        lastPlaying = current.filter { it.playing }.map { it.packageName }.toSet()
        if (events.isEmpty()) return
        // Timestamped here, on the thread that was told, and written later:
        // a queued insert must not date the stretch from when it got its turn.
        val rows = events.mapNotNull { event ->
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
        }
        writer.execute { dao.appendPlayback(rows) }
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

    private val watcher by lazy {
        PlaybackWatcher(
            MediaSessionPlaybackReader(this),
            PlaybackHandler(AgentDatabase.get(this).queue()),
        )
    }

    override fun onListenerConnected() = watcher.start()

    override fun onListenerDisconnected() = watcher.stop()

    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit
}
