package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.database.FlashcardsDatabase
import dev.memoji.flashcards.core.database.inMemoryDatabase
import dev.memoji.flashcards.core.model.Card
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Run over a real in-memory Room database rather than a stand-in DAO — the mapping between row
 * and Card is most of what this does, and Mastered being derived only shows up in that mapping.
 */
@RunWith(RobolectricTestRunner::class)
class LocalCardRepositoryTest {

    private lateinit var database: FlashcardsDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: CardRepository
    private lateinit var deckRepository: DeckRepository
    private var deckId = 0L

    @Before
    fun openDatabase() = runTest {
        database = inMemoryDatabase()
        clock = MutableClock(Instant.parse("2026-08-04T09:00:00Z"))
        repository = LocalCardRepository(database.cardDao(), clock)
        deckRepository = LocalDeckRepository(database.deckDao(), clock)
        deckId = deckRepository.createDeck("Big-O notation")
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a created Card shows up with both sides and the time it was written`() = runTest {
        repository.createCard(deckId, "O(1)", "Constant time.")

        val card = repository.observeCards(deckId).first().single()

        assertEquals(deckId, card.deckId)
        assertEquals("O(1)", card.front)
        assertEquals("Constant time.", card.back)
        assertEquals(Instant.parse("2026-08-04T09:00:00Z"), card.createdAt)
    }

    @Test
    fun `surrounding whitespace is trimmed off both sides`() = runTest {
        repository.createCard(deckId, "  O(n)\n", "\tLinear time.  ")

        val card = repository.observeCards(deckId).first().single()

        assertEquals("O(n)", card.front)
        assertEquals("Linear time.", card.back)
    }

    @Test
    fun `a new Card is Learning and has never been seen`() = runTest {
        repository.createCard(deckId, "O(1)", "Constant time.")

        val card = repository.observeCards(deckId).first().single()

        assertEquals(0, card.masteryStreak)
        assertEquals(null, card.lastSeenAt)
        assertFalse(card.isMastered)
    }

    @Test
    fun `Cards come back newest first`() = runTest {
        repository.createCard(deckId, "First", "b")
        clock.advanceOneMinute()
        repository.createCard(deckId, "Second", "b")

        val fronts = repository.observeCards(deckId).first().map { it.front }

        assertEquals(listOf("Second", "First"), fronts)
    }

    @Test
    fun `editing a Card replaces what it says`() = runTest {
        val id = repository.createCard(deckId, "O(1)", "Constant tme.")

        repository.updateCard(id, "  O(1)  ", "  Constant time.  ")

        val card = repository.observeCards(deckId).first().single()
        assertEquals("O(1)", card.front)
        assertEquals("Constant time.", card.back)
    }

    @Test
    fun `editing a Card leaves its Mastery streak alone`() = runTest {
        val id = repository.createCard(deckId, "O(1)", "Constant tme.")
        database.gradeForTest(id, masteryStreak = 4, lastSeenAt = 99L)

        repository.updateCard(id, "O(1)", "Constant time.")

        val card = repository.observeCards(deckId).first().single()
        assertEquals(4, card.masteryStreak)
        assertEquals(Instant.ofEpochMilli(99L), card.lastSeenAt)
    }

    @Test
    fun `a Card is Mastered at the threshold and Learning below it`() = runTest {
        val id = repository.createCard(deckId, "O(1)", "Constant time.")

        database.gradeForTest(id, masteryStreak = Card.MASTERY_THRESHOLD - 1)
        assertFalse(repository.observeCards(deckId).first().single().isMastered)

        database.gradeForTest(id, masteryStreak = Card.MASTERY_THRESHOLD)
        assertTrue(repository.observeCards(deckId).first().single().isMastered)
    }

    @Test
    fun `a deleted Card is gone`() = runTest {
        val id = repository.createCard(deckId, "Doomed", "b")

        repository.deleteCard(id)

        assertEquals(emptyList<Card>(), repository.observeCards(deckId).first())
    }

    @Test
    fun `deleting the Deck takes its Cards with it`() = runTest {
        repository.createCard(deckId, "O(1)", "Constant time.")

        deckRepository.deleteDeck(deckId)

        assertEquals(emptyList<Card>(), repository.observeCards(deckId).first())
    }

    /** A [Clock] the test moves by hand, so ordering does not depend on how fast it runs. */
    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
        override fun instant() = now
        fun advanceOneMinute() {
            now = now.plusSeconds(60)
        }
    }
}

/**
 * Sessions are the only thing that will ever move a Mastery streak, and they do not exist yet.
 * Until they do, a test puts one on the row directly — no production code can, which is the
 * point of the tests above.
 */
private fun FlashcardsDatabase.gradeForTest(
    id: Long,
    masteryStreak: Int,
    lastSeenAt: Long? = null,
) {
    openHelper.writableDatabase.execSQL(
        "UPDATE cards SET mastery_streak = ?, last_seen_at = ? WHERE id = ?",
        arrayOf<Any?>(masteryStreak, lastSeenAt, id),
    )
}
