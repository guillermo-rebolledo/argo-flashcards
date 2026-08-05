package dev.memoji.flashcards.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Closing the database and opening it again is the closest a JVM test gets to killing the app
 * and starting it back up. Without this, nothing here would notice a Deck that only ever
 * existed in memory.
 */
@RunWith(RobolectricTestRunner::class)
class DeckPersistenceTest {

    @Test
    fun `Decks are still there after the database is closed and opened again`() = runTest {
        val before = onDiskDatabase()
        val kept = before.deckDao().insert(DeckEntity(name = "Big-O notation", createdAt = 1_000L))
        val doomed = before.deckDao().insert(DeckEntity(name = "Doomed", createdAt = 2_000L))
        before.deckDao().rename(kept, "Big-O")
        before.deckDao().delete(doomed)
        before.close()

        val after = onDiskDatabase()
        try {
            val decks = after.deckDao().observeAll().first()

            assertEquals(listOf("Big-O"), decks.map { it.name })
            assertEquals(listOf(kept), decks.map { it.id })
        } finally {
            after.close()
        }
    }
}
