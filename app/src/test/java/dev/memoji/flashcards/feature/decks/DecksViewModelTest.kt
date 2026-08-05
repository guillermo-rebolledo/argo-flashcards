package dev.memoji.flashcards.feature.decks

import dev.memoji.flashcards.core.data.DeckRepository
import dev.memoji.flashcards.core.model.Deck
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

    private val repository = FakeDeckRepository()

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

    /**
     * `uiState` only reads the repository while something is collecting it, so every test
     * needs a collector. `backgroundScope` cancels it when the test ends.
     */
    private fun TestScope.watchedViewModel() = DecksViewModel(repository).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private fun DecksViewModel.decks() = (uiState.value as DecksUiState.Decks).decks
    private fun DecksViewModel.deckNames() = decks().map { it.name }
    private fun DecksViewModel.deckIds() = decks().map { it.id }

    /** A stand-in written by hand — the project uses no mocking library. */
    private class FakeDeckRepository : DeckRepository {
        private val decks = MutableStateFlow(emptyList<Deck>())
        private var nextId = 1L

        override fun observeDecks(): Flow<List<Deck>> =
            decks.map { list -> list.sortedByDescending(Deck::createdAt) }

        override suspend fun createDeck(name: String): Long {
            val id = nextId++
            decks.value += Deck(id, name.trim(), Instant.ofEpochMilli(id))
            return id
        }

        override suspend fun renameDeck(id: Long, name: String) {
            decks.value = decks.value.map { if (it.id == id) it.copy(name = name.trim()) else it }
        }

        override suspend fun deleteDeck(id: Long) {
            decks.value = decks.value.filterNot { it.id == id }
        }
    }
}
