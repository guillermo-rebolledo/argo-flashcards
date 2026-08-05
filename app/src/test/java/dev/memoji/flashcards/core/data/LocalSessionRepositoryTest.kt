package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.database.FlashcardsDatabase
import dev.memoji.flashcards.core.database.inMemoryDatabase
import dev.memoji.flashcards.core.model.Session
import dev.memoji.flashcards.core.testing.MutableClock
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Over a real database, because what this slice adds is a table: the Deck reference nulling out
 * rather than cascading is a property of the schema, and a fake DAO would agree with whatever
 * the test expected.
 */
@RunWith(RobolectricTestRunner::class)
class LocalSessionRepositoryTest {

    private lateinit var database: FlashcardsDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: SessionRepository
    private lateinit var deckRepository: DeckRepository
    private var deckId = 0L

    @Before
    fun openDatabase() = runTest {
        database = inMemoryDatabase()
        clock = MutableClock()
        repository = LocalSessionRepository(database.sessionDao(), clock)
        deckRepository = LocalDeckRepository(database.deckDao(), database.cardDao(), clock)
        deckId = deckRepository.createDeck("Big-O notation")
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a recorded Session comes back with what was studied and when`() = runTest {
        val started = clock.instant()
        clock.advance(Duration.ofMinutes(2))

        repository.recordSession(deckId, started, cardsReviewed = 5, knewIt = 3)

        val session = repository.observeSessions().first().single()
        assertEquals(deckId, session.deckId)
        assertEquals(started, session.started)
        assertEquals(clock.instant(), session.ended)
        assertEquals(5, session.cardsReviewed)
        assertEquals(3, session.knewIt)
        assertEquals(Duration.ofMinutes(2), session.duration)
    }

    @Test
    fun `Sessions come back oldest first, whatever order they were written in`() = runTest {
        val second = clock.instant()
        clock.advance(Duration.ofMinutes(1))
        repository.recordSession(deckId, second, cardsReviewed = 5, knewIt = 5)

        val first = second.minus(Duration.ofDays(1))
        repository.recordSession(deckId, first, cardsReviewed = 3, knewIt = 1)

        assertEquals(
            listOf(first, second),
            repository.observeSessions().first().map(Session::started),
        )
    }

    @Test
    fun `deleting a Deck keeps the Sessions studied on it, without the Deck`() = runTest {
        val started = clock.instant()
        clock.advanceOneMinute()
        repository.recordSession(deckId, started, cardsReviewed = 5, knewIt = 3)

        deckRepository.deleteDeck(deckId)

        // The Deck is gone, but the day the user studied is not: the streak is theirs, not the
        // Deck's, and a tidy-up months later must not take a day out of it.
        val session = repository.observeSessions().first().single()
        assertNull(session.deckId)
        assertEquals(5, session.cardsReviewed)
    }

    @Test
    fun `a Session left open on a backgrounded phone reports the ceiling, not the wait`() =
        runTest {
            val started = clock.instant()
            clock.advance(Duration.ofHours(8))

            repository.recordSession(deckId, started, cardsReviewed = 5, knewIt = 3)

            val session = repository.observeSessions().first().single()
            assertEquals(Session.MAX_DURATION, session.duration)
        }
}
