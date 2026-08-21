package ch.jorisda.nestling.agent.store

/** In-memory settings for JVM tests; the real one needs AndroidKeyStore. */
class FakeAgentSettings(
    override var baseUrl: String? = null,
    override var deviceToken: String? = null,
    override var lastSyncMillis: Long = 0L,
    override var lastError: String? = null,
) : AgentSettings {
    override fun unpair() {
        baseUrl = null
        deviceToken = null
    }
}
