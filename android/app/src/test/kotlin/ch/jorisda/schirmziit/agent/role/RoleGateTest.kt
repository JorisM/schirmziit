package ch.jorisda.schirmziit.agent.role

import ch.jorisda.schirmziit.agent.parent.InMemoryParentSession
import ch.jorisda.schirmziit.agent.store.FakeAgentSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The role gate, and the credential separation it exists to enforce.
 *
 * The second half of this file is the part worth having: a phone that has been
 * both roles must never hold both credentials. A parent phone holding a device
 * token would keep uploading somebody's screen time from the parent's own
 * pocket; a child's phone holding a parent session is the exact thing
 * `ParentSetup` was written to avoid, arriving by a different door.
 */
class RoleGateTest {

    @Test
    fun `no role yet asks what this phone is`() {
        assertEquals(Destination.RoleChoice, destination(role = null, parentSignedIn = false))
        // Even a leftover session does not skip the question: the role is what
        // decides which credential is allowed to exist at all.
        assertEquals(Destination.RoleChoice, destination(role = null, parentSignedIn = true))
    }

    @Test
    fun `a parent phone signs in before it shows anything`() {
        assertEquals(
            Destination.ParentSignIn,
            destination(role = AppRole.Parent, parentSignedIn = false),
        )
        assertEquals(
            Destination.ParentChildren,
            destination(role = AppRole.Parent, parentSignedIn = true),
        )
    }

    @Test
    fun `a child phone goes to the agent regardless of any parent session`() {
        assertEquals(Destination.ChildAgent, destination(role = AppRole.Child, parentSignedIn = false))
        assertEquals(Destination.ChildAgent, destination(role = AppRole.Child, parentSignedIn = true))
    }

    @Test
    fun `becoming a parent phone drops any device token this phone held`() {
        val agent = FakeAgentSettings(baseUrl = "https://api.example.ch", deviceToken = "tok")
        val parent = InMemoryParentSession(cookie = null)

        adoptRole(AppRole.Parent, agent, parent)

        assertNull("a parent phone must not keep reporting", agent.deviceToken)
        assertNull(agent.baseUrl)
    }

    @Test
    fun `becoming a child phone drops any parent session this phone held`() {
        val agent = FakeAgentSettings()
        val parent = InMemoryParentSession(cookie = "schirmziit_session=abc")

        adoptRole(AppRole.Child, agent, parent)

        assertNull("a child's phone must not hold a parent session", parent.cookie)
    }

    @Test
    fun `leaving a role clears both sides, whichever one was in use`() {
        val agent = FakeAgentSettings(baseUrl = "https://api.example.ch", deviceToken = "tok")
        val parent = InMemoryParentSession(cookie = "schirmziit_session=abc")
        val store = InMemoryRoleStore(AppRole.Parent)

        forgetRole(store, agent, parent)

        assertNull(store.load())
        assertNull(agent.deviceToken)
        assertNull(parent.cookie)
    }

    // ─── upgrading a phone that was already reporting ─────────────────────

    @Test
    fun `a phone that is already enrolled is a child phone, and is not asked`() {
        // The upgrade path. Every phone installed before the role question
        // existed has a device token and no stored role, so asking would put a
        // working child phone one tap away from `adoptRole(Parent)` — which
        // calls `unpair()` and destroys that token. The child then stops
        // reporting silently and has to be enrolled again.
        val agent = FakeAgentSettings(baseUrl = "https://api.example.ch", deviceToken = "tok")
        val store = InMemoryRoleStore(null)

        assertEquals(AppRole.Child, resolveRole(store, agent))
    }

    @Test
    fun `resolving the role writes it down, so the question is asked once at most`() {
        val agent = FakeAgentSettings(baseUrl = "https://api.example.ch", deviceToken = "tok")
        val store = InMemoryRoleStore(null)

        resolveRole(store, agent)

        assertEquals(
            "an inferred role has to persist, or every launch re-infers it",
            AppRole.Child,
            store.load(),
        )
    }

    @Test
    fun `a fresh install is still asked what it is`() {
        // Nothing to infer from: no token, no role. This is the only case the
        // role question is for.
        assertNull(resolveRole(InMemoryRoleStore(null), FakeAgentSettings()))
    }

    @Test
    fun `a stored role always wins over what is inferred`() {
        // A parent phone holds no device token, so inference would say "child"
        // for one — but only in the absence of an answer. An explicit choice is
        // never second-guessed.
        assertEquals(
            AppRole.Parent,
            resolveRole(InMemoryRoleStore(AppRole.Parent), FakeAgentSettings()),
        )
        // And a half-migrated phone that somehow holds both does not flip back.
        assertEquals(
            AppRole.Parent,
            resolveRole(
                InMemoryRoleStore(AppRole.Parent),
                FakeAgentSettings(baseUrl = "https://x", deviceToken = "tok"),
            ),
        )
    }
}
