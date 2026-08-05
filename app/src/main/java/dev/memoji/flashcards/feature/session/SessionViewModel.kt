package dev.memoji.flashcards.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.memoji.flashcards.core.data.CardRepository
import dev.memoji.flashcards.core.data.DeckRepository
import dev.memoji.flashcards.core.data.SettingsRepository
import dev.memoji.flashcards.core.domain.composeSession
import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Grade
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One sitting. The Cards are drawn once, when the Session starts, and held here for as long as
 * it lasts: a Session the user is part-way through must not reshuffle underneath them because
 * a Grade they just gave changed what the Deck would compose now.
 */
@HiltViewModel
internal class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle[SessionRoute.DECK_ID_ARG]) {
        "A Session was started without a Deck id"
    }

    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private lateinit var deckName: String
    private var cards: List<Card> = emptyList()
    private var position = 0
    private var knewIt = 0
    private val misses = mutableListOf<Card>()

    init {
        viewModelScope.launch {
            val deck = deckRepository.observeDeck(deckId).first()
            deckName = deck?.name.orEmpty()
            if (deck == null) {
                _uiState.value = SessionUiState.Empty
                return@launch
            }
            val length = settingsRepository.observeSessionLength().first().cards
            begin(composeSession(cardRepository.cardsInDeck(deckId), length))
        }
    }

    /** Revealing is deliberate — the Back is not shown until the user asks for it. */
    fun reveal() {
        val reviewing = _uiState.value as? SessionUiState.Reviewing ?: return
        _uiState.value = reviewing.copy(revealed = true)
    }

    /**
     * Writes the Grade and moves on. The Card is graded whether or not its Back was revealed:
     * a user who knew it on sight has no reason to look.
     */
    fun grade(grade: Grade) {
        val reviewing = _uiState.value as? SessionUiState.Reviewing ?: return
        val card = reviewing.card

        when (grade) {
            Grade.KNEW_IT -> knewIt++
            Grade.AGAIN -> misses += card
        }
        viewModelScope.launch { cardRepository.recordGrade(card.id, grade) }

        position++
        _uiState.value = if (position < cards.size) {
            reviewing.copy(card = cards[position], revealed = false, position = position + 1)
        } else {
            SessionUiState.Finished(
                deckName = deckName,
                knewIt = knewIt,
                total = cards.size,
                misses = misses.toList(),
            )
        }
    }

    /**
     * A second Session over the Cards that did not come back, scored on its own. Closing the
     * loop while the material is fresh is the point, so nothing is drawn from the Deck around
     * them — these Cards and no others.
     */
    fun reviewMisses() {
        val finished = _uiState.value as? SessionUiState.Finished ?: return
        if (finished.misses.isEmpty()) return
        begin(finished.misses)
    }

    private fun begin(session: List<Card>) {
        cards = session
        position = 0
        knewIt = 0
        misses.clear()
        _uiState.value = if (session.isEmpty()) {
            SessionUiState.Empty
        } else {
            SessionUiState.Reviewing(
                deckName = deckName,
                card = session.first(),
                revealed = false,
                position = 1,
                total = session.size,
            )
        }
    }
}
