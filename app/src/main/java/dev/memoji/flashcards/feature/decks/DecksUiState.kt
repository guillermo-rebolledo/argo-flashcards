package dev.memoji.flashcards.feature.decks

import dev.memoji.flashcards.core.model.Deck

/**
 * Empty is its own state rather than an empty list: on first launch the screen has something to
 * say, and "we have not read the database yet" must not flash that message.
 */
sealed interface DecksUiState {
    data object Loading : DecksUiState
    data object Empty : DecksUiState
    data class Decks(val decks: List<Deck>) : DecksUiState
}
