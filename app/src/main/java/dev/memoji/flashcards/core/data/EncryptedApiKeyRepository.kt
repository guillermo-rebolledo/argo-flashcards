package dev.memoji.flashcards.core.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The key, encrypted with a key that never leaves the device's hardware-backed keystore.
 *
 * What is written to disk is the ciphertext and its initialisation vector, in a file of their
 * own rather than in the settings store — the backup rules exclude this file by name, and a
 * credential sharing a file with preferences would be one rule away from being backed up with
 * them. Nothing here logs, and the plaintext is only ever returned to [apiKey].
 */
@Singleton
internal class EncryptedApiKeyRepository(
    private val context: Context,
    private val secretKeySource: SecretKeySource,
) : ApiKeyRepository {

    /**
     * What the app builds. The keystore is the only part of this class the JVM tests cannot
     * run — there is no `AndroidKeyStore` off a device — so it is the only part behind this.
     */
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, KeystoreSecretKeySource())

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(CREDENTIALS_FILE, Context.MODE_PRIVATE)
    }

    /**
     * Whether a key is stored, not the key. Seeded from what is on disk so the Settings screen
     * is right on the first frame after a restart.
     */
    private val hasKey = MutableStateFlow(false)

    init {
        hasKey.value = preferences.contains(STORED_KEY)
    }

    override fun observeHasKey(): Flow<Boolean> = hasKey.asStateFlow()

    override suspend fun apiKey(): String? = withContext(Dispatchers.IO) {
        val stored = preferences.getString(STORED_KEY, null) ?: return@withContext null
        // A key that cannot be decrypted is a key the user no longer has: the keystore entry
        // is gone, or the ciphertext is damaged. Clearing it turns an unexplainable rejection
        // from the API into "no key set", which routes them to Settings to enter it again.
        decrypt(stored) ?: run {
            clearApiKey()
            null
        }
    }

    override suspend fun setApiKey(key: String) = withContext(Dispatchers.IO) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return@withContext
        preferences.edit().putString(STORED_KEY, encrypt(trimmed)).commit()
        hasKey.value = true
    }

    override suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        preferences.edit().remove(STORED_KEY).commit()
        hasKey.value = false
    }

    /** IV and ciphertext travel together, so a rewrite cannot leave them out of step. */
    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySource.secretKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = try {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKeySource.secretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH_BYTES),
        )
        String(
            cipher.doFinal(bytes, IV_LENGTH_BYTES, bytes.size - IV_LENGTH_BYTES),
            Charsets.UTF_8,
        )
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        // Base64 that is not Base64, or too short to hold an IV.
        null
    }

    private companion object {
        const val CREDENTIALS_FILE = "credentials"
        const val STORED_KEY = "anthropic_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val IV_LENGTH_BYTES = 12
    }
}

/** Where the key that encrypts the key comes from. */
internal fun interface SecretKeySource {

    fun secretKey(): SecretKey
}

/**
 * The device's hardware-backed keystore. It generates the key on first use and never hands the
 * key material back — only a [SecretKey] handle that the keystore itself does the work with,
 * so the encryption key is not something this process could leak even if it wanted to.
 */
internal class KeystoreSecretKeySource : SecretKeySource {

    override fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "flashcards_api_key"
        const val KEY_SIZE_BITS = 256
    }
}
