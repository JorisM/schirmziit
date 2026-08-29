package ch.jorisda.schirmziit.agent.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.core.CoreBridge
import ch.jorisda.schirmziit.agent.notify.OngoingNotice
import ch.jorisda.schirmziit.agent.playback.MediaSessionPlaybackReader
import ch.jorisda.schirmziit.agent.store.AgentDatabase
import ch.jorisda.schirmziit.agent.store.AgentStore
import ch.jorisda.schirmziit.agent.usage.AndroidUsageSource
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = AgentStore(applicationContext)
        val source = AndroidUsageSource(applicationContext)

        if (!source.hasPermission()) {
            // Nothing to retry until the user re-grants; surface it instead of
            // failing silently, which is indistinguishable from an unused phone.
            store.lastError = applicationContext.getString(R.string.permission_revoked)
            OngoingNotice.update(applicationContext, store)
            return Result.success()
        }

        val collector = Collector(
            bridge = CoreBridge(),
            source = source,
            dao = AgentDatabase.get(applicationContext).queue(),
            store = store,
            // Read, never listened to: the same grant PlaybackListener needs,
            // checked here so the hour can say whether background listening
            // could be observed at all.
            playback = MediaSessionPlaybackReader(applicationContext),
        )
        collector.collect()

        val baseUrl = store.baseUrl ?: return Result.success()
        val outcome = collector.sync(SchirmziitClient(baseUrl, OkHttpClient()))
        OngoingNotice.update(applicationContext, store)

        return if (outcome.error == null) Result.success() else Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "schirmziit-sync"
        // WorkManager's floor is 15 minutes; the spec's cadence is 30.
        private const val INTERVAL_MINUTES = 30L

        /**
         * Run one sync as soon as the network allows. Backs the status screen's
         * "Send now" button, and the schirmziit://sync debug link.
         */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .build(),
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
