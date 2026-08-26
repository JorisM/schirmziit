package ch.jorisda.schirmziit.agent.playback

/** In-memory playback reader for JVM tests; the real one needs MediaSessionManager. */
class FakePlaybackReader(
    private val granted: Boolean = false,
    private val active: List<PlaybackState> = emptyList(),
) : PlaybackReader {
    override fun active(): List<PlaybackState> = active

    override fun hasPermission(): Boolean = granted
}
