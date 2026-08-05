package dev.memoji.flashcards.feature.session

/**
 * Where a Session lives in the graph. A Session belongs to one Deck, so the Deck id is the
 * whole of its identity — everything else about it is decided when it starts.
 */
object SessionRoute {

    /** The argument name the screen's ViewModel reads out of the back stack entry. */
    const val DECK_ID_ARG = "deckId"

    private const val PREFIX = "session"

    const val PATTERN = "$PREFIX/{$DECK_ID_ARG}"

    fun of(deckId: Long): String = "$PREFIX/$deckId"
}
