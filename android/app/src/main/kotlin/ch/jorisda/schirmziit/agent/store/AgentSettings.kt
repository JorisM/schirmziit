package ch.jorisda.schirmziit.agent.store

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

    /**
     * Whether the family has said "not now" to background listening. One card,
     * dismissed once, gone for good: an optional grant that keeps asking is a
     * nag, and this one is genuinely optional.
     */
    var backgroundCardDismissed: Boolean

    val isPaired: Boolean get() = baseUrl != null && deviceToken != null

    fun unpair()
}
