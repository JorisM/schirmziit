package ch.jorisda.schirmziit.agent.usage

import android.app.usage.UsageEvents
import ch.jorisda.schirmziit.agent.core.EventKind
import ch.jorisda.schirmziit.agent.core.RawEvent

object EventMapper {
    // API 29 renamed these; a device may emit either family, so handle both or
    // lose all usage on older Android.
    private const val LEGACY_MOVE_TO_FOREGROUND = 1
    private const val LEGACY_MOVE_TO_BACKGROUND = 2

    fun map(type: Int, packageName: String?, atMillis: Long): RawEvent? = when (type) {
        UsageEvents.Event.ACTIVITY_RESUMED, LEGACY_MOVE_TO_FOREGROUND ->
            packageName?.let { RawEvent(atMillis, EventKind.Resumed(it)) }

        UsageEvents.Event.ACTIVITY_PAUSED, LEGACY_MOVE_TO_BACKGROUND ->
            packageName?.let { RawEvent(atMillis, EventKind.Paused(it)) }

        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> RawEvent(atMillis, EventKind.ScreenOff)
        UsageEvents.Event.KEYGUARD_HIDDEN -> RawEvent(atMillis, EventKind.Unlock)
        else -> null
    }
}
