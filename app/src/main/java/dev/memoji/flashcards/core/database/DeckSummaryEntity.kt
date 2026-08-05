package dev.memoji.flashcards.core.database

import androidx.room.ColumnInfo

/**
 * What one row of [DeckDao.observeSummaries] holds. Not a table — the counts are computed by
 * the query, so there is nowhere for them to fall out of step with the Cards they count.
 */
data class DeckSummaryEntity(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "card_count") val cardCount: Int,
    @ColumnInfo(name = "mastered_count") val masteredCount: Int,
    /** Null for a Deck that has never been studied. */
    @ColumnInfo(name = "last_studied_at") val lastStudiedAt: Long?,
)
