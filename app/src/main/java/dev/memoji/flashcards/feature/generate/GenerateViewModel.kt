package dev.memoji.flashcards.feature.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.memoji.flashcards.core.data.CardRepository
import dev.memoji.flashcards.core.data.DeckRepository
import dev.memoji.flashcards.core.generation.CardGenerator
import dev.memoji.flashcards.core.generation.GenerationResult
import dev.memoji.flashcards.core.generation.Source
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Generation runs here, in the ViewModel's own scope: it survives the screen being recreated
 * by a rotation, and it is cancelled when the user leaves the flow and the ViewModel goes.
 * There is no worker and no pending-Deck state — a Generation the user walked away from is a
 * Generation that never happened.
 *
 * The proposed Cards live in memory until the user saves. Only the Kept ones are written.
 */
@HiltViewModel
internal class GenerateViewModel @Inject constructor(
    private val cardGenerator: CardGenerator,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GenerateUiState>(GenerateUiState.Entry())
    val uiState: StateFlow<GenerateUiState> = _uiState.asStateFlow()

    /** The Deck to open once one has been made, by either route. Cleared once acted on. */
    private val _savedDeckId = MutableStateFlow<Long?>(null)
    val savedDeckId: StateFlow<Long?> = _savedDeckId.asStateFlow()

    fun setText(text: String) {
        _uiState.update { state ->
            // Typing clears the last failure: it was about text that has since changed.
            (state as? GenerateUiState.Entry)?.copy(text = text, failure = null) ?: state
        }
    }

    fun generate() {
        val entry = _uiState.value as? GenerateUiState.Entry ?: return
        if (!entry.canGenerate) return

        _uiState.value = GenerateUiState.Busy
        viewModelScope.launch {
            when (val result = cardGenerator.generate(Source.PastedText(entry.text))) {
                is GenerationResult.Generated -> _uiState.value = GenerateUiState.Proposed(
                    deckName = result.deckName,
                    cards = result.cards.map { ProposedCard(it) },
                    sourceText = entry.text,
                )
                // Back to what they pasted, with the reason above it — the text is still
                // there to try again with, or to shorten.
                is GenerationResult.Failed -> _uiState.value =
                    entry.copy(failure = result.failure)
            }
        }
    }

    fun setKept(index: Int, kept: Boolean) {
        _uiState.update { state ->
            val review = state as? GenerateUiState.Proposed ?: return@update state
            if (index !in review.cards.indices) return@update state
            review.copy(
                cards = review.cards.mapIndexed { i, card ->
                    if (i == index) card.copy(kept = kept) else card
                },
            )
        }
    }

    fun setDeckName(name: String) {
        _uiState.update { state ->
            (state as? GenerateUiState.Proposed)?.copy(deckName = name) ?: state
        }
    }

    /** Back to the box, keeping what was pasted so a discarded Generation is not a retype. */
    fun backToEntry(text: String) {
        _uiState.value = GenerateUiState.Entry(text = text)
    }

    /**
     * Creates the Deck and exactly the Kept Cards. The unkept ones reach this method and stop
     * here — they are never written, and the Deck the user opens holds only what they ticked.
     */
    fun save() {
        val review = _uiState.value as? GenerateUiState.Proposed ?: return
        if (!review.canSave) return

        _uiState.value = review.copy(saving = true)
        viewModelScope.launch {
            val deckId = deckRepository.createDeck(review.deckName)
            review.cards.filter(ProposedCard::kept).forEach { proposed ->
                cardRepository.createCard(deckId, proposed.card.front, proposed.card.back)
            }
            _savedDeckId.value = deckId
        }
    }

    /** The other way out of this screen: an empty Deck to write Cards into by hand. */
    fun createEmptyDeck(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { _savedDeckId.value = deckRepository.createDeck(name) }
    }

    fun deckOpened() {
        _savedDeckId.value = null
    }
}
