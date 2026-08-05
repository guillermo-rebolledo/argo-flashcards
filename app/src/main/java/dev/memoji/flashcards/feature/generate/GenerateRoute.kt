package dev.memoji.flashcards.feature.generate

/**
 * Where the Add Cards flow lives in the graph. It carries the Deck the Cards are headed for
 * when it was entered from one, and nothing when it was entered from the Deck list — where the
 * Cards are headed is then a new Deck. Either way the user can change the target inside.
 */
object GenerateRoute {

    /** The argument name the flow's ViewModel reads out of the back stack entry. */
    const val DECK_ID_ARG = "deckId"

    /**
     * What the argument holds when the flow was not entered from a Deck. A navigation argument
     * of a primitive type cannot be absent, so "no Deck" has to be a value rather than nothing.
     */
    const val NO_DECK = -1L

    private const val PREFIX = "generate"

    const val PATTERN = "$PREFIX?$DECK_ID_ARG={$DECK_ID_ARG}"

    /** Entered from the Deck list: there is no Deck to add to yet. */
    fun forNewDeck(): String = PREFIX

    /** Entered from a Deck, which is the Deck the flow starts aimed at. */
    fun forDeck(deckId: Long): String = "$PREFIX?$DECK_ID_ARG=$deckId"
}
