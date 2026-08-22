package ch.jorisda.schirmziit.agent.store

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Base URL and device token. Encrypted at rest because the token is a write
 * credential for this child's data — a plain SharedPreferences file is readable
 * on a rooted or backed-up device.
 */
class AgentStore(context: Context) : AgentSettings {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "schirmziit-agent",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override var baseUrl: String?
        get() = prefs.getString(KEY_BASE_URL, null)
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    override var deviceToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    override var lastSyncMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    /** Surfaced on the status screen: a silent failure is the worst outcome. */
    override var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()

    override fun unpair() {
        prefs.edit().remove(KEY_BASE_URL).remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "device_token"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_LAST_ERROR = "last_error"
    }
}
