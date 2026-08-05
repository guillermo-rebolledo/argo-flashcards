package dev.memoji.flashcards.feature.progress

import dev.memoji.flashcards.core.data.FakeCardRepository
import dev.memoji.flashcards.core.data.FakeDeckRepository
import dev.memoji.flashcards.core.data.FakeSessionRepository
import dev.memoji.flashcards.core.data.FakeSettingsRepository
import dev.memoji.flashcards.core.testing.MutableClock
import java.time.Duration
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val clock = MutableClock()
    private val cardRepository = FakeCardRepository(clock)
    private val deckRepository = FakeDeckRepository(cardRepository)
    private val sessionRepository = FakeSessionRepository(clock)
    private val settingsRepository = FakeSettingsRepository()

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun restoreDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `with nothing studied the screen says so rather than showing zeroes`() = runTest {
        val viewModel = watchedViewModel()

        assertEquals(ProgressUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `a finished Session shows up on the screen without it being asked again`() = runTest {
        val deckId = deckRepository.createDeck("Big-O notation")
        val viewModel = watchedViewModel()

        clock.advance(Duration.ofMinutes(2))
        sessionRepository.recordSession(
            deckId = deckId,
            startedAt = MutableClock.START,
            cardsReviewed = 5,
            knewIt = 3,
        )

        val summary = viewModel.summary().summary
        assertEquals(1, summary.dayStreak)
        assertEquals(5, summary.cardsReviewed)
        assertEquals(2, summary.minutes)
        assertEquals(1, summary.decksTouched)
        assertEquals(listOf("Big-O notation"), summary.decks.map { it.name })
        assertNull(summary.skippedDay)
    }

    @Test
    fun `with the streak hidden the screen still shows what was studied`() = runTest {
        val deckId = deckRepository.createDeck("Big-O notation")
        settingsRepository.setHideStreak(true)
        val viewModel = watchedViewModel()

        clock.advanceOneMinute()
        sessionRepository.recordSession(deckId, MutableClock.START, cardsReviewed = 5, knewIt = 3)

        val state = viewModel.summary()
        assertTrue(state.hideStreak)
        assertEquals(5, state.summary.cardsReviewed)
    }

    /** The setting is read from the same flow as everywhere else, so it lands without a restart. */
    @Test
    fun `hiding the streak takes effect while the screen is open`() = runTest {
        val deckId = deckRepository.createDeck("Big-O notation")
        val viewModel = watchedViewModel()
        clock.advanceOneMinute()
        sessionRepository.recordSession(deckId, MutableClock.START, cardsReviewed = 5, knewIt = 3)

        settingsRepository.setHideStreak(true)

        assertTrue(viewModel.summary().hideStreak)
    }

    /**
     * `uiState` only reads while something is collecting it, so every test needs a collector.
     * `backgroundScope` cancels it when the test ends.
     */
    private fun TestScope.watchedViewModel() = ProgressViewModel(
        sessionRepository = sessionRepository,
        deckRepository = deckRepository,
        settingsRepository = settingsRepository,
        clock = clock,
    ).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private fun ProgressViewModel.summary() = uiState.value as ProgressUiState.Summary
}
