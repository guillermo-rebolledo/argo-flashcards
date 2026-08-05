package dev.memoji.flashcards.feature.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.memoji.flashcards.core.data.DeckRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DecksViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
) : ViewModel() {

    val uiState: StateFlow<DecksUiState> = deckRepository.observeDecks()
        .map { decks ->
            if (decks.isEmpty()) DecksUiState.Empty else DecksUiState.Decks(decks)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DecksUiState.Loading,
        )

    /** A name of nothing but whitespace is not a name; the dialog also blocks it. */
    fun createDeck(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { deckRepository.createDeck(name) }
    }

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
