package dev.memoji.flashcards.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Deck as it is stored. Room is not a cache here — this row is the only record of the Deck,
 * so nothing about it is recoverable once it is deleted.
 */
@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
