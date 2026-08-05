package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.database.FlashcardsDatabase
import dev.memoji.flashcards.core.database.inMemoryDatabase
import dev.memoji.flashcards.core.model.Deck
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

    @Before
    fun openDatabase() {
        database = inMemoryDatabase()
        clock = MutableClock(Instant.parse("2026-08-04T09:00:00Z"))
        repository = LocalDeckRepository(database.deckDao(), clock)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a created Deck shows up with its name and the time it was created`() = runTest {
        repository.createDeck("Big-O notation")

        val deck = repository.observeDecks().first().single()

        assertEquals("Big-O notation", deck.name)
        assertEquals(Instant.parse("2026-08-04T09:00:00Z"), deck.createdAt)
    }

    @Test
    fun `surrounding whitespace is trimmed off a new name`() = runTest {
        repository.createDeck("  Git basics\n")

        assertEquals("Git basics", repository.observeDecks().first().single().name)
    }

    @Test
    fun `Decks come back newest first`() = runTest {
        repository.createDeck("First")
        clock.advanceOneMinute()
        repository.createDeck("Second")

        val names = repository.observeDecks().first().map { it.name }

        assertEquals(listOf("Second", "First"), names)
    }

    @Test
    fun `renaming a Deck replaces its name and keeps its place`() = runTest {
        val id = repository.createDeck("Big-O notaton")
        clock.advanceOneMinute()
        repository.createDeck("Git basics")

        repository.renameDeck(id, "  Big-O notation  ")

        val decks = repository.observeDecks().first()
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

        assertEquals(emptyList<Deck>(), repository.observeDecks().first())
    }

    /** A [Clock] the test moves by hand, so ordering does not depend on how fast it runs. */
    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = now
        fun advanceOneMinute() {
            now = now.plusSeconds(60)
        }
    }
}
