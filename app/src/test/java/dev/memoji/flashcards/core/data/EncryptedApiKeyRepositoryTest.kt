package dev.memoji.flashcards.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real AES-GCM and a real preferences file, because what is being tested is that the key
 * survives a restart and that the plaintext never reaches the disk — neither of which a fake
 * could tell us anything about.
 *
 * The one substitution is where the encryption key comes from: there is no `AndroidKeyStore`
 * off a device, so these run against an AES key held for the length of the test. It stands in
 * for the device's, which likewise hands back the same key to every instance of the app.
 */
@RunWith(RobolectricTestRunner::class)
class EncryptedApiKeyRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val secretKey: SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun repository() = EncryptedApiKeyRepository(context) { secretKey }

    @Test
    fun `a stored key reads back`() = runTest {
        val repository = repository()

        repository.setApiKey("sk-ant-secret")

        assertEquals("sk-ant-secret", repository.apiKey())
    }

    /** A new instance is what the next launch gets, and it must find the key already there. */
    @Test
    fun `a stored key survives a restart`() = runTest {
        repository().setApiKey("sk-ant-secret")

        val restarted = repository()

        assertEquals("sk-ant-secret", restarted.apiKey())
        assertTrue(restarted.observeHasKey().first())
    }

    @Test
    fun `there is no key before one is entered`() = runTest {
        val repository = repository()

        assertNull(repository.apiKey())
        assertFalse(repository.observeHasKey().first())
    }

    @Test
    fun `a cleared key is gone`() = runTest {
        val repository = repository()
        repository.setApiKey("sk-ant-secret")

        repository.clearApiKey()

        assertNull(repository.apiKey())
        assertFalse(repository.observeHasKey().first())
        assertFalse(repository().observeHasKey().first())
    }

    @Test
    fun `a key is trimmed on the way in`() = runTest {
        val repository = repository()

        repository.setApiKey("  sk-ant-secret\n")

        assertEquals("sk-ant-secret", repository.apiKey())
    }

    /** A blank field is the user clearing the box, not a key of no characters. */
    @Test
    fun `a blank key is not stored`() = runTest {
        val repository = repository()

        repository.setApiKey("   ")

        assertFalse(repository.observeHasKey().first())
    }

    /** The point of the whole class: what lands on disk must not be the key. */
    @Test
    fun `the stored value is not the key`() = runTest {
        repository().setApiKey("sk-ant-secret")

        val stored = context
            .getSharedPreferences("credentials", Context.MODE_PRIVATE)
            .getString("anthropic_api_key", null)

        assertTrue(stored != null && !stored.contains("sk-ant-secret"))
    }

    /** Ciphertext that will not decrypt is treated as no key at all, not as a crash. */
    @Test
    fun `a damaged stored value reads as no key`() = runTest {
        repository().setApiKey("sk-ant-secret")
        context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
            .edit()
            .putString("anthropic_api_key", "not-real-ciphertext")
            .commit()

        val repository = repository()

        assertNull(repository.apiKey())
        assertFalse(repository.observeHasKey().first())
    }
}
