package ch.jorisda.schirmziit.agent.pair

import ch.jorisda.schirmziit.agent.store.AgentSettings
import ch.jorisda.schirmziit.agent.sync.EnrollResult
import ch.jorisda.schirmziit.agent.sync.ParentSession
import ch.jorisda.schirmziit.agent.sync.SchirmziitClient
import ch.jorisda.schirmziit.agent.sync.SetupChild

/**
 * Setting this phone up from a parent's account instead of a pairing code.
 *
 * The sequence is the point: claim a device token, store it, and only then end
 * the parent session. A child's phone must never be left holding a parent
 * session — not after a success, and not after a failure either.
 */
class ParentSetup(private val client: SchirmziitClient, private val settings: AgentSettings) {

    sealed interface SignIn {
        data class Ready(val session: ParentSession, val children: List<SetupChild>) : SignIn
        data object WrongCredentials : SignIn
        data class Failed(val message: String) : SignIn
    }

    fun signIn(email: String, password: String): SignIn {
        val session = runCatching { client.signIn(email, password) }
            .getOrElse { return SignIn.Failed(it.message ?: "") }
            ?: return SignIn.WrongCredentials

        val children = runCatching { client.children(session) }
            .getOrElse {
                client.signOut(session)
                return SignIn.Failed(it.message ?: "")
            }
        return SignIn.Ready(session, children)
    }

    sealed interface Unpair {
        data object Done : Unpair
        data object WrongCredentials : Unpair
        data class Failed(val message: String) : Unpair
    }

    /**
     * Stops this phone reporting — behind the parent's password, deliberately.
     *
     * The child can see every number this app sends, but must not be able to
     * switch it off alone; that is the same guard as leaving child mode, and the
     * reason this takes a password rather than a confirm dialog.
     *
     * Only the *local* pairing is cleared. The device row keeps its token
     * server-side: the phone never learns its own device id, so revoking is the
     * parent's job from the dashboard. Nothing holds the token afterwards — it
     * is erased here — so the row is orphaned rather than live.
     */
    fun unpair(email: String, password: String): Unpair {
        val session = runCatching { client.signIn(email, password) }
            .getOrElse { return Unpair.Failed(it.message ?: "") }
            ?: return Unpair.WrongCredentials

        // Ended before the local wipe, and its failure deliberately ignored: a
        // phone that stopped reporting because a logout call blipped would be a
        // silent gap, which is the one outcome worse than a stale session.
        runCatching { client.signOut(session) }
        settings.unpair()
        return Unpair.Done
    }

    /**
     * Returns the enrolled device on success. The session is ended either way,
     * so a parent who walks away mid-setup leaves nothing behind.
     */
    fun claim(
        session: ParentSession,
        baseUrl: String,
        childId: String,
        model: String,
        label: String,
    ): Result<EnrollResult> {
        val claimed = runCatching {
            client.claimDevice(session, childId, "android", model, label)
        }
        claimed.onSuccess { enrolled ->
            settings.baseUrl = baseUrl
            settings.deviceToken = enrolled.token
        }
        client.signOut(session)
        return claimed
    }
}
