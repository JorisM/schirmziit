package ch.jorisda.nestling.agent.ui

object StatusText {
    fun lastSync(nowMillis: Long, lastSyncMillis: Long): String {
        if (lastSyncMillis <= 0L) return "never"
        val minutes = (nowMillis - lastSyncMillis) / 60_000
        return when {
            minutes < 60 -> "$minutes minutes ago"
            minutes < 24 * 60 -> "${minutes / 60} hours ago"
            else -> "over a day ago"
        }
    }
}
