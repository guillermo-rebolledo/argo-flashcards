package dev.memoji.flashcards.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.memoji.flashcards.core.data.ApiKeyRepository
import dev.memoji.flashcards.core.data.SettingsRepository
import dev.memoji.flashcards.core.model.ReminderTime
import dev.memoji.flashcards.core.model.SessionLength
import dev.memoji.flashcards.core.model.ThemePreference
import dev.memoji.flashcards.core.model.UserSettings
import dev.memoji.flashcards.core.reminders.ReminderNotifier
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What Settings shows: every preference, and whether a key is stored — never the key itself,
 * which is read only when a Generation needs it.
 */
internal data class SettingsUiState(
    val settings: UserSettings = UserSettings.DEFAULT,
    val hasApiKey: Boolean = false,
    /**
     * Assumed until asked. The answer arrives a frame later, and starting at `false` would
     * have every launch flash "Android is blocking notifications" at a user for whom nothing
     * is wrong.
     */
    val notificationsAllowed: Boolean = true,
) {
    val reminderStatus: ReminderStatus
        get() = reminderStatus(settings.remindersEnabled, notificationsAllowed)
}

/**
 * The Settings screen has no state of its own: what it shows is what is stored, and a write
 * comes back through the same flow every other screen is reading. Nothing is held here to be
 * saved later, so there is no way for the screen and the app to disagree.
 */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val reminderNotifier: ReminderNotifier,
) : ViewModel() {

    /**
     * Not a flow anyone else owns: Android has nothing to subscribe to here, so the screen
     * asks again whenever it comes back to the front — which is how a permission changed in
     * system settings gets noticed.
     */
    private val notificationsAllowed = MutableStateFlow(reminderNotifier.notificationsAllowed())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        apiKeyRepository.observeHasKey(),
        notificationsAllowed,
    ) { settings, hasApiKey, notificationsAllowed ->
        SettingsUiState(
            settings = settings,
            hasApiKey = hasApiKey,
            notificationsAllowed = notificationsAllowed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        // The defaults, not a loading state: every setting has an answer before the read
        // finishes, and a Settings screen that blinks is worse than one that fills in.
        initialValue = SettingsUiState(),
    )

    fun setSessionLength(length: SessionLength) {
        viewModelScope.launch { settingsRepository.setSessionLength(length) }
    }

    /**
     * The screen offers dark and light, not "follow the system" — [ThemePreference.FOLLOW_SYSTEM]
     * is where the user starts, and choosing either is what leaves it.
     */
    fun setDarkTheme(dark: Boolean) {
        val theme = if (dark) ThemePreference.DARK else ThemePreference.LIGHT
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun setReducedMotion(reducedMotion: Boolean) {
        viewModelScope.launch { settingsRepository.setReducedMotion(reducedMotion) }
    }

    fun setHideDayStreak(hideDayStreak: Boolean) {
        viewModelScope.launch { settingsRepository.setHideDayStreak(hideDayStreak) }
    }

    /**
     * Stored whatever the system then does about it. Turning the switch on is the user
     * answering a question about what they want, and a permission they were refused does not
     * change that answer — the row says so, and granting it later needs no second visit here.
     * Nothing is scheduled from here: [dev.memoji.flashcards.core.reminders.ReminderCoordinator]
     * is watching what gets written.
     */
    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRemindersEnabled(enabled) }
        refreshNotificationsAllowed()
    }

    fun setReminderTime(time: ReminderTime) {
        viewModelScope.launch { settingsRepository.setReminderTime(time) }
    }

    /** Asked again every time the screen comes back to the front, and after a permission prompt. */
    fun refreshNotificationsAllowed() {
        notificationsAllowed.value = reminderNotifier.notificationsAllowed()
    }

    /** Blank is not a key: an empty box is nothing entered, not an instruction to clear. */
    fun setApiKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch { apiKeyRepository.setApiKey(key) }
    }

    fun clearApiKey() {
        viewModelScope.launch { apiKeyRepository.clearApiKey() }
    }

    private companion object {
        /** Long enough to ride out a rotation without re-reading the preferences. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
