package dev.memoji.flashcards.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Cards table is the app's only record of a Card, so these run against a real Room database
 * held in memory rather than a stand-in. No mocking library is involved anywhere below.
 */
@RunWith(RobolectricTestRunner::class)
class CardDaoTest {

    private lateinit var database: FlashcardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var dao: CardDao
    private var deckId = 0L

    @Before
    fun openDatabase() {
        database = inMemoryDatabase()
        deckDao = database.deckDao()
        dao = database.cardDao()
        deckId = 0L
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun deck(name: String = "Big-O notation"): Long =
        deckDao.insert(DeckEntity(name = name, createdAt = 1_000L))

    @Test
    fun `an inserted Card comes back in its Deck`() = runTest {
        deckId = deck()

        dao.insert(cardEntity(front = "O(1)", back = "Constant time."))

        val cards = dao.observeByDeck(deckId).first()
        assertEquals(listOf("O(1)"), cards.map { it.front })
        assertEquals(listOf("Constant time."), cards.map { it.back })
    }

    @Test
    fun `insert hands back the id Room assigned`() = runTest {
        deckId = deck()

        val id = dao.insert(cardEntity(front = "O(n)", back = "Linear time."))

        assertTrue("expected a generated id, got $id", id > 0)
        assertEquals(id, dao.observeByDeck(deckId).first().single().id)
    }

    @Test
    fun `a new Card starts with no Mastery streak and has never been seen`() = runTest {
        deckId = deck()

        dao.insert(cardEntity(front = "O(n)", back = "Linear time."))

        val card = dao.observeByDeck(deckId).first().single()
        assertEquals(0, card.masteryStreak)
        assertEquals(null, card.lastSeenAt)
    }

    @Test
    fun `the list is newest first`() = runTest {
        deckId = deck()
        dao.insert(cardEntity(front = "Oldest", back = "b", createdAt = 1_000L))
        dao.insert(cardEntity(front = "Newest", back = "b", createdAt = 3_000L))
        dao.insert(cardEntity(front = "Middle", back = "b", createdAt = 2_000L))

        val cards = dao.observeByDeck(deckId).first()

        assertEquals(listOf("Newest", "Middle", "Oldest"), cards.map { it.front })
    }

    @Test
    fun `Cards written in the same millisecond still have a stable order`() = runTest {
        deckId = deck()
        val first = dao.insert(cardEntity(front = "First", back = "b", createdAt = 1_000L))
        val second = dao.insert(cardEntity(front = "Second", back = "b", createdAt = 1_000L))

        val cards = dao.observeByDeck(deckId).first()

        assertEquals(listOf(second, first), cards.map { it.id })
    }

    @Test
    fun `a Deck only lists its own Cards`() = runTest {
        deckId = deck("Big-O notation")
        dao.insert(cardEntity(front = "O(1)", back = "Constant time."))
        val otherDeck = deck("Git basics")
        dao.insert(cardEntity(deckId = otherDeck, front = "rebase", back = "Replays commits."))

        assertEquals(listOf("O(1)"), dao.observeByDeck(deckId).first().map { it.front })
        assertEquals(listOf("rebase"), dao.observeByDeck(otherDeck).first().map { it.front })
    }

    @Test
    fun `editing a Card replaces both sides`() = runTest {
        deckId = deck()
        val id = dao.insert(cardEntity(front = "O(1)", back = "Constant tme."))

        dao.updateContent(id, front = "O(1)", back = "Constant time.")

        val card = dao.observeByDeck(deckId).first().single()
        assertEquals("O(1)", card.front)
        assertEquals("Constant time.", card.back)
    }

    /**
     * The point of the whole slice: fixing a typo cannot cost the user progress. There are no
     * Sessions yet to make this visible, which is exactly why it is pinned down now.
     */
    @Test
    fun `editing a Card leaves its Mastery streak and last-seen alone`() = runTest {
        deckId = deck()
        val id = dao.insert(
            cardEntity(front = "O(1)", back = "Constant tme.", masteryStreak = 4, lastSeenAt = 99L),
        )

        dao.updateContent(id, front = "O(1)", back = "Constant time.")

        val card = dao.observeByDeck(deckId).first().single()
        assertEquals(4, card.masteryStreak)
        assertEquals(99L, card.lastSeenAt)
    }

    @Test
    fun `editing an id that is not there changes nothing`() = runTest {
        deckId = deck()
        dao.insert(cardEntity(front = "O(1)", back = "Constant time."))

        dao.updateContent(id = 404L, front = "Ghost", back = "Ghost")

        assertEquals(listOf("O(1)"), dao.observeByDeck(deckId).first().map { it.front })
    }

    @Test
    fun `deleting a Card removes only that Card`() = runTest {
        deckId = deck()
        val doomed = dao.insert(cardEntity(front = "Doomed", back = "b", createdAt = 1_000L))
        dao.insert(cardEntity(front = "Survivor", back = "b", createdAt = 2_000L))

        dao.delete(doomed)

        assertEquals(listOf("Survivor"), dao.observeByDeck(deckId).first().map { it.front })
    }

    @Test
    fun `deleting a Deck takes its Cards with it`() = runTest {
        deckId = deck("Doomed")
        dao.insert(cardEntity(front = "O(1)", back = "Constant time."))
        dao.insert(cardEntity(front = "O(n)", back = "Linear time."))
        val survivor = deck("Git basics")
        dao.insert(cardEntity(deckId = survivor, front = "rebase", back = "Replays commits."))

        deckDao.delete(deckId)

        assertEquals(emptyList<CardEntity>(), dao.observeByDeck(deckId).first())
        assertEquals(listOf("rebase"), dao.observeByDeck(survivor).first().map { it.front })
    }

    @Test
    fun `a Deck starts with no Cards`() = runTest {
        deckId = deck()

        assertEquals(emptyList<CardEntity>(), dao.observeByDeck(deckId).first())
    }

    private fun cardEntity(
        front: String,
        back: String,
        deckId: Long = this.deckId,
        masteryStreak: Int = 0,
        lastSeenAt: Long? = null,
        createdAt: Long = 1_000L,
    ) = CardEntity(
        deckId = deckId,
        front = front,
        back = back,
        masteryStreak = masteryStreak,
        lastSeenAt = lastSeenAt,
        createdAt = createdAt,
    )
}
