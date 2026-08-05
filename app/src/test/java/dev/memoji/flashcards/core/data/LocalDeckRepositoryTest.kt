package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.database.FlashcardsDatabase
import dev.memoji.flashcards.core.database.inMemoryDatabase
import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.DeckSummary
import dev.memoji.flashcards.core.model.Grade
import dev.memoji.flashcards.core.testing.MutableClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The repository is tested over a real in-memory Room database rather than a stand-in DAO —
 * the mapping between row and Deck is most of what it does, and a fake would not exercise it.
 */
@RunWith(RobolectricTestRunner::class)
class LocalDeckRepositoryTest {

    private lateinit var database: FlashcardsDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: DeckRepository
    private lateinit var cardRepository: CardRepository

    @Before
    fun openDatabase() {
        database = inMemoryDatabase()
        clock = MutableClock()
        repository = LocalDeckRepository(database.deckDao(), database.cardDao(), clock)
        cardRepository = LocalCardRepository(database.cardDao(), clock)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a created Deck shows up with its name and the time it was created`() = runTest {
        repository.createDeck("Big-O notation")

        val deck = repository.decks().single()

        assertEquals("Big-O notation", deck.name)
        assertEquals(MutableClock.START, deck.createdAt)
    }

    @Test
    fun `surrounding whitespace is trimmed off a new name`() = runTest {
        repository.createDeck("  Git basics\n")

        assertEquals("Git basics", repository.decks().single().name)
    }

    @Test
    fun `Decks come back newest first`() = runTest {
        repository.createDeck("First")
        clock.advanceOneMinute()
        repository.createDeck("Second")

        val names = repository.decks().map { it.name }

        assertEquals(listOf("Second", "First"), names)
    }

    @Test
    fun `renaming a Deck replaces its name and keeps its place`() = runTest {
        val id = repository.createDeck("Big-O notaton")
        clock.advanceOneMinute()
        repository.createDeck("Git basics")

        repository.renameDeck(id, "  Big-O notation  ")

        val decks = repository.decks()
        assertEquals(listOf("Git basics", "Big-O notation"), decks.map { it.name })
    }

    @Test
    fun `one Deck can be watched on its own`() = runTest {
        val id = repository.createDeck("Big-O notation")
        repository.createDeck("Git basics")

        assertEquals("Big-O notation", repository.observeDeck(id).first()?.name)
    }

    /** How the Deck detail screen learns the Deck it is showing has been deleted. */
    @Test
    fun `watching a Deck that is not there reads as null`() = runTest {
        val id = repository.createDeck("Doomed")

        repository.deleteDeck(id)

        assertEquals(null, repository.observeDeck(id).first())
        assertEquals(null, repository.observeDeck(404L).first())
    }

    @Test
    fun `a deleted Deck is gone`() = runTest {
        val id = repository.createDeck("Doomed")

        repository.deleteDeck(id)

        assertEquals(emptyList<DeckSummary>(), repository.observeDeckSummaries().first())
    }

    @Test
    fun `a Deck counts its Cards and how many of them are Mastered`() = runTest {
        val deckId = repository.createDeck("Big-O notation")
        val mastered = cardRepository.createCard(deckId, "O(1)", "Constant time.")
        cardRepository.createCard(deckId, "O(n)", "Linear time.")
        repeat(Card.MASTERY_THRESHOLD) { cardRepository.recordGrade(mastered, Grade.KNEW_IT) }

        val summary = repository.observeDeckSummaries().first().single()

        assertEquals(2, summary.cardCount)
        assertEquals(1, summary.masteredCount)
        assertEquals(1, summary.learningCount)
    }

    @Test
    fun `a Deck with no Cards counts zero rather than dropping out of the list`() = runTest {
        repository.createDeck("Big-O notation")

        val summary = repository.observeDeckSummaries().first().single()

        assertEquals(0, summary.cardCount)
        assertEquals(0, summary.masteredCount)
        assertEquals(null, summary.lastStudiedAt)
    }

    /** When the Deck was last studied is when the most recent of its Cards was last seen. */
    @Test
    fun `a Deck was last studied when its most recently seen Card was seen`() = runTest {
        val deckId = repository.createDeck("Big-O notation")
        val first = cardRepository.createCard(deckId, "O(1)", "Constant time.")
        val second = cardRepository.createCard(deckId, "O(n)", "Linear time.")
        cardRepository.recordGrade(second, Grade.KNEW_IT)
        clock.advanceOneMinute()
        cardRepository.recordGrade(first, Grade.AGAIN)

        val summary = repository.observeDeckSummaries().first().single()

        assertEquals(MutableClock.START.plusSeconds(60), summary.lastStudiedAt)
    }

    private suspend fun DeckRepository.decks() =
        observeDeckSummaries().first().map(DeckSummary::deck)
}
