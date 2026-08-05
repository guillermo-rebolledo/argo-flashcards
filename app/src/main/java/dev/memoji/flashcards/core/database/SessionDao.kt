package dev.memoji.flashcards.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /**
     * Every Session ever recorded, oldest first. Not a sum, a count, or a window — the streak
     * has no fixed length to ask for, and the arithmetic on top of these rows is written once
     * in Kotlin rather than once per figure in SQL. A user who studies daily for a decade has
     * a few thousand rows of six numbers.
     */
    @Query("SELECT * FROM sessions ORDER BY started_at, id")
    fun observeAll(): Flow<List<SessionEntity>>

    @Insert
    suspend fun insert(session: SessionEntity): Long
}
