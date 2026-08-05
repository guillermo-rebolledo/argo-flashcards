package dev.memoji.flashcards.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Card as it is stored. The Deck foreign key cascades on delete, so a Deck and its Cards go
 * together in one statement — nothing can leave a Card pointing at a Deck that is gone.
 *
 * There is no `mastered` column: it is derived from [masteryStreak] at read time.
 */
@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deck_id")],
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "deck_id") val deckId: Long,
    @ColumnInfo(name = "front") val front: String,
    @ColumnInfo(name = "back") val back: String,
    @ColumnInfo(name = "mastery_streak") val masteryStreak: Int = 0,
    /** Null until the Card has been through a Review. */
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
