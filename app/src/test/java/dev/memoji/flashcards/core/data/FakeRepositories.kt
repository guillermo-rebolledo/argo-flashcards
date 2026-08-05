package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Deck
import dev.memoji.flashcards.core.model.DeckSummary
import dev.memoji.flashcards.core.model.Grade
import dev.memoji.flashcards.core.model.Session
import dev.memoji.flashcards.core.model.SessionLength
import dev.memoji.flashcards.core.model.ThemePreference
import dev.memoji.flashcards.core.model.UserSettings
import dev.memoji.flashcards.core.testing.MutableClock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Stand-ins written by hand — the project uses no mocking library. They keep the trimming and
 * the ordering the real repositories promise, so a ViewModel test that depends on either is
 * testing the same thing the app does.
 */
internal class FakeDeckRepository(
    private val cards: FakeCardRepository = FakeCardRepository(),
) : DeckRepository {
    private val decks = MutableStateFlow(emptyList<Deck>())
    private var nextId = 1L

    /**
     * Counted here the way the real query counts them, so a ViewModel test reading a mastery
     * count or a last-studied time is reading what the database would have told it.
     */
    override fun observeDeckSummaries(): Flow<List<DeckSummary>> =
        combine(decks, cards.observeAll()) { decks, allCards ->
            decks.sortedByDescending(Deck::createdAt).map { deck ->
                val inDeck = allCards.filter { it.deckId == deck.id }
                DeckSummary(
                    deck = deck,
                    cardCount = inDeck.size,
                    masteredCount = inDeck.count(Card::isMastered),
                    lastStudiedAt = inDeck.mapNotNull(Card::lastSeenAt).maxOrNull(),
                )
            }
        }

    override fun observeDeck(id: Long): Flow<Deck?> =
        decks.map { list -> list.find { it.id == id } }

    override suspend fun createDeck(name: String): Long {
        val id = nextId++
        decks.value += Deck(id, name.trim(), Instant.ofEpochMilli(id))
        return id
    }

    override suspend fun renameDeck(id: Long, name: String) {
        decks.value = decks.value.map { if (it.id == id) it.copy(name = name.trim()) else it }
    }

    /** The Cards go with it, as the database's `ON DELETE CASCADE` takes them. */
    override suspend fun deleteDeck(id: Long) {
        decks.value = decks.value.filterNot { it.id == id }
        cards.cascadeDeckDelete(id)
    }
}

internal class FakeCardRepository(
    private val clock: MutableClock = MutableClock(),
) : CardRepository {
    private val cards = MutableStateFlow(emptyList<Card>())
    private var nextId = 1L

    override fun observeCards(deckId: Long): Flow<List<Card>> = cards.map { list ->
        list.filter { it.deckId == deckId }.sortedByDescending(Card::createdAt)
    }

    /** Every Card in every Deck, which is what a Deck summary is counted from. */
    fun observeAll(): Flow<List<Card>> = cards

    override suspend fun cardsInDeck(deckId: Long): List<Card> =
        cards.value.filter { it.deckId == deckId }

    override suspend fun createCard(deckId: Long, front: String, back: String): Long {
        val id = nextId++
        cards.value += Card(
            id = id,
            deckId = deckId,
            front = front.trim(),
            back = back.trim(),
            masteryStreak = 0,
            lastSeenAt = null,
            createdAt = Instant.ofEpochMilli(id),
        )
        return id
    }

    /** Writes the two content fields only, as the real `UPDATE` statement does. */
    override suspend fun updateCard(id: Long, front: String, back: String) {
        cards.value = cards.value.map {
            if (it.id == id) it.copy(front = front.trim(), back = back.trim()) else it
        }
    }

    override suspend fun deleteCard(id: Long) {
        cards.value = cards.value.filterNot { it.id == id }
    }

    /** The same arithmetic the real `UPDATE` statements do, against the same clock. */
    override suspend fun recordGrade(id: Long, grade: Grade) {
        cards.value = cards.value.map { card ->
            if (card.id != id) {
                card
            } else {
                card.copy(
                    masteryStreak = when (grade) {
                        Grade.KNEW_IT -> card.masteryStreak + 1
                        Grade.AGAIN -> 0
                    },
                    lastSeenAt = clock.instant(),
                )
            }
        }
    }

    /** Grades the Card `Knew it` until it is Mastered, as a Session would over three sittings. */
    suspend fun master(id: Long) {
        repeat(Card.MASTERY_THRESHOLD) { recordGrade(id, Grade.KNEW_IT) }
    }

    internal fun cascadeDeckDelete(deckId: Long) {
        cards.value = cards.value.filterNot { it.deckId == deckId }
    }
}

/**
 * Keeps the rows in the order the real table hands them back, and stamps the end of a Session
 * off the same clock the real repository reads.
 */
internal class FakeSessionRepository(
    private val clock: MutableClock = MutableClock(),
) : SessionRepository {
    private val sessions = MutableStateFlow(emptyList<Session>())
    private var nextId = 1L

    override fun observeSessions(): Flow<List<Session>> = sessions

    override suspend fun recordSession(
        deckId: Long,
        startedAt: Instant,
        cardsReviewed: Int,
        knewIt: Int,
    ) {
        sessions.value = (
            sessions.value + Session(
                id = nextId++,
                deckId = deckId,
                started = startedAt,
                ended = clock.instant(),
                cardsReviewed = cardsReviewed,
                knewIt = knewIt,
            )
            ).sortedBy(Session::started)
    }
}

internal class FakeSettingsRepository : SettingsRepository {
    private val settings = MutableStateFlow(UserSettings.DEFAULT)

    override fun observeSettings(): Flow<UserSettings> = settings

    override suspend fun setSessionLength(length: SessionLength) {
        settings.value = settings.value.copy(sessionLength = length)
    }

    override suspend fun setTheme(theme: ThemePreference) {
        settings.value = settings.value.copy(theme = theme)
    }

    override suspend fun setReducedMotion(reducedMotion: Boolean) {
        settings.value = settings.value.copy(reducedMotion = reducedMotion)
    }

    override suspend fun setHideStreak(hideStreak: Boolean) {
        settings.value = settings.value.copy(hideStreak = hideStreak)
    }
}
