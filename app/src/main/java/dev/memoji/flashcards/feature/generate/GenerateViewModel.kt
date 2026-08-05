package dev.memoji.flashcards.feature.generate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.memoji.flashcards.core.data.CardRepository
import dev.memoji.flashcards.core.data.DeckRepository
import dev.memoji.flashcards.core.generation.CardGenerator
import dev.memoji.flashcards.core.generation.GenerationResult
import dev.memoji.flashcards.core.model.DeckSummary
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
 * The proposed Cards live in memory until the user saves. Only the Kept ones are written, into
 * the Deck the flow is aimed at: the one it was entered from, another the user picked, or a
 * new one made on save.
 */
@HiltViewModel
internal class GenerateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cardGenerator: CardGenerator,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        // Entered from a Deck, the flow is aimed at it from the first frame — waiting for the
        // name to arrive would show the new-Deck name field once and then take it away.
        GenerateUiState(target = savedStateHandle.initialTarget()),
    )
    val uiState: StateFlow<GenerateUiState> = _uiState.asStateFlow()

    /** The Deck to open once the flow is finished with, by any route. Cleared once acted on. */
    private val _savedDeckId = MutableStateFlow<Long?>(null)
    val savedDeckId: StateFlow<Long?> = _savedDeckId.asStateFlow()

    init {
        // The Decks are read for the picker, and the target's name is taken from the same
        // reading: it stays what the Deck is called, and a Deck deleted from the list behind
        // this screen stops being somewhere Cards can be added to.
        viewModelScope.launch {
            deckRepository.observeDeckSummaries().collect { summaries ->
                _uiState.update { state ->
                    state.copy(
                        decks = summaries.map { it.option() },
                        target = state.target.reconciledWith(summaries),
                    )
                }
            }
        }
    }

    fun setText(text: String) {
        _uiState.update { state ->
            // Typing clears the last failure: it was about text that has since changed.
            val entry = state.step as? GenerateStep.Entry ?: return@update state
            state.copy(step = entry.copy(text = text, failure = null))
        }
    }

    fun generate() {
        val state = _uiState.value
        val entry = state.step as? GenerateStep.Entry ?: return
        if (!entry.canGenerate) return

        _uiState.value = state.copy(step = GenerateStep.Busy)
        viewModelScope.launch {
            // Whatever the box was read as is what gets generated from — the same call the
            // screen showed a hint from before the tap.
            val step = when (val result = cardGenerator.generate(entry.source)) {
                is GenerationResult.Generated -> GenerateStep.Proposed(
                    deckName = result.deckName,
                    cards = result.cards.map { ProposedCard(it) },
                    sourceText = entry.text,
                )
                // Back to what they pasted, with the reason above it — the text is still
                // there to try again with, or to shorten.
                is GenerationResult.Failed -> entry.copy(failure = result.failure)
            }
            _uiState.update { it.copy(step = step) }
        }
    }

    fun setKept(index: Int, kept: Boolean) {
        _uiState.update { state ->
            val proposed = state.step as? GenerateStep.Proposed ?: return@update state
            if (index !in proposed.cards.indices) return@update state
            state.copy(
                step = proposed.copy(
                    cards = proposed.cards.mapIndexed { i, card ->
                        if (i == index) card.copy(kept = kept) else card
                    },
                ),
            )
        }
    }

    /** Only ever the name of a Deck that does not exist yet — see [GenerateTarget.ExistingDeck]. */
    fun setDeckName(name: String) {
        _uiState.update { state ->
            val proposed = state.step as? GenerateStep.Proposed ?: return@update state
            if (!state.isNamingNewDeck) return@update state
            state.copy(step = proposed.copy(deckName = name))
        }
    }

    /** Aims the flow at a Deck that already exists. An id that names no Deck is no target. */
    fun addToDeck(deckId: Long) {
        _uiState.update { state ->
            val deck = state.decks.find { it.id == deckId } ?: return@update state
            state.copy(target = GenerateTarget.ExistingDeck(deck.id, deck.name))
        }
    }

    /** Aims it at a Deck that does not exist yet, which the name field then asks about. */
    fun addToNewDeck() {
        _uiState.update { it.copy(target = GenerateTarget.NewDeck) }
    }

    /**
     * The way out of a page that would not open: the box is emptied of the link, ready for the
     * text it was meant to hold. Keeping the URL there would leave the failure's own message
     * pointing at the thing that caused it.
     */
    fun pasteTextInstead() {
        _uiState.update { state ->
            if (state.step !is GenerateStep.Entry) {
                state
            } else {
                state.copy(step = GenerateStep.Entry())
            }
        }
    }

    /** Back to the box, keeping what was pasted so a discarded Generation is not a retype. */
    fun backToEntry(text: String) {
        _uiState.update { it.copy(step = GenerateStep.Entry(text = text)) }
    }

    /**
     * Writes exactly the Kept Cards into the Deck the flow is aimed at, making that Deck first
     * if it does not exist yet. The unkept ones reach this method and stop here — they are
     * never written. Nothing already in the Deck is touched: the Cards are added beside what is
     * there, each starting at a Mastery streak of zero as any new Card does.
     */
    fun save() {
        val state = _uiState.value
        val proposed = state.step as? GenerateStep.Proposed ?: return
        if (!state.canSave) return

        _uiState.value = state.copy(step = proposed.copy(saving = true))
        viewModelScope.launch {
            val deckId = when (val target = state.target) {
                GenerateTarget.NewDeck -> deckRepository.createDeck(proposed.deckName)
                is GenerateTarget.ExistingDeck -> target.id
            }
            proposed.cards.filter(ProposedCard::kept).forEach { proposedCard ->
                cardRepository.createCard(
                    deckId,
                    proposedCard.card.front,
                    proposedCard.card.back,
                )
            }
            _savedDeckId.value = deckId
        }
    }

    /** One way out of this screen: an empty Deck to write Cards into by hand. */
    fun createEmptyDeck(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { _savedDeckId.value = deckRepository.createDeck(name) }
    }

    /**
     * The other: the Deck the flow is aimed at is already there, so writing Cards into it by
     * hand is a matter of opening it. Nothing is created.
     */
    fun openTargetDeck() {
        val target = _uiState.value.target as? GenerateTarget.ExistingDeck ?: return
        _savedDeckId.value = target.id
    }

    fun deckOpened() {
        _savedDeckId.value = null
    }

    /**
     * A target that is gone is no target: the Deck was deleted from the list behind this
     * screen, and the Cards have nowhere to go but a new Deck.
     */
    private fun GenerateTarget.reconciledWith(summaries: List<DeckSummary>): GenerateTarget {
        if (this !is GenerateTarget.ExistingDeck) return this
        val deck = summaries.find { it.deck.id == id }?.deck ?: return GenerateTarget.NewDeck
        return GenerateTarget.ExistingDeck(deck.id, deck.name)
    }

    private companion object {

        /**
         * The Deck the flow was entered from, if it was. The name is not known yet — the first
         * reading of the Decks fills it in, and nothing before then needs it.
         */
        fun SavedStateHandle.initialTarget(): GenerateTarget {
            val deckId = get<Long>(GenerateRoute.DECK_ID_ARG) ?: GenerateRoute.NO_DECK
            return if (deckId == GenerateRoute.NO_DECK) {
                GenerateTarget.NewDeck
            } else {
                GenerateTarget.ExistingDeck(deckId, name = "")
            }
        }

        fun DeckSummary.option() = DeckOption(deck.id, deck.name, cardCount)
    }
}
