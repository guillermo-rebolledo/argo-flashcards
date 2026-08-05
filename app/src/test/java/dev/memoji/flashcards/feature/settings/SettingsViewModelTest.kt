package dev.memoji.flashcards.feature.settings

import dev.memoji.flashcards.core.data.FakeSettingsRepository
import dev.memoji.flashcards.core.model.SessionLength
import dev.memoji.flashcards.core.model.ThemePreference
import dev.memoji.flashcards.core.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
class SettingsViewModelTest {

    private val repository = FakeSettingsRepository()

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun restoreDispatcher() {
        Dispatchers.resetMain()
    }

    /** No spinner and no blank rows: an unset setting already has an answer. */
    @Test
    fun `the state starts at the defaults rather than at nothing`() {
        assertEquals(UserSettings.DEFAULT, SettingsViewModel(repository).uiState.value)
    }

    @Test
    fun `choosing a Session length stores it`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.setSessionLength(SessionLength.LONG)

        assertEquals(SessionLength.LONG, repository.observeSettings().first().sessionLength)
        assertEquals(SessionLength.LONG, viewModel.uiState.value.sessionLength)
    }

    @Test
    fun `turning dark theme on and off is an override either way`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.setDarkTheme(true)
        assertEquals(ThemePreference.DARK, viewModel.uiState.value.theme)

        viewModel.setDarkTheme(false)
        assertEquals(ThemePreference.LIGHT, viewModel.uiState.value.theme)
    }

    @Test
    fun `the theme follows the system until the user chooses`() = runTest {
        val viewModel = watchedViewModel()

        assertEquals(ThemePreference.FOLLOW_SYSTEM, viewModel.uiState.value.theme)
    }

    @Test
    fun `the reduced-motion toggle stores both positions`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.setReducedMotion(true)
        assertTrue(viewModel.uiState.value.reducedMotion)

        viewModel.setReducedMotion(false)
        assertFalse(viewModel.uiState.value.reducedMotion)
    }

    @Test
    fun `the hide-day-streak toggle stores both positions`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.setHideDayStreak(true)
        assertTrue(viewModel.uiState.value.hideDayStreak)

        viewModel.setHideDayStreak(false)
        assertFalse(viewModel.uiState.value.hideDayStreak)
    }

    /**
     * The screen reads the same flow every other screen reads, so a preference changed
     * anywhere — here or by a later restore — arrives without the screen being rebuilt.
     */
    @Test
    fun `a change made elsewhere shows up without the screen asking again`() = runTest {
        val viewModel = watchedViewModel()

        repository.setSessionLength(SessionLength.SHORT)

        assertEquals(SessionLength.SHORT, viewModel.uiState.value.sessionLength)
    }

    /**
     * `uiState` only reads the repository while something is collecting it, so every test
     * needs a collector. `backgroundScope` cancels it when the test ends.
     */
    private fun TestScope.watchedViewModel() =
        SettingsViewModel(repository).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect { }
            }
        }
}
