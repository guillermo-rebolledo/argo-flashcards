package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.model.Card
import kotlinx.coroutines.flow.Flow

/**
 * The Cards in a Deck. As with Decks there is nothing behind this but the database, so a
 * delete here is final.
 */
interface CardRepository {

    /** Every Card in [deckId], newest first, re-emitted whenever any of them changes. */
    fun observeCards(deckId: Long): Flow<List<Card>>

    /**
     * Creates a Card in [deckId] with both sides trimmed, and returns its id. A new Card starts
     * with a Mastery streak of zero, which is to say Learning.
     */
    suspend fun createCard(deckId: Long, front: String, back: String): Long

    /** Replaces what the Card says. Its Mastery streak is not part of what the user edited. */
    suspend fun updateCard(id: Long, front: String, back: String)

    suspend fun deleteCard(id: Long)
}
