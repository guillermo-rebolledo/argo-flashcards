package dev.memoji.flashcards.feature.decks

import dev.memoji.flashcards.core.model.DeckSummary

/**
 * Empty is its own state rather than an empty list: on first launch the screen has something to
 * say, and "we have not read the database yet" must not flash that message.
 */
internal sealed interface DecksUiState {
    data object Loading : DecksUiState
    data object Empty : DecksUiState

    /**
     * [upNext] is one of [decks], or null when none of them has a Card in it yet. It is held
     * apart rather than flagged on the Deck it points at, because the card at the top of the
     * screen is one thing and the list below it is another.
     */
    data class Decks(
        val decks: List<DeckSummary>,
        val upNext: DeckSummary?,
    ) : DecksUiState
}
