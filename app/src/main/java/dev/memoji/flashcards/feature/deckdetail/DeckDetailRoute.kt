package dev.memoji.flashcards.feature.deckdetail

/**
 * Where the Deck detail screen lives in the graph. The pattern and the way a route is built
 * from a Deck id sit together, so the two cannot drift apart.
 */
object DeckDetailRoute {

    /** The argument name the screen's ViewModel reads out of the back stack entry. */
    const val DECK_ID_ARG = "deckId"

    const val PATTERN = "deck/{$DECK_ID_ARG}"

    fun of(deckId: Long): String = "deck/$deckId"
}
