package dev.memoji.flashcards.feature.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.memoji.flashcards.core.data.DeckRepository
import dev.memoji.flashcards.core.domain.upNextDeck
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class DecksViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
) : ViewModel() {

    val uiState: StateFlow<DecksUiState> = deckRepository.observeDeckSummaries()
        .map { decks ->
            if (decks.isEmpty()) {
                DecksUiState.Empty
            } else {
                DecksUiState.Decks(decks = decks, upNext = upNextDeck(decks))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DecksUiState.Loading,
        )

    fun renameDeck(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { deckRepository.renameDeck(id, name) }
    }

    fun deleteDeck(id: Long) {
        viewModelScope.launch { deckRepository.deleteDeck(id) }
    }

    private companion object {
        /** Long enough to ride out a rotation without re-reading the database. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
