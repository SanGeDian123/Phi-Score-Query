package xyz.plcliangpicup.phigrosscore.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)
    private val alias = "phigros_score_session_v1"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun hasSession(): Boolean = preferences.contains(KEY_TOKEN)
    fun hasStoredSessionToken(): Boolean = preferences.contains(KEY_SESSION_TOKEN)

    fun save(accessToken: String, expiresAtEpochMs: Long, sessionToken: String? = null) {
        preferences.edit {
            putString(KEY_TOKEN, encrypt(accessToken))
            putLong(KEY_EXPIRES_AT, expiresAtEpochMs)
            sessionToken?.let { putString(KEY_SESSION_TOKEN, encrypt(it)) }
        }
    }

    fun read(): StoredSession? {
        val encoded = preferences.getString(KEY_TOKEN, null) ?: return null
        return runCatching {
            StoredSession(
                accessToken = decrypt(encoded),
                expiresAtEpochMs = preferences.getLong(KEY_EXPIRES_AT, 0),
            )
        }.getOrElse {
            clear()
            null
        }
    }

    fun readSessionToken(): String? {
        val encoded = preferences.getString(KEY_SESSION_TOKEN, null) ?: return null
        return runCatching { decrypt(encoded) }.getOrElse {
            preferences.edit { remove(KEY_SESSION_TOKEN) }
            null
        }
    }

    fun clear() {
        preferences.edit { clear() }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val value = Base64.decode(encoded, Base64.NO_WRAP)
        require(value.size > IV_SIZE)
        val iv = value.copyOfRange(0, IV_SIZE)
        val encrypted = value.copyOfRange(IV_SIZE, value.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    data class StoredSession(val accessToken: String, val expiresAtEpochMs: Long)

    private companion object {
        const val KEY_TOKEN = "access_token"
        const val KEY_SESSION_TOKEN = "session_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
