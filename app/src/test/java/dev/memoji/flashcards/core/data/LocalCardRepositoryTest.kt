package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.database.FlashcardsDatabase
import dev.memoji.flashcards.core.database.inMemoryDatabase
import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Grade
import dev.memoji.flashcards.core.testing.MutableClock
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
        clock = MutableClock()
        repository = LocalCardRepository(database.cardDao(), clock)
        deckRepository = LocalDeckRepository(database.deckDao(), database.cardDao(), clock)
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
        assertEquals(MutableClock.START, card.createdAt)
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
        repository.recordGrade(id, Grade.KNEW_IT)
        val seenAt = repository.observeCards(deckId).first().single().lastSeenAt
        clock.advanceOneMinute()

        repository.updateCard(id, "O(1)", "Constant time.")

        val card = repository.observeCards(deckId).first().single()
        assertEquals(1, card.masteryStreak)
        assertEquals(seenAt, card.lastSeenAt)
    }

    @Test
    fun `Knew it lifts the Mastery streak by one and Again drops it to zero`() = runTest {
        val id = repository.createCard(deckId, "O(1)", "Constant time.")

        repository.recordGrade(id, Grade.KNEW_IT)
        assertEquals(1, repository.observeCards(deckId).first().single().masteryStreak)

        repository.recordGrade(id, Grade.KNEW_IT)
        assertEquals(2, repository.observeCards(deckId).first().single().masteryStreak)

        repository.recordGrade(id, Grade.AGAIN)
        assertEquals(0, repository.observeCards(deckId).first().single().masteryStreak)
    }

    @Test
    fun `three consecutive Knew it Grades make a Card Mastered, and two do not`() = runTest {
        val id = repository.createCard(deckId, "O(1)", "Constant time.")

        repeat(Card.MASTERY_THRESHOLD - 1) { repository.recordGrade(id, Grade.KNEW_IT) }
        assertFalse(repository.observeCards(deckId).first().single().isMastered)

        repository.recordGrade(id, Grade.KNEW_IT)
        assertTrue(repository.observeCards(deckId).first().single().isMastered)
    }

    @Test
    fun `Again on a Mastered Card returns it to Learning with a streak of zero`() = runTest {
        val id = repository.createCard(deckId, "O(1)", "Constant time.")
        repeat(Card.MASTERY_THRESHOLD) { repository.recordGrade(id, Grade.KNEW_IT) }

        repository.recordGrade(id, Grade.AGAIN)

        val card = repository.observeCards(deckId).first().single()
        assertEquals(0, card.masteryStreak)
        assertFalse(card.isMastered)
    }

    /** The streak survives a Session ending after it; only a later Grade may move it. */
    @Test
    fun `a Grade on one Card leaves the others alone`() = runTest {
        val graded = repository.createCard(deckId, "O(1)", "Constant time.")
        val untouched = repository.createCard(deckId, "O(n)", "Linear time.")

        repository.recordGrade(graded, Grade.KNEW_IT)

        val card = repository.observeCards(deckId).first().single { it.id == untouched }
        assertEquals(0, card.masteryStreak)
        assertEquals(null, card.lastSeenAt)
    }

    @Test
    fun `both Grades stamp the Card as seen now`() = runTest {
        val knew = repository.createCard(deckId, "O(1)", "Constant time.")
        val again = repository.createCard(deckId, "O(n)", "Linear time.")
        clock.advanceOneMinute()

        repository.recordGrade(knew, Grade.KNEW_IT)
        repository.recordGrade(again, Grade.AGAIN)

        val seen = repository.observeCards(deckId).first().map { it.lastSeenAt }
        assertEquals(listOf(MutableClock.START.plusSeconds(60)), seen.distinct())
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
}
