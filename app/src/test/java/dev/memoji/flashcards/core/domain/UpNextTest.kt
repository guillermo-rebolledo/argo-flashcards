package dev.memoji.flashcards.core.domain

import dev.memoji.flashcards.core.model.Deck
import dev.memoji.flashcards.core.model.DeckSummary
import dev.memoji.flashcards.core.testing.MutableClock
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class UpNextTest {

    @Test
    fun `the Deck studied most recently is the one offered`() {
        val decks = listOf(
            summary(id = 1, studiedAt = daysIn(1)),
            summary(id = 2, studiedAt = daysIn(3)),
            summary(id = 3, studiedAt = daysIn(2)),
        )

        assertEquals(2L, upNextDeck(decks)?.deck?.id)
    }

    @Test
    fun `with nothing studied yet, the newest Deck is offered`() {
        val decks = listOf(
            summary(id = 1, createdAt = daysIn(1)),
            summary(id = 2, createdAt = daysIn(3)),
            summary(id = 3, createdAt = daysIn(2)),
        )

        assertEquals(2L, upNextDeck(decks)?.deck?.id)
    }

    @Test
    fun `a Deck that has been studied comes before one that never has`() {
        val decks = listOf(
            summary(id = 1, createdAt = daysIn(5)),
            summary(id = 2, createdAt = daysIn(1), studiedAt = daysIn(2)),
        )

        assertEquals(2L, upNextDeck(decks)?.deck?.id)
    }

    @Test
    fun `a fully Mastered Deck steps aside for the next one with Learning Cards`() {
        val decks = listOf(
            summary(id = 1, cards = 4, mastered = 4, studiedAt = daysIn(3)),
            summary(id = 2, cards = 4, mastered = 1, studiedAt = daysIn(2)),
            summary(id = 3, cards = 4, mastered = 0, studiedAt = daysIn(1)),
        )

        assertEquals(2L, upNextDeck(decks)?.deck?.id)
    }

    @Test
    fun `a Deck with one Card left to learn is not stepped over`() {
        val decks = listOf(
            summary(id = 1, cards = 4, mastered = 3, studiedAt = daysIn(3)),
            summary(id = 2, cards = 4, mastered = 0, studiedAt = daysIn(2)),
        )

        assertEquals(1L, upNextDeck(decks)?.deck?.id)
    }

    /** An all-Mastered Session is a real Session, and better than a home screen with nothing. */
    @Test
    fun `when every Deck is fully Mastered the most recent one is offered anyway`() {
        val decks = listOf(
            summary(id = 1, cards = 2, mastered = 2, studiedAt = daysIn(1)),
            summary(id = 2, cards = 2, mastered = 2, studiedAt = daysIn(2)),
        )

        assertEquals(2L, upNextDeck(decks)?.deck?.id)
    }

    @Test
    fun `a Deck with no Cards is never offered`() {
        val decks = listOf(
            summary(id = 1, cards = 0, createdAt = daysIn(5)),
            summary(id = 2, cards = 3, createdAt = daysIn(1)),
        )

        assertEquals(2L, upNextDeck(decks)?.deck?.id)
    }

    @Test
    fun `no Decks means nothing to study`() {
        assertEquals(null, upNextDeck(emptyList()))
    }

    @Test
    fun `Decks that are all empty mean nothing to study`() {
        assertEquals(null, upNextDeck(listOf(summary(id = 1, cards = 0))))
    }

    private fun summary(
        id: Long,
        cards: Int = 3,
        mastered: Int = 0,
        createdAt: Instant = MutableClock.START,
        studiedAt: Instant? = null,
    ) = DeckSummary(
        deck = Deck(id = id, name = "Deck $id", createdAt = createdAt),
        cardCount = cards,
        masteredCount = mastered,
        lastStudiedAt = studiedAt,
    )

    private fun daysIn(days: Long) = MutableClock.START.plus(Duration.ofDays(days))
}
