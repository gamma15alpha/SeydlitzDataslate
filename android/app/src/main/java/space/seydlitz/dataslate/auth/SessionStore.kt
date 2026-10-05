package space.seydlitz.dataslate.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import space.seydlitz.dataslate.api.ApiJson
import space.seydlitz.dataslate.api.User
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedSession(val token: String, val user: User)

interface SessionStore {
    suspend fun load(): SavedSession?
    suspend fun save(session: SavedSession)
    suspend fun clear()
}

private val Context.sessionData by preferencesDataStore("session")
private val TOKEN = stringPreferencesKey("token")
private val USER = stringPreferencesKey("user")

// Токен — зашифрован ключом из Android Keystore; ключ не покидает устройство.
class KeystoreSessionStore(private val context: Context) : SessionStore {
    override suspend fun load(): SavedSession? {
        val prefs = context.sessionData.data.first()
        val token = prefs[TOKEN]?.let(::decrypt) ?: return null
        val user = prefs[USER]?.let { runCatching { ApiJson.decodeFromString<User>(it) }.getOrNull() } ?: return null
        return SavedSession(token, user)
    }

    override suspend fun save(session: SavedSession) {
        context.sessionData.edit {
            it[TOKEN] = encrypt(session.token)
            it[USER] = ApiJson.encodeToString(session.user)
        }
    }

    override suspend fun clear() {
        context.sessionData.edit { it.clear() }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    // null — если не расшифровать (например, данные восстановлены из бэкапа на другом устройстве).
    private fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
    }.getOrNull()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "session-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
