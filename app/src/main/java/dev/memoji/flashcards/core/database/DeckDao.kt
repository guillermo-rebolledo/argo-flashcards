package dev.memoji.flashcards.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    /**
     * Newest first, so a Deck the user just made is at the top where they are looking. `id`
     * breaks ties, because two Decks created in the same millisecond must not swap places
     * between reads.
     */
    @Query("SELECT * FROM decks ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<DeckEntity>>

    /** Emits null once the Deck is deleted, so a screen showing it knows to leave. */
    @Query("SELECT * FROM decks WHERE id = :id")
    fun observeById(id: Long): Flow<DeckEntity?>

    /** Returns the id Room assigned to the new row. */
    @Insert
    suspend fun insert(deck: DeckEntity): Long

    @Query("UPDATE decks SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun delete(id: Long)
}
