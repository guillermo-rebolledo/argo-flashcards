package dev.memoji.flashcards.core.model

import java.time.Instant

/**
 * One idea, with a Front and a Back. Never two ideas — hence exactly two content fields and no
 * room for an example, a hint, or an image.
 */
data class Card(
    val id: Long,
    val deckId: Long,
    val front: String,
    val back: String,
    val masteryStreak: Int,
    /** Null until the Card has been through a Review. */
    val lastSeenAt: Instant?,
    val createdAt: Instant,
) {
    /**
     * Derived from the Mastery streak rather than stored beside it. A stored flag could drift
     * out of step with the streak it is supposed to summarise; this cannot.
     */
    val isMastered: Boolean get() = masteryStreak >= MASTERY_THRESHOLD

    companion object {
        /** How many consecutive `Knew it` grades make a Card Mastered. */
        const val MASTERY_THRESHOLD = 3
    }
}
