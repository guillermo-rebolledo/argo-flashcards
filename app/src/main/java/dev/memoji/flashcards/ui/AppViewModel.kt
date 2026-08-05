package dev.memoji.flashcards.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.memoji.flashcards.core.data.SettingsRepository
import dev.memoji.flashcards.core.model.UserSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The settings that decide how the whole app looks and moves, rather than what any one screen
 * shows. They are read here, above the navigation graph, so a change made on the Settings
 * screen reaches the theme and every screen's motion without anything being recreated.
 */
@HiltViewModel
internal class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<UserSettings> = settingsRepository.observeSettings()
        .stateIn(
            scope = viewModelScope,
            // Eagerly, and never stopped: the read starts the moment the app does rather than
            // when something first composes, so the window in which the defaults are on
            // screen is as short as a local file read can make it.
            started = SharingStarted.Eagerly,
            initialValue = UserSettings.DEFAULT,
        )
}
