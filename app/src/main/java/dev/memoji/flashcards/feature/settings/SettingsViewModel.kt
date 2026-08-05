package dev.memoji.flashcards.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.memoji.flashcards.core.data.ApiKeyRepository
import dev.memoji.flashcards.core.data.SettingsRepository
import dev.memoji.flashcards.core.model.SessionLength
import dev.memoji.flashcards.core.model.ThemePreference
import dev.memoji.flashcards.core.model.UserSettings
import javax.inject.Inject
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
)

/**
 * The Settings screen has no state of its own: what it shows is what is stored, and a write
 * comes back through the same flow every other screen is reading. Nothing is held here to be
 * saved later, so there is no way for the screen and the app to disagree.
 */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val apiKeyRepository: ApiKeyRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        apiKeyRepository.observeHasKey(),
    ) { settings, hasApiKey ->
        SettingsUiState(settings = settings, hasApiKey = hasApiKey)
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
