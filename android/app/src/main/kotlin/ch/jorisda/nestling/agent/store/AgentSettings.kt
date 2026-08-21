package ch.jorisda.nestling.agent.store

/**
 * Pairing state and last-sync bookkeeping. An interface because the real
 * implementation needs AndroidKeyStore, which Robolectric has no sandbox for —
 * JVM tests substitute an in-memory one while production stays encrypted.
 */
interface AgentSettings {
    var baseUrl: String?
    var deviceToken: String?
    var lastSyncMillis: Long
    var lastError: String?

    val isPaired: Boolean get() = baseUrl != null && deviceToken != null

    fun unpair()
}
