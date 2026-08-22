package ch.jorisda.schirmziit.agent.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.store.AgentSettings
import ch.jorisda.schirmziit.agent.ui.StatusText

/**
 * The visible half of "never covert". Low importance so it does not nag, but
 * ongoing so it cannot be swiped away and forgotten.
 */
object OngoingNotice {
    const val CHANNEL_ID = "schirmziit-monitoring"
    private const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun update(context: Context, settings: AgentSettings) {
        val detail = settings.lastError
            ?.let { context.getString(R.string.notification_problem, it) }
            ?: context.getString(
                R.string.notification_last_sent,
                StatusText.lastSync(context, System.currentTimeMillis(), settings.lastSyncMillis),
            )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(detail)
            .setOngoing(true)
            .setSilent(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
