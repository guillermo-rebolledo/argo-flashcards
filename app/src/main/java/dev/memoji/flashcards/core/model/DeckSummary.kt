package dev.memoji.flashcards.core.model

import java.time.Instant

/**
 * A Deck and what the screens listing Decks say about it, counted in one query rather than by
 * loading every Card. [lastStudiedAt] is the most recent time any of its Cards was seen, which
 * is what "the Deck I studied most recently" means — there is no separate record of a sitting.
 */
data class DeckSummary(
    val deck: Deck,
    val cardCount: Int,
    val masteredCount: Int,
    val lastStudiedAt: Instant?,
) {

    /** An empty Deck has no Session in it. */
    val hasCards: Boolean get() = cardCount > 0

    val learningCount: Int get() = cardCount - masteredCount

    /** Nothing left to learn here — still studiable, but not where a user should be sent. */
    val isFullyMastered: Boolean get() = hasCards && learningCount == 0
}
