package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Deck
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Stand-ins written by hand — the project uses no mocking library. They keep the trimming and
 * the ordering the real repositories promise, so a ViewModel test that depends on either is
 * testing the same thing the app does.
 */
internal class FakeDeckRepository : DeckRepository {
    private val decks = MutableStateFlow(emptyList<Deck>())
    private var nextId = 1L

    /** Set by [FakeCardRepository] when the two are wired together, so a delete cascades. */
    var onDeckDeleted: (Long) -> Unit = {}

    override fun observeDecks(): Flow<List<Deck>> =
        decks.map { list -> list.sortedByDescending(Deck::createdAt) }

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

    override suspend fun deleteDeck(id: Long) {
        decks.value = decks.value.filterNot { it.id == id }
        onDeckDeleted(id)
    }
}

internal class FakeCardRepository : CardRepository {
    private val cards = MutableStateFlow(emptyList<Card>())
    private var nextId = 1L

    override fun observeCards(deckId: Long): Flow<List<Card>> = cards.map { list ->
        list.filter { it.deckId == deckId }.sortedByDescending(Card::createdAt)
    }

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

    /** Stands in for a Session having graded the Card, which no code can do yet. */
    fun setMasteryStreak(id: Long, streak: Int) {
        cards.value = cards.value.map { if (it.id == id) it.copy(masteryStreak = streak) else it }
    }

    /** Mirrors the database's `ON DELETE CASCADE`. */
    fun cascadeDeckDelete(deckId: Long) {
        cards.value = cards.value.filterNot { it.deckId == deckId }
    }
}
