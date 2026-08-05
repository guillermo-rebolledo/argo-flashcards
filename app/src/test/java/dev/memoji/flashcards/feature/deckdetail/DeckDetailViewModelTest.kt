package dev.memoji.flashcards.feature.deckdetail

import androidx.lifecycle.SavedStateHandle
import dev.memoji.flashcards.core.data.FakeCardRepository
import dev.memoji.flashcards.core.data.FakeDeckRepository
import dev.memoji.flashcards.core.model.Card
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
class DeckDetailViewModelTest {

    private val deckRepository = FakeDeckRepository()
    private val cardRepository = FakeCardRepository()
    private var deckId = 0L

    @Before
    fun useTestDispatcher() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deckRepository.onDeckDeleted = cardRepository::cascadeDeckDelete
        deckId = deckRepository.createDeck("Big-O notation")
    }

    @After
    fun restoreDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the state starts out loading, before the first read comes back`() {
        assertEquals(DeckDetailUiState.Loading, viewModel().uiState.value)
    }

    @Test
    fun `an added Card lands in the list`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.addCard("O(1)", "Constant time.")

        assertEquals(listOf("O(1)"), viewModel.fronts())
    }

    @Test
    fun `a Card with a blank side is not added`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.addCard(" ", "Constant time.")
        viewModel.addCard("O(1)", "  ")

        assertEquals(emptyList<String>(), viewModel.fronts())
    }

    @Test
    fun `an empty Deck reads as empty rather than as a filter that matched nothing`() = runTest {
        val viewModel = watchedViewModel()

        assertEquals(true, viewModel.ready().isDeckEmpty)
    }

    @Test
    fun `editing a Card shows what it now says`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.addCard("O(1)", "Constant tme.")

        viewModel.editCard(viewModel.ids().single(), "O(1)", "Constant time.")

        assertEquals(listOf("Constant time."), viewModel.ready().cards.map { it.back })
    }

    /**
     * The acceptance criterion that has no visible symptom yet: there are no Sessions to build
     * a streak with, so nothing but a test would notice an edit quietly resetting one.
     */
    @Test
    fun `editing a Card leaves its Mastery streak untouched`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.addCard("O(1)", "Constant tme.")
        val id = viewModel.ids().single()
        cardRepository.setMasteryStreak(id, 4)

        viewModel.editCard(id, "O(1)", "Constant time.")

        assertEquals(4, viewModel.ready().cards.single().masteryStreak)
    }

    @Test
    fun `a blank edit leaves the Card as it was`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.addCard("O(1)", "Constant time.")

        viewModel.editCard(viewModel.ids().single(), "O(1)", " ")

        assertEquals(listOf("Constant time."), viewModel.ready().cards.map { it.back })
    }

    @Test
    fun `a deleted Card leaves the list`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.addCard("Doomed", "b")

        viewModel.deleteCard(viewModel.ids().single())

        assertEquals(emptyList<String>(), viewModel.fronts())
    }

    @Test
    fun `the filter starts on All and lists everything`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.addCard("O(1)", "Constant time.")
        viewModel.addCard("O(n)", "Linear time.")
        cardRepository.setMasteryStreak(viewModel.ids().first(), Card.MASTERY_THRESHOLD)

        assertEquals(CardFilter.ALL, viewModel.ready().filter)
        assertEquals(2, viewModel.ready().cards.size)
    }

    @Test
    fun `Learning lists the Cards below the threshold and Mastered the rest`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.addCard("Learning one", "b")
        viewModel.addCard("Mastered one", "b")
        val masteredId = viewModel.ready().cards.first { it.front == "Mastered one" }.id
        cardRepository.setMasteryStreak(masteredId, Card.MASTERY_THRESHOLD)

        viewModel.setFilter(CardFilter.LEARNING)
        assertEquals(listOf("Learning one"), viewModel.fronts())

        viewModel.setFilter(CardFilter.MASTERED)
        assertEquals(listOf("Mastered one"), viewModel.fronts())
    }

    /** A filter that matches nothing is not an empty Deck, and must not read as one. */
    @Test
    fun `a filter matching nothing still knows the Deck has Cards`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.addCard("O(1)", "Constant time.")

        viewModel.setFilter(CardFilter.MASTERED)

        assertEquals(emptyList<String>(), viewModel.fronts())
        assertEquals(false, viewModel.ready().isDeckEmpty)
    }

    @Test
    fun `the mastery summary counts the whole Deck, not what the filter shows`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.addCard("O(1)", "Constant time.")
        viewModel.addCard("O(n)", "Linear time.")
        cardRepository.setMasteryStreak(viewModel.ids().first(), Card.MASTERY_THRESHOLD)

        viewModel.setFilter(CardFilter.MASTERED)

        assertEquals(2, viewModel.ready().cardCount)
        assertEquals(1, viewModel.ready().masteredCount)
    }

    @Test
    fun `renaming the Deck shows the new name`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.renameDeck("Big-O")

        assertEquals("Big-O", viewModel.ready().deck.name)
    }

    @Test
    fun `a blank rename leaves the old name alone`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.renameDeck("  ")

        assertEquals("Big-O notation", viewModel.ready().deck.name)
    }

    @Test
    fun `deleting the Deck leaves nothing to show`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.addCard("O(1)", "Constant time.")

        viewModel.deleteDeck()

        assertEquals(DeckDetailUiState.DeckGone, viewModel.uiState.value)
    }

    @Test
    fun `a Deck deleted from somewhere else also leaves nothing to show`() = runTest {
        val viewModel = watchedViewModel()

        deckRepository.deleteDeck(deckId)

        assertEquals(DeckDetailUiState.DeckGone, viewModel.uiState.value)
    }

    private fun viewModel() = DeckDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(DeckDetailRoute.DECK_ID_ARG to deckId)),
        deckRepository = deckRepository,
        cardRepository = cardRepository,
    )

    /**
     * `uiState` only reads the repositories while something is collecting it, so every test
     * needs a collector. `backgroundScope` cancels it when the test ends.
     */
    private fun TestScope.watchedViewModel() = viewModel().also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private fun DeckDetailViewModel.ready() = uiState.value as DeckDetailUiState.Ready
    private fun DeckDetailViewModel.fronts() = ready().cards.map { it.front }
    private fun DeckDetailViewModel.ids() = ready().cards.map { it.id }
}
