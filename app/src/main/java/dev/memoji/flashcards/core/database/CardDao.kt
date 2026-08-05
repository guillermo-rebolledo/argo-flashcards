package dev.memoji.flashcards.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    /**
     * Newest first, matching the Deck list, so a Card the user just wrote is at the top where
     * they are looking rather than below the fold. `id` breaks ties between Cards written in
     * the same millisecond, which a Generation writes by the handful.
     */
    @Query("SELECT * FROM cards WHERE deck_id = :deckId ORDER BY created_at DESC, id DESC")
    fun observeByDeck(deckId: Long): Flow<List<CardEntity>>

    /** Returns the id Room assigned to the new row. */
    @Insert
    suspend fun insert(card: CardEntity): Long

    /**
     * Writes the two content fields and nothing else. Editing a Card is fixing what it says,
     * which cannot cost the user the Mastery streak they built on it — so the statement has no
     * way to touch `mastery_streak` or `last_seen_at` even by accident.
     */
    @Query("UPDATE cards SET front = :front, back = :back WHERE id = :id")
    suspend fun updateContent(id: Long, front: String, back: String)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: Long)
}
