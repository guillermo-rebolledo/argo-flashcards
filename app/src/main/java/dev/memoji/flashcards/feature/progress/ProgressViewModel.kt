package dev.memoji.flashcards.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.memoji.flashcards.core.data.DeckRepository
import dev.memoji.flashcards.core.data.SessionRepository
import dev.memoji.flashcards.core.data.SettingsRepository
import dev.memoji.flashcards.core.domain.summarizeProgress
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Everything on this screen is counted from the Session log when it is read, so a Session
 * finished a moment ago is already in the streak and no total needs to be kept up to date.
 *
 * The Decks come along only for their names — the figures are the log's, not theirs.
 */
@HiltViewModel
internal class ProgressViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    deckRepository: DeckRepository,
    settingsRepository: SettingsRepository,
    clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<ProgressUiState> = combine(
        sessionRepository.observeSessions(),
        deckRepository.observeDeckSummaries(),
        settingsRepository.observeSettings(),
    ) { sessions, decks, settings ->
        if (sessions.isEmpty()) {
            ProgressUiState.Empty
        } else {
            ProgressUiState.Summary(
                summary = summarizeProgress(
                    sessions = sessions,
                    deckNames = decks.associate { it.deck.id to it.deck.name },
                    clock = clock,
                ),
                hideStreak = settings.hideStreak,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ProgressUiState.Loading,
    )

    private companion object {
        /** Long enough to ride out a rotation without re-reading the database. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
