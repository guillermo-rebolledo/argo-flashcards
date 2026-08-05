package dev.memoji.flashcards.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    /**
     * Every Deck, each with what the Deck list and the "Up next" card need to say about
     * it. `LEFT JOIN` so a Deck with no Cards still comes back, counting zero.
     *
     * Newest first, so a Deck the user just made is at the top where they are looking. `id`
     * breaks ties, because two Decks created in the same millisecond must not swap places
     * between reads.
     *
     * [masteryThreshold] is bound from `Card.MASTERY_THRESHOLD` rather than written into the
     * statement: Mastered is derived from the streak in exactly one place, and a number
     * hard-coded here would be a second one that could disagree with it.
     */
    @Query(
        """
        SELECT d.id AS id, d.name AS name, d.created_at AS created_at,
            COUNT(c.id) AS card_count,
            COALESCE(
                SUM(CASE WHEN c.mastery_streak >= :masteryThreshold THEN 1 ELSE 0 END),
                0
            ) AS mastered_count,
            MAX(c.last_seen_at) AS last_studied_at
        FROM decks d LEFT JOIN cards c ON c.deck_id = d.id
        GROUP BY d.id
        ORDER BY d.created_at DESC, d.id DESC
        """,
    )
    fun observeSummaries(masteryThreshold: Int): Flow<List<DeckSummaryEntity>>

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
