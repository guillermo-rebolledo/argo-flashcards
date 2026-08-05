package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Grade
import kotlinx.coroutines.flow.Flow

/**
 * The Cards in a Deck. As with Decks there is nothing behind this but the database, so a
 * delete here is final.
 */
interface CardRepository {

    /** Every Card in [deckId], newest first, re-emitted whenever any of them changes. */
    fun observeCards(deckId: Long): Flow<List<Card>>

    /**
     * Every Card in [deckId], read once. A Session is composed from the Deck as it stands when
     * it starts and does not change under the user while they are part-way through it.
     */
    suspend fun cardsInDeck(deckId: Long): List<Card>

    /**
     * Creates a Card in [deckId] with both sides trimmed, and returns its id. A new Card starts
     * with a Mastery streak of zero, which is to say Learning.
     */
    suspend fun createCard(deckId: Long, front: String, back: String): Long

    /** Replaces what the Card says. Its Mastery streak is not part of what the user edited. */
    suspend fun updateCard(id: Long, front: String, back: String)

    suspend fun deleteCard(id: Long)

    /**
     * Applies [grade] to the Card's Mastery streak and stamps it as seen now. This is the only
     * way a streak ever moves, and the only place the app writes a last-seen time.
     */
    suspend fun recordGrade(id: Long, grade: Grade)
}
