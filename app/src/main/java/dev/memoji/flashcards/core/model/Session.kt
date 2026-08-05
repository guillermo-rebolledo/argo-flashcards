package dev.memoji.flashcards.core.model

import java.time.Duration
import java.time.Instant

/**
 * One sitting, as it was recorded when it ended. Every figure on the Progress screen is counted
 * from these rows and from nothing else, so no total can drift away from what happened.
 *
 * [deckId] is null once the Deck is deleted. The sitting still happened — losing a Deck must not
 * take the day it was studied on out of the user's streak.
 */
data class Session(
    val id: Long,
    val deckId: Long?,
    val started: Instant,
    val ended: Instant,
    val cardsReviewed: Int,
    val knewIt: Int,
) {

    /**
     * How long the sitting took, capped. The app has no way of knowing that a Session left open
     * on a backgrounded phone was over — the row honestly says it ended when the user came back —
     * so the ceiling is applied here, in the one place minutes are ever read from. Capping the
     * stored value instead would write a lie into the only record there is.
     */
    val duration: Duration
        get() = Duration.between(started, ended)
            .coerceIn(Duration.ZERO, MAX_DURATION)

    companion object {
        /**
         * The longest sitting the app can produce is 10 Cards and a pass over the Misses, and a
         * Card a user is actually thinking about takes well under a minute. Set with room to
         * spare above that, because the cost of clipping a real sitting is a minute the user
         * did study going unrecorded — while anything past this is a phone in a pocket.
         */
        val MAX_DURATION: Duration = Duration.ofMinutes(20)
    }
}
