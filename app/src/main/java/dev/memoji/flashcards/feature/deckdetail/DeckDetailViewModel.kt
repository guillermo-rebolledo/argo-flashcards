package dev.memoji.flashcards.feature.deckdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.memoji.flashcards.core.data.CardRepository
import dev.memoji.flashcards.core.data.DeckRepository
import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Deck
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class DeckDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle[DeckDetailRoute.DECK_ID_ARG]) {
        "DeckDetail was opened without a Deck id"
    }

    /**
     * The chip lives here rather than in the composable so that it survives the screen being
     * recreated and so the filtering has somewhere to be tested.
     */
    private val filter = MutableStateFlow(CardFilter.ALL)

    val uiState: StateFlow<DeckDetailUiState> = combine(
        deckRepository.observeDeck(deckId),
        cardRepository.observeCards(deckId),
        filter,
    ) { deck, cards, selectedFilter ->
        if (deck == null) DeckDetailUiState.DeckGone else ready(deck, cards, selectedFilter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DeckDetailUiState.Loading,
    )

    fun setFilter(filter: CardFilter) {
        this.filter.value = filter
    }

    /** Both sides are required; the editor also blocks a blank one. */
    fun addCard(front: String, back: String) {
        if (front.isBlank() || back.isBlank()) return
        // Writing into a Deck that has just been deleted would fail on the foreign key. The
        // screen is already on its way out by then, so there is nothing to add to.
        if (uiState.value is DeckDetailUiState.DeckGone) return
        // A new Card is always Learning, so the Mastered chip would swallow it. Writing a Card
        // and not seeing it appear reads as the app having lost it.
        if (filter.value == CardFilter.MASTERED) filter.value = CardFilter.ALL
        viewModelScope.launch { cardRepository.createCard(deckId, front, back) }
    }

    fun editCard(id: Long, front: String, back: String) {
        if (front.isBlank() || back.isBlank()) return
        viewModelScope.launch { cardRepository.updateCard(id, front, back) }
    }

    fun deleteCard(id: Long) {
        viewModelScope.launch { cardRepository.deleteCard(id) }
    }

    fun renameDeck(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { deckRepository.renameDeck(deckId, name) }
    }

    /** The Cards go with it — the database cascades the delete. */
    fun deleteDeck() {
        viewModelScope.launch { deckRepository.deleteDeck(deckId) }
    }

    private fun ready(deck: Deck, cards: List<Card>, filter: CardFilter) = DeckDetailUiState.Ready(
        deck = deck,
        cards = cards.filter(filter::accepts),
        filter = filter,
        cardCount = cards.size,
        masteredCount = cards.count(Card::isMastered),
    )

    private companion object {
        /** Long enough to ride out a rotation without re-reading the database. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
