package dev.memoji.flashcards.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's single database. Later slices add entities and bump [DATABASE_VERSION] with a
 * migration — there is no cache to fall back on, so destructive migration is never an option.
 */
@Database(
    entities = [DeckEntity::class],
    version = FlashcardsDatabase.DATABASE_VERSION,
    exportSchema = true,
)
abstract class FlashcardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "flashcards.db"
    }
}
