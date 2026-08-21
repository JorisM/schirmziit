package ch.jorisda.nestling.agent.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import ch.jorisda.nestling.agent.core.RawEvent

/**
 * The one seam over Android's usage APIs. Everything above it is testable on the
 * JVM against FakeUsageSource; only this implementation needs a real device.
 */
interface UsageSource {
    fun events(fromMillis: Long, toMillis: Long): List<RawEvent>
    fun labels(packages: Set<String>): Map<String, String>
    fun hasPermission(): Boolean
}

class AndroidUsageSource(private val context: Context) : UsageSource {

    /**
     * Derived from queryEvents, not queryUsageStats: the latter returns coarse
     * pre-aggregated intervals that disagree with reality after reboots and
     * cannot be attributed to an hour.
     */
    override fun events(fromMillis: Long, toMillis: Long): List<RawEvent> {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val stream = manager.queryEvents(fromMillis, toMillis)
        val out = mutableListOf<RawEvent>()
        val event = UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            EventMapper.map(event.eventType, event.packageName, event.timeStamp)?.let(out::add)
        }
        return out
    }

    override fun labels(packages: Set<String>): Map<String, String> {
        val pm = context.packageManager
        return packages.mapNotNull { name ->
            runCatching {
                name to pm.getApplicationLabel(pm.getApplicationInfo(name, 0)).toString()
            }.getOrNull()
        }.toMap()
    }

    /**
     * PACKAGE_USAGE_STATS cannot be requested in-app; it is an AppOps grant the
     * user makes in Settings, so we check the op rather than the permission.
     */
    override fun hasPermission(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
