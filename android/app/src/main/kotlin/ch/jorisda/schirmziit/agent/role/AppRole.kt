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
