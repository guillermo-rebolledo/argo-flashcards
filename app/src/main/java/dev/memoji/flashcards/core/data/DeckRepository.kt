package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.model.Deck
import kotlinx.coroutines.flow.Flow

/**
 * The app's Decks. There is no network source and nothing to sync — the database behind this
 * is the only record, so a delete here is final.
 */
interface DeckRepository {

    /** Every Deck, newest first, re-emitted whenever any of them changes. */
    fun observeDecks(): Flow<List<Deck>>

    /** One Deck, or null once it is deleted. */
    fun observeDeck(id: Long): Flow<Deck?>

    /** Creates a Deck named [name] with surrounding whitespace trimmed, and returns its id. */
    suspend fun createDeck(name: String): Long

    suspend fun renameDeck(id: Long, name: String)

    suspend fun deleteDeck(id: Long)
}
