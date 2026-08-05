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
 * The Deck table is the app's only record of a Deck, so these run against a real Room database
 * held in memory rather than a stand-in. No mocking library is involved anywhere below.
 */
@RunWith(RobolectricTestRunner::class)
class DeckDaoTest {

    private lateinit var database: FlashcardsDatabase
    private lateinit var dao: DeckDao

    @Before
    fun openDatabase() {
        database = inMemoryDatabase()
        dao = database.deckDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an inserted Deck comes back in the list`() = runTest {
        dao.insert(deckEntity(name = "Big-O notation"))

        val decks = dao.observeAll().first()

        assertEquals(listOf("Big-O notation"), decks.map { it.name })
    }

    @Test
    fun `insert hands back the id Room assigned`() = runTest {
        val id = dao.insert(deckEntity(name = "Git basics"))

        assertTrue("expected a generated id, got $id", id > 0)
        assertEquals(id, dao.observeAll().first().single().id)
    }

    @Test
    fun `the list is newest first`() = runTest {
        dao.insert(deckEntity(name = "Oldest", createdAt = 1_000L))
        dao.insert(deckEntity(name = "Newest", createdAt = 3_000L))
        dao.insert(deckEntity(name = "Middle", createdAt = 2_000L))

        val decks = dao.observeAll().first()

        assertEquals(listOf("Newest", "Middle", "Oldest"), decks.map { it.name })
    }

    @Test
    fun `Decks created in the same millisecond still have a stable order`() = runTest {
        val first = dao.insert(deckEntity(name = "First", createdAt = 1_000L))
        val second = dao.insert(deckEntity(name = "Second", createdAt = 1_000L))

        val decks = dao.observeAll().first()

        assertEquals(listOf(second, first), decks.map { it.id })
    }

    @Test
    fun `renaming a Deck changes its name and leaves the rest alone`() = runTest {
        val id = dao.insert(deckEntity(name = "Big-O notaton", createdAt = 1_000L))

        dao.rename(id, "Big-O notation")

        val deck = dao.observeAll().first().single()
        assertEquals("Big-O notation", deck.name)
        assertEquals(1_000L, deck.createdAt)
    }

    @Test
    fun `renaming an id that is not there changes nothing`() = runTest {
        dao.insert(deckEntity(name = "Git basics"))

        dao.rename(id = 404L, name = "Ghost")

        assertEquals(listOf("Git basics"), dao.observeAll().first().map { it.name })
    }

    @Test
    fun `deleting a Deck removes only that Deck`() = runTest {
        val doomed = dao.insert(deckEntity(name = "Doomed", createdAt = 1_000L))
        dao.insert(deckEntity(name = "Survivor", createdAt = 2_000L))

        dao.delete(doomed)

        assertEquals(listOf("Survivor"), dao.observeAll().first().map { it.name })
    }

    @Test
    fun `the list starts empty`() = runTest {
        assertEquals(emptyList<DeckEntity>(), dao.observeAll().first())
    }

    private fun deckEntity(name: String, createdAt: Long = 1_000L) =
        DeckEntity(name = name, createdAt = createdAt)
}
