package dev.memoji.flashcards.core.domain

import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.testing.MutableClock
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one place a silent bug corrupts user data over weeks with no visible symptom — a Card
 * that quietly never resurfaces. Every case the rule has is written down here, against a
 * clock the test moves by hand so last-seen ordering is a fact rather than a race.
 */
class SessionCompositionTest {

    private val clock = MutableClock()

    @Test
    fun `a Session is Session-length Cards long`() {
        val deck = (1..20).map { learning(it) }

        assertEquals(5, composeSession(deck, length = 5).size)
        assertEquals(3, composeSession(deck, length = 3).size)
        assertEquals(10, composeSession(deck, length = 10).size)
    }

    @Test
    fun `no Card appears in a Session twice`() {
        val deck = (1..10).map { learning(it) }

        val ids = composeSession(deck, length = 10).map { it.id }

        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `Learning Cards come in lowest Mastery streak first`() {
        val deck = listOf(
            learning(id = 1, streak = 2),
            learning(id = 2, streak = 0),
            learning(id = 3, streak = 1),
        )

        assertEquals(listOf(2L, 3L, 1L), composeSession(deck, length = 3).map { it.id })
    }

    @Test
    fun `Learning Cards on the same streak come oldest last-seen first`() {
        val deck = listOf(
            learning(id = 1, seenAt = minutesIn(30)),
            learning(id = 2, seenAt = minutesIn(10)),
            learning(id = 3, seenAt = minutesIn(20)),
        )

        assertEquals(listOf(2L, 3L, 1L), composeSession(deck, length = 3).map { it.id })
    }

    /** A Card that has never been through a Review is the oldest thing there is. */
    @Test
    fun `a Card that has never been seen comes before one that has`() {
        val deck = listOf(
            learning(id = 1, seenAt = minutesIn(10)),
            learning(id = 2, seenAt = null),
        )

        assertEquals(listOf(2L, 1L), composeSession(deck, length = 2).map { it.id })
    }

    /** The streak is the first sort key, so a never-seen Card does not jump a weaker one. */
    @Test
    fun `the streak is compared before last-seen`() {
        val deck = listOf(
            learning(id = 1, streak = 1, seenAt = null),
            learning(id = 2, streak = 0, seenAt = minutesIn(10)),
        )

        assertEquals(listOf(2L, 1L), composeSession(deck, length = 2).map { it.id })
    }

    @Test
    fun `every fifth slot draws a Mastered Card`() {
        val deck = (1..12).map { learning(it) } + (101..104).map { mastered(it) }

        val ids = composeSession(deck, length = 10).map { it.id }

        assertEquals(listOf(101L, 102L), ids.filterIndexed { index, _ -> (index + 1) % 5 == 0 })
        assertEquals(8, ids.count { it < 100 })
    }

    @Test
    fun `Mastered Cards come oldest last-seen first`() {
        val deck = (1..12).map { learning(it) } + listOf(
            mastered(id = 101, seenAt = minutesIn(30)),
            mastered(id = 102, seenAt = minutesIn(10)),
        )

        val ids = composeSession(deck, length = 10).map { it.id }

        assertEquals(listOf(102L, 101L), ids.filterIndexed { index, _ -> (index + 1) % 5 == 0 })
    }

    /** The first four slots are Learning, so a Session of 3 or 4 has no Mastered slot in it. */
    @Test
    fun `a Session shorter than five has no Mastered slot`() {
        val deck = (1..3).map { learning(it) } + (101..103).map { mastered(it) }

        val ids = composeSession(deck, length = 3).map { it.id }

        assertEquals(listOf(1L, 2L, 3L), ids)
    }

    @Test
    fun `a Deck with no Mastered Cards yields an all-Learning Session`() {
        val deck = (1..10).map { learning(it) }

        val ids = composeSession(deck, length = 10).map { it.id }

        assertEquals((1L..10L).toList(), ids)
    }

    @Test
    fun `a fully Mastered Deck yields an all-Mastered Session`() {
        val deck = (101..110).map { mastered(it) }

        val ids = composeSession(deck, length = 5).map { it.id }

        assertEquals(listOf(101L, 102L, 103L, 104L, 105L), ids)
    }

    /** Two Learning Cards and plenty Mastered: the Learning slots fall through, not go short. */
    @Test
    fun `Learning slots fall through to Mastered once the Learning Cards run out`() {
        val deck = listOf(learning(1), learning(2)) + (101..110).map { mastered(it) }

        val ids = composeSession(deck, length = 5).map { it.id }

        assertEquals(listOf(1L, 2L, 101L, 102L, 103L), ids)
    }

    /** The Mastered slot falls through too, rather than leaving a hole at position five. */
    @Test
    fun `the fifth slot falls through to Learning when nothing is Mastered`() {
        val deck = (1..6).map { learning(it) }

        val ids = composeSession(deck, length = 6).map { it.id }

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), ids)
    }

    @Test
    fun `a Deck smaller than the Session length gives every Card it has, once`() {
        val deck = listOf(learning(1), learning(2), mastered(101))

        val ids = composeSession(deck, length = 5).map { it.id }

        assertEquals(listOf(1L, 2L, 101L), ids)
    }

    @Test
    fun `an empty Deck yields an empty Session`() {
        assertEquals(emptyList<Card>(), composeSession(emptyList(), length = 5))
    }

    /** Ordering is by the Card set alone: same Cards in, same Session out, every time. */
    @Test
    fun `the same Deck composes the same Session however the Cards arrive`() {
        val deck = (1..12).map { learning(it, streak = it % 3) } + (101..104).map { mastered(it) }

        val first = composeSession(deck, length = 10).map { it.id }
        val second = composeSession(deck.reversed(), length = 10).map { it.id }

        assertEquals(first, second)
    }

    private fun learning(id: Int, streak: Int = 0, seenAt: Instant? = null) = card(
        id = id,
        streak = streak.also { require(it < Card.MASTERY_THRESHOLD) },
        seenAt = seenAt,
    )

    private fun mastered(id: Int, seenAt: Instant? = null) =
        card(id = id, streak = Card.MASTERY_THRESHOLD, seenAt = seenAt)

    private fun card(id: Int, streak: Int, seenAt: Instant?) = Card(
        id = id.toLong(),
        deckId = 1L,
        front = "Front $id",
        back = "Back $id",
        masteryStreak = streak,
        lastSeenAt = seenAt,
        createdAt = clock.instant(),
    )

    private fun minutesIn(minutes: Long) = MutableClock.START.plus(Duration.ofMinutes(minutes))
}
