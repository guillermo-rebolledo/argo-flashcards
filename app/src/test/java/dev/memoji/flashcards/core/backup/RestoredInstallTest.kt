package dev.memoji.flashcards.core.backup

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import dev.memoji.flashcards.core.data.EncryptedApiKeyRepository
import dev.memoji.flashcards.core.data.FakeCardRepository
import dev.memoji.flashcards.core.data.FakeDeckRepository
import dev.memoji.flashcards.core.generation.AnthropicCardGenerator
import dev.memoji.flashcards.core.generation.GenerationFailure
import dev.memoji.flashcards.core.share.ShareInbox
import dev.memoji.flashcards.feature.generate.FailureAction
import dev.memoji.flashcards.feature.generate.GenerateStep
import dev.memoji.flashcards.feature.generate.GenerateViewModel
import javax.crypto.KeyGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a restore leaves behind, and what the app does with it. The Decks came back and the
 * credential did not — that is the whole point of the backup rules — so the first Generation
 * on a restored phone is the same one a fresh install gets: no key, a message, and Settings.
 *
 * The Decks are a fake because a restore is not what a repository does; the key store is the
 * real one, on a filesystem with nothing in it, because "the key did not come back" is exactly
 * the state being tested.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RestoredInstallTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()

    private val cardRepository = FakeCardRepository()
    private val deckRepository = FakeDeckRepository(cardRepository)

    /** The real store, reading a device that has just had a backup restored onto it. */
    private val apiKeyRepository = EncryptedApiKeyRepository(context) {
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    /** The rules kept it out of the backup; this is that seen from the app's side. */
    @Test
    fun `a restored install has no key`() = runTest {
        assertNull(apiKeyRepository.apiKey())
        assertFalse(apiKeyRepository.observeHasKey().first())
    }

    /**
     * Exactly what a fresh install gets — the same failure, the same way forward. A restore
     * that brought Decks back has changed nothing about how the missing key is handled.
     */
    @Test
    fun `generating on a restored install is the first-launch story, not an error`() = runTest {
        deckRepository.createDeck("Big-O notation")
        val viewModel = viewModel()
        viewModel.setText("Big-O notation describes how work grows with input size.")

        viewModel.generate()

        val entry = viewModel.generated()
        assertEquals(GenerationFailure.NO_KEY_SET, entry.failure)
        assertEquals(FailureAction.OPEN_SETTINGS, entry.failureCopy?.action)
    }

    /** Nothing is sent to Anthropic on a key the restore was right not to bring back. */
    @Test
    fun `a restored install sends nothing without a key`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation describes how work grows with input size.")

        viewModel.generate()

        viewModel.generated()
        assertEquals(0, server.requestCount)
    }

    /**
     * The real generator reads the key off the IO dispatcher, so the Generation has genuinely
     * finished only once the flow is back at the box with something to say about it.
     */
    private suspend fun GenerateViewModel.generated(): GenerateStep.Entry =
        uiState.map { it.step }
            .filterIsInstance<GenerateStep.Entry>()
            .first { it.failure != null }

    private fun viewModel() = GenerateViewModel(
        savedStateHandle = SavedStateHandle(),
        cardGenerator = AnthropicCardGenerator(
            apiKeyRepository,
            OkHttpClient(),
            server.url("/v1/messages").toString(),
        ),
        deckRepository = deckRepository,
        cardRepository = cardRepository,
        shareInbox = ShareInbox(),
    )
}
