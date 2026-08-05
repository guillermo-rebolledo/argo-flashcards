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

    /**
     * Every Card in every Deck. The Deck list counts its Cards from this rather than from a
     * `COUNT` in SQL, so Mastered stays derived in the one place Kotlin derives it.
     */
    @Query("SELECT * FROM cards")
    fun observeAll(): Flow<List<CardEntity>>

    /** A one-shot read of the whole Deck, which a Session is composed from. */
    @Query("SELECT * FROM cards WHERE deck_id = :deckId")
    suspend fun getByDeck(deckId: Long): List<CardEntity>

    /** Returns the id Room assigned to the new row. */
    @Insert
    suspend fun insert(card: CardEntity): Long

    /**
     * The two halves of the learning model, one statement each. Both are relative to the row
     * rather than to a streak the caller read first, so two Grades in flight at once cannot
     * write each other's arithmetic — and neither can be told a streak that is out of date.
     */
    @Query(
        "UPDATE cards SET mastery_streak = mastery_streak + 1, last_seen_at = :seenAt " +
            "WHERE id = :id",
    )
    suspend fun recordKnewIt(id: Long, seenAt: Long)

    /**
     * Zero, not a decrement: one `Again` says the Card did not come back, whatever the run
     * before it was. This is also what returns a Mastered Card to Learning.
     */
    @Query("UPDATE cards SET mastery_streak = 0, last_seen_at = :seenAt WHERE id = :id")
    suspend fun recordAgain(id: Long, seenAt: Long)

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
