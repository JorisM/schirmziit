package ch.jorisda.schirmziit.agent.store

import android.content.Context
import ch.jorisda.schirmziit.agent.role.AppRole
import ch.jorisda.schirmziit.agent.role.RoleStore

/**
 * What this phone is, remembered across launches.
 *
 * Plain SharedPreferences, not encrypted: the role is not a secret and nothing
 * is authorised by it — the credentials it decides between live in
 * [AgentStore] and
 * [ch.jorisda.schirmziit.agent.parent.EncryptedParentSession], which are.
 * Encrypting this too would only add another way for `MasterKey` to fail on a
 * phone whose keystore is unhappy, and a role that fails to load drops the
 * parent back to the role question with a session they cannot see.
 */
class PrefsRoleStore(context: Context) : RoleStore {
    private val prefs = context.getSharedPreferences("schirmziit-role", Context.MODE_PRIVATE)

    override fun load(): AppRole? =
        prefs.getString(KEY_ROLE, null)?.let { stored ->
            AppRole.entries.firstOrNull { it.name == stored }
        }

    override fun save(role: AppRole) {
        prefs.edit().putString(KEY_ROLE, role.name).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_ROLE).apply()
    }

    private companion object {
        const val KEY_ROLE = "role"
    }
}
