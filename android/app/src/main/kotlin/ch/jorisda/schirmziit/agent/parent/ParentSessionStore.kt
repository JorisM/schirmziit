package ch.jorisda.schirmziit.agent.parent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The parent's session cookie and the server it belongs to.
 *
 * A separate store from [ch.jorisda.schirmziit.agent.store.AgentStore], in its
 * own encrypted file, rather than two more fields on the child agent's
 * settings. The role gate clears one side or the other
 * (`role.adoptRole`), and that is far harder to get wrong when the two
 * credentials cannot share a key namespace in the first place.
 */
interface ParentSessionStore {
    var cookie: String?
    var baseUrl: String?
    fun clear()
}

class EncryptedParentSession(context: Context) : ParentSessionStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "schirmziit-parent",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override var cookie: String?
        get() = prefs.getString(KEY_COOKIE, null)
        set(value) = prefs.edit().putString(KEY_COOKIE, value).apply()

    override var baseUrl: String?
        get() = prefs.getString(KEY_BASE_URL, null)
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    override fun clear() {
        prefs.edit().remove(KEY_COOKIE).remove(KEY_BASE_URL).apply()
    }

    private companion object {
        const val KEY_COOKIE = "session_cookie"
        const val KEY_BASE_URL = "base_url"
    }
}

/** For tests, previews and the screenshot run. */
class InMemoryParentSession(
    override var cookie: String? = null,
    override var baseUrl: String? = null,
) : ParentSessionStore {
    override fun clear() {
        cookie = null
        baseUrl = null
    }
}
