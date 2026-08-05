package dev.memoji.flashcards.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per Session, written once when the sitting ends and never updated. The Progress screen
 * queries these rows for every figure it shows rather than reading counters kept alongside them,
 * so there is nothing that can fall out of step with them.
 *
 * The Deck reference nulls out rather than cascading: deleting a Deck removes its Cards, but the
 * afternoon the user spent on it still happened, and a day must not drop out of their streak
 * because of a tidy-up months later.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("deck_id")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "deck_id") val deckId: Long?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "cards_reviewed") val cardsReviewed: Int,
    @ColumnInfo(name = "knew_it") val knewIt: Int,
)
