package dev.memoji.flashcards.feature.decks

import dev.memoji.flashcards.core.data.FakeCardRepository
import dev.memoji.flashcards.core.data.FakeDeckRepository
import dev.memoji.flashcards.core.model.Grade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DecksViewModelTest {

    private val cards = FakeCardRepository()
    private val repository = FakeDeckRepository(cards)

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun restoreDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the state starts out loading, before the first read comes back`() {
        assertEquals(DecksUiState.Loading, DecksViewModel(repository).uiState.value)
    }

    @Test
    fun `no Decks reads as empty rather than as an empty list`() = runTest {
        val viewModel = watchedViewModel()

        assertEquals(DecksUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `a created Deck lands in the state`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.createDeck("Big-O notation")

        assertEquals(listOf("Big-O notation"), viewModel.deckNames())
    }

    @Test
    fun `a blank name creates nothing`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.createDeck("   ")

        assertEquals(DecksUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `renaming a Deck shows the new name`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.createDeck("Big-O notaton")

        viewModel.renameDeck(viewModel.deckIds().single(), "Big-O notation")

        assertEquals(listOf("Big-O notation"), viewModel.deckNames())
    }

    @Test
    fun `a blank rename leaves the old name alone`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.createDeck("Git basics")

        viewModel.renameDeck(viewModel.deckIds().single(), " ")

        assertEquals(listOf("Git basics"), viewModel.deckNames())
    }

    @Test
    fun `a deleted Deck leaves the state`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.createDeck("Doomed")

        viewModel.deleteDeck(viewModel.deckIds().single())

        assertEquals(DecksUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `Up next points at the Deck studied most recently`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.createDeck("Big-O notation")
        viewModel.createDeck("Git basics")
        val bigO = viewModel.deckIds().last()
        cards.createCard(bigO, "O(1)", "Constant time.")
        cards.createCard(viewModel.deckIds().first(), "rebase", "Replays commits.")

        cards.recordGrade(cards.cardsInDeck(bigO).single().id, Grade.KNEW_IT)

        assertEquals("Big-O notation", viewModel.state().upNext?.deck?.name)
    }

    /** Nothing studied yet: the newest Deck is the one the user just made. */
    @Test
    fun `Up next falls back to the newest Deck with Cards`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.createDeck("Big-O notation")
        viewModel.createDeck("Git basics")
        cards.createCard(viewModel.deckIds().last(), "O(1)", "Constant time.")

        assertEquals("Big-O notation", viewModel.state().upNext?.deck?.name)
    }

    @Test
    fun `Decks with no Cards yet have nothing to be up next`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.createDeck("Big-O notation")

        assertEquals(null, viewModel.state().upNext)
    }

    /**
     * `uiState` only reads the repository while something is collecting it, so every test
     * needs a collector. `backgroundScope` cancels it when the test ends.
     */
    private fun TestScope.watchedViewModel() = DecksViewModel(repository).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private fun DecksViewModel.state() = uiState.value as DecksUiState.Decks
    private fun DecksViewModel.decks() = state().decks.map { it.deck }
    private fun DecksViewModel.deckNames() = decks().map { it.name }
    private fun DecksViewModel.deckIds() = decks().map { it.id }
}
