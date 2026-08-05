package dev.memoji.flashcards.feature.deckdetail

import androidx.annotation.StringRes
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Deck

/**
 * The three chips above the Card list. Every Card is either Learning or Mastered, so the chip
 * label and what to say when a chip matches nothing both belong to the chip — one place to
 * look, rather than a `when` on this enum in each of the screen's three corners.
 */
internal enum class CardFilter(
    @param:StringRes val labelRes: Int,
    @param:StringRes val emptyTitleRes: Int,
    @param:StringRes val emptyBodyRes: Int,
) {
    // ALL only reaches its empty state on a Deck with no Cards at all, which the screen shows
    // its own message for — these two are here so the enum has no hole in it.
    ALL(R.string.cards_filter_all, R.string.cards_empty_title, R.string.cards_empty_body),
    LEARNING(
        R.string.cards_filter_learning,
        R.string.cards_none_learning_title,
        R.string.cards_none_learning_body,
    ),
    MASTERED(
        R.string.cards_filter_mastered,
        R.string.cards_none_mastered_title,
        R.string.cards_none_mastered_body,
    ),
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
