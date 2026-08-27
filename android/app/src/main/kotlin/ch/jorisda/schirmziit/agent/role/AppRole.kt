package ch.jorisda.schirmziit.agent.role

import ch.jorisda.schirmziit.agent.parent.ParentSessionStore
import ch.jorisda.schirmziit.agent.store.AgentSettings

/**
 * What this phone is. Asked before any password, because what a phone is
 * decides everything else it does — the same first question iOS asks
 * (`ios/Sources/Role/AppRole.swift`).
 */
enum class AppRole { Parent, Child }

interface RoleStore {
    fun load(): AppRole?
    fun save(role: AppRole)
    fun clear()
}

/** For tests and previews. */
class InMemoryRoleStore(private var role: AppRole? = null) : RoleStore {
    override fun load(): AppRole? = role
    override fun save(role: AppRole) { this.role = role }
    override fun clear() { role = null }
}

/**
 * What this phone is, answering from what it already holds when nobody has been
 * asked yet.
 *
 * The migration this exists for: every phone enrolled before the role question
 * shipped has a device token and no stored role. Asking such a phone would put a
 * child that is already reporting one tap away from *My phone*, and
 * [adoptRole] would then do exactly what it is supposed to do — destroy the
 * device token — leaving the child silently not reporting and needing to be
 * enrolled again. An enrolled phone has already answered the question by being
 * enrolled.
 *
 * The answer is written down rather than re-inferred each launch, so the phone
 * behaves identically after it is later unpaired: without persisting it, an
 * unpaired child phone would fall back to the role question, which is right
 * exactly once and confusing thereafter.
 *
 * A stored role always wins. A parent phone holds no device token, so inference
 * alone would call it a child — but only ever in the absence of an answer.
 */
fun resolveRole(store: RoleStore, agent: AgentSettings): AppRole? {
    store.load()?.let { return it }
    if (!agent.isPaired) return null
    store.save(AppRole.Child)
    return AppRole.Child
}

/** Where the app goes once the role is known. */
sealed interface Destination {
    data object RoleChoice : Destination
    data object ParentSignIn : Destination
    data object ParentChildren : Destination

    /** The existing child agent: usage permission, then pairing, then status. */
    data object ChildAgent : Destination
}

fun destination(role: AppRole?, parentSignedIn: Boolean): Destination = when (role) {
    null -> Destination.RoleChoice
    AppRole.Child -> Destination.ChildAgent
    AppRole.Parent -> if (parentSignedIn) Destination.ParentChildren else Destination.ParentSignIn
}

/**
 * Takes a role *and* destroys the other role's credential.
 *
 * Not a nicety. Tenancy on the server is proved by the type system
 * (`crates/server`), and this is the on-device half of the same rule: a device
 * token is a write credential for one child's data and a parent session reads
 * every child in the family. One phone must never hold both. Reachable by an
 * ordinary route, too — a phone that was a child's and is handed on becomes a
 * parent phone, and nobody reinstalls the app first.
 *
 * Called on every role choice, not only on a change: a half-finished setup can
 * leave a credential behind under the role it belongs to, and re-picking the
 * same role is exactly what a parent does after one.
 */
fun adoptRole(role: AppRole, agent: AgentSettings, parent: ParentSessionStore) {
    when (role) {
        // `unpair()` and not just the token: the base URL is the child's server,
        // and the parent types their own on the sign-in screen.
        AppRole.Parent -> agent.unpair()
        AppRole.Child -> parent.clear()
    }
}

/** Signing out, or leaving child mode: neither credential survives it. */
fun forgetRole(store: RoleStore, agent: AgentSettings, parent: ParentSessionStore) {
    store.clear()
    agent.unpair()
    parent.clear()
}
