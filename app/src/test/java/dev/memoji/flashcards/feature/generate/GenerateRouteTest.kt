package dev.memoji.flashcards.feature.generate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two ways into the Add Cards flow, both of which the one pattern has to match: from the
 * Deck list with no Deck to add to, and from a Deck with one.
 */
class GenerateRouteTest {

    @Test
    fun `entering from the Deck list carries no Deck`() {
        assertEquals("generate", GenerateRoute.forNewDeck())
    }

    @Test
    fun `entering from a Deck carries its id`() {
        assertEquals("generate?deckId=7", GenerateRoute.forDeck(7))
    }

    /** The route the graph is declared with is the one both are matched against. */
    @Test
    fun `both routes are the pattern with the argument filled in or left out`() {
        assertEquals("generate?deckId={deckId}", GenerateRoute.PATTERN)
        assertTrue(GenerateRoute.PATTERN.startsWith(GenerateRoute.forNewDeck()))
    }

    /** No real Deck id is negative, so the default cannot be mistaken for one. */
    @Test
    fun `no Deck is a value a Deck id never takes`() {
        assertTrue(GenerateRoute.NO_DECK < 0)
    }
}
