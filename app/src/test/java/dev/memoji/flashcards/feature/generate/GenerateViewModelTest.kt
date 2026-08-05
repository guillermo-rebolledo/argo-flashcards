package dev.memoji.flashcards.feature.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import dev.memoji.flashcards.core.data.FakeCardRepository
import dev.memoji.flashcards.core.data.FakeDeckRepository
import dev.memoji.flashcards.core.generation.FakeCardGenerator
import dev.memoji.flashcards.core.generation.GeneratedCard
import dev.memoji.flashcards.core.generation.GenerationFailure
import dev.memoji.flashcards.core.generation.GenerationResult
import dev.memoji.flashcards.core.generation.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The whole Generation flow with no key and no network: the fake generator answers with a
 * canned Deck or with any of the typed failures, which is what it exists for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GenerateViewModelTest {

    private val generator = FakeCardGenerator()
    private val cardRepository = FakeCardRepository()
    private val deckRepository = FakeDeckRepository(cardRepository)

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun restoreDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the flow starts at an empty box`() {
        assertEquals(GenerateUiState.Entry(), viewModel().uiState.value)
    }

    @Test
    fun `the word count reflects what was pasted`() {
        val viewModel = viewModel()

        viewModel.setText("  Big-O   notation describes\nhow work grows.  ")

        assertEquals(6, viewModel.entry().wordCount)
    }

    @Test
    fun `an empty box counts no words and cannot generate`() {
        val viewModel = viewModel()

        viewModel.setText("   ")

        assertEquals(0, viewModel.entry().wordCount)
        assertFalse(viewModel.entry().canGenerate)
    }

    @Test
    fun `generating produces the proposed Cards and a suggested name`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation describes how work grows with input size.")

        viewModel.generate()

        val proposed = viewModel.proposed()
        assertEquals("Big-O notation", proposed.deckName)
        assertEquals(listOf("O(1)", "O(n)", "O(log n)"), proposed.cards.map { it.card.front })
        assertEquals(3, proposed.keptCount)
    }

    @Test
    fun `what was pasted is what gets generated from`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")

        viewModel.generate()

        assertEquals(Source.PastedText("Big-O notation."), generator.lastSource)
    }

    /** A wait with nothing on screen reads as the app having frozen. */
    @Test
    fun `the state says it is busy while a Generation is in flight`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")
        generator.holdOpen()

        viewModel.generate()
        assertEquals(GenerateUiState.Busy, viewModel.uiState.value)

        generator.finish()
        assertTrue(viewModel.uiState.value is GenerateUiState.Proposed)
    }

    /**
     * A rotation recreates the screen, not the ViewModel: the same state is still there and
     * the request was never restarted.
     */
    @Test
    fun `a Generation survives the screen being recreated`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")
        generator.holdOpen()
        viewModel.generate()

        // What a rotation does: the composable reads the state again from the same ViewModel.
        assertEquals(GenerateUiState.Busy, viewModel.uiState.value)
        generator.finish()

        assertTrue(viewModel.uiState.value is GenerateUiState.Proposed)
        assertEquals(1, generator.generateCount)
    }

    @Test
    fun `leaving the flow cancels the request`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")
        generator.holdOpen()
        viewModel.generate()

        clear(viewModel)

        assertTrue(generator.wasCancelled)
    }

    @Test
    fun `unticking a Card drops it from the Kept count`() = runTest {
        val viewModel = generatedViewModel()

        viewModel.setKept(1, false)

        assertEquals(2, viewModel.proposed().keptCount)
        assertEquals(3, viewModel.proposed().cards.size)
    }

    @Test
    fun `unticking every Card leaves nothing to save`() = runTest {
        val viewModel = generatedViewModel()

        viewModel.proposed().cards.indices.forEach { viewModel.setKept(it, false) }

        assertFalse(viewModel.proposed().canSave)
    }

    @Test
    fun `the Deck name can be edited before saving`() = runTest {
        val viewModel = generatedViewModel()

        viewModel.setDeckName("Complexity")
        viewModel.save()

        assertEquals(listOf("Complexity"), deckRepository.deckNames())
    }

    /** A Deck has to be called something, and the button says so by staying off. */
    @Test
    fun `a blank Deck name cannot be saved`() = runTest {
        val viewModel = generatedViewModel()

        viewModel.setDeckName("  ")

        assertFalse(viewModel.proposed().canSave)
        viewModel.save()
        assertEquals(emptyList<String>(), deckRepository.deckNames())
    }

    @Test
    fun `saving creates a Deck holding exactly the Kept Cards`() = runTest {
        val viewModel = generatedViewModel()

        viewModel.setKept(1, false)
        viewModel.save()

        val deckId = deckRepository.deckIds().single()
        assertEquals(
            listOf("O(1)", "O(log n)"),
            cardRepository.cardsInDeck(deckId).map { it.front },
        )
    }

    /** The point of holding them in memory: an unkept Card is never written anywhere. */
    @Test
    fun `unkept Cards are never persisted`() = runTest {
        val viewModel = generatedViewModel()

        viewModel.setKept(1, false)
        viewModel.save()

        val fronts = cardRepository.cardsInDeck(deckRepository.deckIds().single()).map { it.front }
        assertFalse(fronts.contains("O(n)"))
    }

    @Test
    fun `saving opens the Deck it made`() = runTest {
        val viewModel = generatedViewModel()

        viewModel.save()

        assertEquals(deckRepository.deckIds().single(), viewModel.savedDeckId.value)
    }

    /** Two taps on Save must not make two Decks. */
    @Test
    fun `saving twice creates one Deck`() = runTest {
        val viewModel = generatedViewModel()

        viewModel.save()
        viewModel.save()

        assertEquals(1, deckRepository.deckIds().size)
    }

    @Test
    fun `writing them yourself creates an empty Deck and opens it`() = runTest {
        val viewModel = viewModel()

        viewModel.createEmptyDeck("Git basics")

        val deckId = deckRepository.deckIds().single()
        assertEquals("Git basics", deckRepository.deckNames().single())
        assertEquals(emptyList<String>(), cardRepository.cardsInDeck(deckId).map { it.front })
        assertEquals(deckId, viewModel.savedDeckId.value)
    }

    @Test
    fun `a blank name creates no Deck`() = runTest {
        val viewModel = viewModel()

        viewModel.createEmptyDeck("  ")

        assertEquals(emptyList<Long>(), deckRepository.deckIds())
        assertNull(viewModel.savedDeckId.value)
    }

    /**
     * Every failure lands back on the box with its own reason and the pasted text still there.
     * The screen turns the reason into a message and an action; this is the seven arriving.
     */
    @Test
    fun `each failure comes back with its own reason`() = runTest {
        GenerationFailure.entries.forEach { failure ->
            val viewModel = viewModel()
            viewModel.setText("Big-O notation.")
            generator.failWith(failure)

            viewModel.generate()

            assertEquals(failure, viewModel.entry().failure)
            assertEquals("Big-O notation.", viewModel.entry().text)
        }
    }

    /** The one failure that is not about this attempt: it routes to Settings. */
    @Test
    fun `generating with no key set offers Settings`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")
        generator.failWith(GenerationFailure.NO_KEY_SET)

        viewModel.generate()

        assertEquals(FailureAction.OPEN_SETTINGS, viewModel.entry().failureCopy?.action)
    }

    @Test
    fun `a rejected key is worth another go at Settings, not at the text`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")
        generator.failWith(GenerationFailure.INVALID_KEY)

        viewModel.generate()

        assertEquals(FailureAction.OPEN_SETTINGS, viewModel.entry().failureCopy?.action)
    }

    @Test
    fun `a failure worth retrying offers to try again`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")
        generator.failWith(GenerationFailure.OFFLINE)

        viewModel.generate()

        assertEquals(FailureAction.TRY_AGAIN, viewModel.entry().failureCopy?.action)
    }

    /** Nothing to retry with: the material itself is what has to change. */
    @Test
    fun `nothing usable offers no retry`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")
        generator.failWith(GenerationFailure.NO_CARDS)

        viewModel.generate()

        assertEquals(FailureAction.NONE, viewModel.entry().failureCopy?.action)
    }

    @Test
    fun `changing the text clears the last failure`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")
        generator.failWith(GenerationFailure.NO_CARDS)
        viewModel.generate()

        viewModel.setText("Big-O notation describes how work grows.")

        assertNull(viewModel.entry().failure)
    }

    /** Backing out of a Generation must not mean pasting it all again. */
    @Test
    fun `going back to the box keeps what was pasted`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")
        viewModel.generate()

        viewModel.backToEntry(viewModel.proposed().sourceText)

        assertEquals("Big-O notation.", viewModel.entry().text)
    }

    @Test
    fun `a Generation with no Cards in it is a failure, not an empty list`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")
        generator.result = GenerationResult.Failed(GenerationFailure.NO_CARDS)

        viewModel.generate()

        assertEquals(GenerationFailure.NO_CARDS, viewModel.entry().failure)
    }

    @Test
    fun `a single generated Card is a Deck of one`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation.")
        generator.result = GenerationResult.Generated(
            deckName = "Big-O notation",
            cards = listOf(GeneratedCard("O(1)", "Constant time.")),
        )

        viewModel.generate()
        viewModel.save()

        assertEquals(
            listOf("O(1)"),
            cardRepository.cardsInDeck(deckRepository.deckIds().single()).map { it.front },
        )
    }

    private fun viewModel() = GenerateViewModel(generator, deckRepository, cardRepository)

    private suspend fun generatedViewModel() = viewModel().also {
        it.setText("Big-O notation describes how work grows with input size.")
        it.generate()
    }

    private fun GenerateViewModel.entry() = uiState.value as GenerateUiState.Entry
    private fun GenerateViewModel.proposed() = uiState.value as GenerateUiState.Proposed

    /**
     * What leaving the flow does: the ViewModel is cleared and its scope cancelled. There is
     * no public way to clear one, so it goes through the store that owns it — the same route
     * navigation takes.
     */
    private fun clear(viewModel: ViewModel) {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
        }
        ViewModelProvider(store, factory)[viewModel::class.java]
        store.clear()
    }

    private suspend fun FakeDeckRepository.deckIds() =
        observeDeckSummaries().first().map { it.deck.id }

    private suspend fun FakeDeckRepository.deckNames() =
        observeDeckSummaries().first().map { it.deck.name }
}
