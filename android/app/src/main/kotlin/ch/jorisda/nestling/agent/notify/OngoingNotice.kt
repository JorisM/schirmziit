package ch.jorisda.nestling.agent.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import ch.jorisda.nestling.agent.store.AgentSettings
import ch.jorisda.nestling.agent.ui.StatusText

/**
 * The visible half of "never covert". Low importance so it does not nag, but
 * ongoing so it cannot be swiped away and forgotten.
 */
object OngoingNotice {
    const val CHANNEL_ID = "nestling-monitoring"
    private const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen-time reporting",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shows that this phone reports screen time to a parent." }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun update(context: Context, settings: AgentSettings) {
        val detail = settings.lastError
            ?.let { "Problem sending: $it" }
            ?: "Last sent ${StatusText.lastSync(System.currentTimeMillis(), settings.lastSyncMillis)}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Screen time is being reported")
            .setContentText(detail)
            .setOngoing(true)
            .setSilent(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
