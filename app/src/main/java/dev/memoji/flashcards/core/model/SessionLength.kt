package dev.memoji.flashcards.core.model

/**
 * How many Cards a Session contains. Three values and no slider: the point of a Session is
 * that the user can see the end of it from the start, which a free number invites them to
 * lose. Anything else that turns up in storage is not a Session length, so it reads as
 * [DEFAULT] rather than being honoured.
 */
enum class SessionLength(val cards: Int) {
    SHORT(3),
    STANDARD(5),
    LONG(10),
    ;

    companion object {
        /** What a Session is until the user says otherwise. */
        val DEFAULT = STANDARD

        fun ofCards(cards: Int): SessionLength = entries.find { it.cards == cards } ?: DEFAULT
    }
}
