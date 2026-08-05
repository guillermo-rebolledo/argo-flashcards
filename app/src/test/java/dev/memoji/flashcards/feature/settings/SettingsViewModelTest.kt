package dev.memoji.flashcards.feature.settings

import dev.memoji.flashcards.core.data.FakeApiKeyRepository
import dev.memoji.flashcards.core.data.FakeSettingsRepository
import dev.memoji.flashcards.core.model.SessionLength
import dev.memoji.flashcards.core.model.ThemePreference
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val repository = FakeSettingsRepository()
    private val apiKeyRepository = FakeApiKeyRepository()

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
        assertEquals(SettingsUiState(), SettingsViewModel(repository, apiKeyRepository).uiState.value)
    }

    @Test
    fun `choosing a Session length stores it`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.setSessionLength(SessionLength.LONG)

        assertEquals(SessionLength.LONG, repository.observeSettings().first().sessionLength)
        assertEquals(SessionLength.LONG, viewModel.uiState.value.settings.sessionLength)
    }

    @Test
    fun `turning dark theme on and off is an override either way`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.setDarkTheme(true)
        assertEquals(ThemePreference.DARK, viewModel.uiState.value.settings.theme)

        viewModel.setDarkTheme(false)
        assertEquals(ThemePreference.LIGHT, viewModel.uiState.value.settings.theme)
    }

    @Test
    fun `the theme follows the system until the user chooses`() = runTest {
        val viewModel = watchedViewModel()

        assertEquals(ThemePreference.FOLLOW_SYSTEM, viewModel.uiState.value.settings.theme)
    }

    @Test
    fun `the reduced-motion toggle stores both positions`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.setReducedMotion(true)
        assertTrue(viewModel.uiState.value.settings.reducedMotion)

        viewModel.setReducedMotion(false)
        assertFalse(viewModel.uiState.value.settings.reducedMotion)
    }

    @Test
    fun `the hide-day-streak toggle stores both positions`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.setHideDayStreak(true)
        assertTrue(viewModel.uiState.value.settings.hideDayStreak)

        viewModel.setHideDayStreak(false)
        assertFalse(viewModel.uiState.value.settings.hideDayStreak)
    }

    /**
     * The screen reads the same flow every other screen reads, so a preference changed
     * anywhere — here or by a later restore — arrives without the screen being rebuilt.
     */
    @Test
    fun `a change made elsewhere shows up without the screen asking again`() = runTest {
        val viewModel = watchedViewModel()

        repository.setSessionLength(SessionLength.SHORT)

        assertEquals(SessionLength.SHORT, viewModel.uiState.value.settings.sessionLength)
    }

    @Test
    fun `an entered key is stored and shows as set`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.setApiKey("sk-ant-secret")

        assertEquals("sk-ant-secret", apiKeyRepository.apiKey())
        assertTrue(viewModel.uiState.value.hasApiKey)
    }

    /** The screen never reads the key back, so all it can know is whether there is one. */
    @Test
    fun `no key is where the app starts`() = runTest {
        val viewModel = watchedViewModel()

        assertFalse(viewModel.uiState.value.hasApiKey)
    }

    @Test
    fun `a removed key is gone`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.setApiKey("sk-ant-secret")

        viewModel.clearApiKey()

        assertNull(apiKeyRepository.apiKey())
        assertFalse(viewModel.uiState.value.hasApiKey)
    }

    /** An empty box is nothing entered, not an instruction to throw the key away. */
    @Test
    fun `a blank key is not stored and does not clear the one there`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.setApiKey("sk-ant-secret")

        viewModel.setApiKey("   ")

        assertEquals("sk-ant-secret", apiKeyRepository.apiKey())
    }

    /**
     * `uiState` only reads the repository while something is collecting it, so every test
     * needs a collector. `backgroundScope` cancels it when the test ends.
     */
    private fun TestScope.watchedViewModel() =
        SettingsViewModel(repository, apiKeyRepository).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect { }
            }
        }
}
