package ch.jorisda.nestling.agent.ui

import android.content.Context
import ch.jorisda.nestling.agent.R

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
}
