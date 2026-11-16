package sg.edumate.app.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small secrets (DB passphrase, auth tokens) encrypted with a Keystore key before being written to
 * SharedPreferences. Excluded from backup by data_extraction_rules.xml.
 */
@Singleton
class SecretStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("edumate_secure", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("edumate_master_v1")

    fun put(key: String, value: ByteArray?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, Base64.encodeToString(cipher.encrypt(value), Base64.NO_WRAP))
        }.apply()
    }

    fun get(key: String): ByteArray? = prefs.getString(key, null)?.let { cipher.decrypt(Base64.decode(it, Base64.NO_WRAP)) }

    /** 256-bit random SQLCipher passphrase, created on first use. */
    fun databasePassphrase(): ByteArray = get(DB_KEY) ?: ByteArray(32).also { SecureRandom().nextBytes(it); put(DB_KEY, it) }

    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val DB_KEY = "db_passphrase"
        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
    }
}
