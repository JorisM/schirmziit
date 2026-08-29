package ch.jorisda.schirmziit.agent.playback

/** In-memory playback reader for JVM tests; the real one needs MediaSessionManager. */
class FakePlaybackReader(
    private val granted: Boolean = false,
    private var active: List<PlaybackState> = emptyList(),
) : PlaybackReader {
    private var onChange: (() -> Unit)? = null

    val watching: Boolean get() = onChange != null

    override fun active(): List<PlaybackState> = active

    override fun hasPermission(): Boolean = granted

    override fun watch(onChange: () -> Unit) {
        this.onChange = onChange
    }

    override fun unwatch() {
        onChange = null
    }

    /** What the media stack does: the sessions change, and the watcher is told. */
    fun emit(sessions: List<PlaybackState>) {
        active = sessions
        onChange?.invoke()
    }
}
