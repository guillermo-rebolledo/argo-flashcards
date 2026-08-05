package dev.memoji.flashcards.feature.deckdetail

import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Deck

/** The three chips above the Card list. Every Card is either Learning or Mastered. */
internal enum class CardFilter {
    ALL,
    LEARNING,
    MASTERED,
    ;

    fun accepts(card: Card): Boolean = when (this) {
        ALL -> true
        LEARNING -> !card.isMastered
        MASTERED -> card.isMastered
    }
}

internal sealed interface DeckDetailUiState {

    data object Loading : DeckDetailUiState

    /**
     * The Deck was deleted — from this screen's own menu, or from the Deck list while this was
     * on the back stack. Either way there is nothing left to show and the screen leaves.
     */
    data object DeckGone : DeckDetailUiState

    /**
     * [cards] has [filter] applied; [cardCount] and [masteredCount] count the whole Deck, so
     * the mastery summary keeps saying the same thing while the chips change what is listed.
     */
    data class Ready(
        val deck: Deck,
        val cards: List<Card>,
        val filter: CardFilter,
        val cardCount: Int,
        val masteredCount: Int,
    ) : DeckDetailUiState {

        /** An empty Deck, as opposed to a filter that happens to match nothing. */
        val isDeckEmpty: Boolean get() = cardCount == 0
    }
}
