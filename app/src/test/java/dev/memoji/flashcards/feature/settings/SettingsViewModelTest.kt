package dev.memoji.flashcards.feature.settings

import dev.memoji.flashcards.core.data.FakeApiKeyRepository
import dev.memoji.flashcards.core.data.FakeSettingsRepository
import dev.memoji.flashcards.core.model.ReminderTime
import dev.memoji.flashcards.core.model.SessionLength
import dev.memoji.flashcards.core.model.ThemePreference
import dev.memoji.flashcards.core.reminders.FakeReminderNotifier
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
    private val notifier = FakeReminderNotifier()

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
        assertEquals(
            SettingsUiState(),
            SettingsViewModel(repository, apiKeyRepository, notifier).uiState.value,
        )
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

    @Test
    fun `reminders are off until the user asks for them`() = runTest {
        val viewModel = watchedViewModel()

        assertEquals(ReminderStatus.OFF, viewModel.uiState.value.reminderStatus)
    }

    @Test
    fun `turning reminders on stores it and shows as on`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.setRemindersEnabled(true)

        assertTrue(repository.observeSettings().first().remindersEnabled)
        assertEquals(ReminderStatus.ON, viewModel.uiState.value.reminderStatus)
    }

    /**
     * The user asked and Android said no. The answer they gave is kept — the switch reads off
     * because that is the truth, and the row explains who is refusing.
     */
    @Test
    fun `a denied permission leaves the switch off and the setting standing`() = runTest {
        notifier.allowed = false
        val viewModel = watchedViewModel()

        viewModel.setRemindersEnabled(true)

        assertEquals(ReminderStatus.BLOCKED, viewModel.uiState.value.reminderStatus)
        assertTrue(repository.observeSettings().first().remindersEnabled)
    }

    /** Granting it later in system settings brings the reminder back without a second ask. */
    @Test
    fun `granting the permission afterwards turns the reminder on by itself`() = runTest {
        notifier.allowed = false
        val viewModel = watchedViewModel()
        viewModel.setRemindersEnabled(true)

        notifier.allowed = true
        viewModel.refreshNotificationsAllowed()

        assertEquals(ReminderStatus.ON, viewModel.uiState.value.reminderStatus)
    }

    /** A permission taken away while the app was elsewhere is noticed on the way back in. */
    @Test
    fun `a revoked permission shows as blocked when the screen comes back`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.setRemindersEnabled(true)

        notifier.allowed = false
        viewModel.refreshNotificationsAllowed()

        assertEquals(ReminderStatus.BLOCKED, viewModel.uiState.value.reminderStatus)
    }

    @Test
    fun `turning reminders off stores it`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.setRemindersEnabled(true)

        viewModel.setRemindersEnabled(false)

        assertFalse(repository.observeSettings().first().remindersEnabled)
        assertEquals(ReminderStatus.OFF, viewModel.uiState.value.reminderStatus)
    }

    @Test
    fun `a chosen reminder time is stored`() = runTest {
        val viewModel = watchedViewModel()

        viewModel.setReminderTime(ReminderTime(7, 30))

        assertEquals(ReminderTime(7, 30), repository.observeSettings().first().reminderTime)
    }

    /** Turning reminders off and on again comes back to the hour the user picked. */
    @Test
    fun `the reminder time outlives reminders being turned off`() = runTest {
        val viewModel = watchedViewModel()
        viewModel.setReminderTime(ReminderTime(7, 30))
        viewModel.setRemindersEnabled(true)

        viewModel.setRemindersEnabled(false)
        viewModel.setRemindersEnabled(true)

        assertEquals(ReminderTime(7, 30), viewModel.uiState.value.settings.reminderTime)
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
        SettingsViewModel(repository, apiKeyRepository, notifier).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect { }
            }
        }
}
