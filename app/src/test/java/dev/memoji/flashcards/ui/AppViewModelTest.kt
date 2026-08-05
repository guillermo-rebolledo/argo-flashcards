package dev.memoji.flashcards.ui

import dev.memoji.flashcards.core.data.FakeSettingsRepository
import dev.memoji.flashcards.core.model.ThemePreference
import dev.memoji.flashcards.core.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The theme and the motion setting reach the app through here and nowhere else, so what this
 * covers is that a preference changed on the Settings screen arrives without anything being
 * restarted — the Settings screen's own tests cannot show that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val repository = FakeSettingsRepository()

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun restoreDispatcher() {
        Dispatchers.resetMain()
    }

    /** The first frame is drawn from this, so it must be a whole answer rather than nothing. */
    @Test
    fun `the state starts at the defaults`() {
        assertEquals(UserSettings.DEFAULT, AppViewModel(repository).uiState.value)
    }

    /**
     * Read eagerly, not `WhileSubscribed`: nothing has composed yet when the app starts, and a
     * flow that only runs while watched would still be on the defaults at that point.
     */
    @Test
    fun `a stored preference arrives with nothing collecting`() = runTest {
        repository.setTheme(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, AppViewModel(repository).uiState.value.theme)
    }

    @Test
    fun `a theme chosen on the Settings screen reaches the app`() = runTest {
        val viewModel = AppViewModel(repository)

        repository.setTheme(ThemePreference.LIGHT)

        assertEquals(ThemePreference.LIGHT, viewModel.uiState.value.theme)
    }

    @Test
    fun `a reduced-motion override chosen on the Settings screen reaches the app`() = runTest {
        val viewModel = AppViewModel(repository)

        repository.setReducedMotion(true)

        assertTrue(viewModel.uiState.value.reducedMotion)
    }
}
