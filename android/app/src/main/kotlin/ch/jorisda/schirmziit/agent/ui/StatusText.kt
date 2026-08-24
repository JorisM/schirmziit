package ch.jorisda.schirmziit.agent.ui

import android.content.Context
import ch.jorisda.schirmziit.agent.R

object StatusText {
    /** Kept as a pure function of the two timestamps so it stays unit-testable. */
    fun bucket(nowMillis: Long, lastSyncMillis: Long): Bucket {
        if (lastSyncMillis <= 0L) return Bucket.Never
        val minutes = (nowMillis - lastSyncMillis) / 60_000
        return when {
            minutes < 0 -> Bucket.Never
            minutes < 60 -> Bucket.Minutes(minutes.toInt())
            minutes < 24 * 60 -> Bucket.Hours((minutes / 60).toInt())
            else -> Bucket.OverADay
        }
    }

    sealed interface Bucket {
        data object Never : Bucket
        data class Minutes(val value: Int) : Bucket
        data class Hours(val value: Int) : Bucket
        data object OverADay : Bucket
    }

    fun lastSync(context: Context, nowMillis: Long, lastSyncMillis: Long): String =
        when (val bucket = bucket(nowMillis, lastSyncMillis)) {
            Bucket.Never -> context.getString(R.string.status_never)
            is Bucket.Minutes -> context.getString(R.string.status_minutes_ago, bucket.value)
            is Bucket.Hours -> context.getString(R.string.status_hours_ago, bucket.value)
            Bucket.OverADay -> context.getString(R.string.status_over_a_day)
        }

    /**
     * "2 h 14 min" / "18 min" — never a decimal hour. Matches the dashboard
     * (`web/src/i18n/index.tsx`) and the iOS agent (`Formatting.duration`).
     *
     * "h"/"min" are hardcoded rather than resource strings: the web and iOS
     * copies confirm those abbreviations are already identical across all four
     * locales, and a pure function here (no Context) is what let the equivalent
     * `bucket` function above stay unit-testable without Robolectric.
     */
    fun duration(millis: Long): String {
        val minutes = Math.round(millis / 60_000.0)
        if (minutes < 60) return "$minutes min"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0L) "$hours h" else "$hours h $rest min"
    }
}
