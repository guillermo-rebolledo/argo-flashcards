package dev.memoji.flashcards.feature.session

import androidx.lifecycle.SavedStateHandle
import dev.memoji.flashcards.core.data.FakeCardRepository
import dev.memoji.flashcards.core.data.FakeDeckRepository
import dev.memoji.flashcards.core.data.FakeSettingsRepository
import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Grade
import dev.memoji.flashcards.core.model.SessionLength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val cardRepository = FakeCardRepository()
    private val deckRepository = FakeDeckRepository(cardRepository)
    private val settingsRepository = FakeSettingsRepository()
    private var deckId = 0L

    @Before
    fun useTestDispatcher() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deckId = deckRepository.createDeck("Big-O notation")
    }

    @After
    fun restoreDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a Session runs Session-length Cards from the Deck, one at a time`() = runTest {
        addCards(8)

        val viewModel = viewModel()

        val reviewing = viewModel.reviewing()
        assertEquals(SessionLength.DEFAULT.cards, reviewing.total)
        assertEquals(1, reviewing.position)
    }

    @Test
    fun `the chosen Session length is what the Session runs`() = runTest {
        addCards(8)
        settingsRepository.setSessionLength(SessionLength.SHORT)

        assertEquals(3, viewModel().reviewing().total)
    }

    @Test
    fun `a Deck smaller than the Session length runs a shorter Session`() = runTest {
        addCards(2)

        assertEquals(2, viewModel().reviewing().total)
    }

    @Test
    fun `only the Front is showing until the Card is tapped`() = runTest {
        addCards(3)
        val viewModel = viewModel()

        assertFalse(viewModel.reviewing().revealed)

        viewModel.toggleReveal()

        assertTrue(viewModel.reviewing().revealed)
    }

    @Test
    fun `a second tap puts the Card face down again`() = runTest {
        addCards(3)
        val viewModel = viewModel()
        viewModel.toggleReveal()

        viewModel.toggleReveal()

        assertFalse(viewModel.reviewing().revealed)
    }

    @Test
    fun `the next Card starts face down again`() = runTest {
        addCards(3)
        val viewModel = viewModel()
        viewModel.toggleReveal()

        viewModel.grade(Grade.KNEW_IT)

        val reviewing = viewModel.reviewing()
        assertEquals(2, reviewing.position)
        assertFalse(reviewing.revealed)
    }

    @Test
    fun `position and progress run through the Session`() = runTest {
        addCards(3)
        val viewModel = viewModel()

        assertEquals(0f, viewModel.reviewing().progress, 0f)

        viewModel.grade(Grade.KNEW_IT)

        assertEquals(2, viewModel.reviewing().position)
        assertEquals(1f / 3, viewModel.reviewing().progress, 0f)
    }

    @Test
    fun `no Card comes up twice in one Session`() = runTest {
        addCards(3)
        val viewModel = viewModel()

        val fronts = buildList {
            repeat(3) {
                add(viewModel.reviewing().card.front)
                viewModel.grade(Grade.KNEW_IT)
            }
        }

        assertEquals(fronts.distinct(), fronts)
    }

    @Test
    fun `Knew it lifts the Card's Mastery streak and Again drops it`() = runTest {
        addCards(2)
        val viewModel = viewModel()
        val first = viewModel.reviewing().card.id
        val second = viewModel.gradeAndReturnNextId(Grade.KNEW_IT)

        viewModel.grade(Grade.AGAIN)

        assertEquals(1, cardRepository.card(first).masteryStreak)
        assertEquals(0, cardRepository.card(second).masteryStreak)
    }

    @Test
    fun `the last Grade ends the Session and scores the first pass`() = runTest {
        addCards(3)
        val viewModel = viewModel()

        viewModel.grade(Grade.KNEW_IT)
        viewModel.grade(Grade.AGAIN)
        viewModel.grade(Grade.KNEW_IT)

        val finished = viewModel.finished()
        assertEquals(2, finished.knewIt)
        assertEquals(3, finished.total)
    }

    @Test
    fun `the results list every Miss and nothing else`() = runTest {
        addCards(3)
        val viewModel = viewModel()
        val missed = viewModel.reviewing().card.front
        viewModel.grade(Grade.AGAIN)
        viewModel.grade(Grade.KNEW_IT)
        viewModel.grade(Grade.KNEW_IT)

        assertEquals(listOf(missed), viewModel.finished().misses.map { it.front })
    }

    @Test
    fun `a Session with nothing Missed has an empty Miss list`() = runTest {
        addCards(2)
        val viewModel = viewModel()

        repeat(2) { viewModel.grade(Grade.KNEW_IT) }

        assertEquals(emptyList<Card>(), viewModel.finished().misses)
    }

    @Test
    fun `reviewing the Misses runs a Session of exactly those Cards`() = runTest {
        addCards(4)
        val viewModel = viewModel()
        val missed = buildList {
            repeat(4) { index ->
                if (index % 2 == 0) add(viewModel.reviewing().card.id)
                viewModel.grade(if (index % 2 == 0) Grade.AGAIN else Grade.KNEW_IT)
            }
        }

        viewModel.reviewMisses()

        val reviewing = viewModel.reviewing()
        assertEquals(missed.size, reviewing.total)
        assertEquals(missed.first(), reviewing.card.id)
        assertFalse(reviewing.revealed)
    }

    @Test
    fun `the Misses are scored on their own, not carried over`() = runTest {
        addCards(3)
        val viewModel = viewModel()
        viewModel.grade(Grade.AGAIN)
        viewModel.grade(Grade.KNEW_IT)
        viewModel.grade(Grade.KNEW_IT)

        viewModel.reviewMisses()
        viewModel.grade(Grade.KNEW_IT)

        val finished = viewModel.finished()
        assertEquals(1, finished.knewIt)
        assertEquals(1, finished.total)
        assertEquals(emptyList<Card>(), finished.misses)
    }

    /** Stopping is always available and never costs anything: nothing is written on the way out. */
    @Test
    fun `ending a Session early leaves the Cards it never reached alone`() = runTest {
        addCards(5)
        val viewModel = viewModel()
        val graded = viewModel.reviewing().card.id

        viewModel.grade(Grade.KNEW_IT)

        assertEquals(1, cardRepository.card(graded).masteryStreak)
        val untouched = cardRepository.cardsInDeck(deckId).filterNot { it.id == graded }
        assertEquals(listOf(0), untouched.map { it.masteryStreak }.distinct())
        assertEquals(listOf(null), untouched.map { it.lastSeenAt }.distinct())
    }

    @Test
    fun `a Deck with no Cards has no Session to run`() = runTest {
        assertEquals(SessionUiState.Empty, viewModel().uiState.value)
    }

    /** Deleting the Deck took its Cards with it, so there is nothing left to draw. */
    @Test
    fun `a Deck that is already gone has no Session to run`() = runTest {
        addCards(3)
        deckRepository.deleteDeck(deckId)

        assertEquals(SessionUiState.Empty, viewModel().uiState.value)
    }

    private suspend fun addCards(count: Int) {
        repeat(count) { cardRepository.createCard(deckId, "Front $it", "Back $it") }
    }

    /**
     * A Grade is written on a scope the app owns, not on the ViewModel's — the user can leave
     * the moment they have graded. The test hands it a scope of its own for the same reason,
     * and the test's own scope cancels it at the end.
     */
    private fun TestScope.viewModel() = SessionViewModel(
        savedStateHandle = SavedStateHandle(mapOf(SessionRoute.DECK_ID_ARG to deckId)),
        cardRepository = cardRepository,
        settingsRepository = settingsRepository,
        applicationScope = CoroutineScope(
            backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
        ),
    )

    private fun SessionViewModel.reviewing() = uiState.value as SessionUiState.Reviewing
    private fun SessionViewModel.finished() = uiState.value as SessionUiState.Finished

    private fun SessionViewModel.gradeAndReturnNextId(grade: Grade): Long {
        this.grade(grade)
        return reviewing().card.id
    }

    private suspend fun FakeCardRepository.card(id: Long) =
        observeCards(deckId).first().single { it.id == id }
}
