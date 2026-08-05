package dev.memoji.flashcards.feature.session

import dev.memoji.flashcards.core.model.Card

internal sealed interface SessionUiState {

    data object Loading : SessionUiState

    /**
     * There is nothing to review: the Deck has no Cards, or it was deleted before the Session
     * could start. Either way the screen says so rather than showing a Session of nothing.
     */
    data object Empty : SessionUiState

    /**
     * One Card, Front up until [revealed]. [position] is 1-based because it is read as "3 of
     * 5" — the Card the user is on, not the number of Cards behind them.
     */
    data class Reviewing(
        val deckName: String,
        val card: Card,
        val revealed: Boolean,
        val position: Int,
        val total: Int,
    ) : SessionUiState {

        /** How much of the Session is behind the user — the Card in hand is not done yet. */
        val progress: Float get() = (position - 1).toFloat() / total
    }

    /**
     * [knewIt] out of [total] on the first pass, and every Card that did not come back. The
     * Misses are the Cards as they were when they came up, so the list reads as what the user
     * just saw rather than as what grading them has since done to their streaks.
     */
    data class Finished(
        val deckName: String,
        val knewIt: Int,
        val total: Int,
        val misses: List<Card>,
    ) : SessionUiState {

        val score: Float get() = if (total == 0) 0f else knewIt.toFloat() / total
    }
}
