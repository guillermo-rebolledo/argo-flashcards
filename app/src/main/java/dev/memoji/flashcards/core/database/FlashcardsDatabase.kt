package dev.memoji.flashcards.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's single database. Later slices add entities and bump [DATABASE_VERSION] with a
 * migration — there is no cache to fall back on, so destructive migration is never an option.
 */
@Database(
    entities = [DeckEntity::class, CardEntity::class, SessionEntity::class],
    version = FlashcardsDatabase.DATABASE_VERSION,
    exportSchema = true,
)
abstract class FlashcardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao

    abstract fun cardDao(): CardDao

    abstract fun sessionDao(): SessionDao

    companion object {
        const val DATABASE_VERSION = 3
        const val DATABASE_NAME = "flashcards.db"

        /**
         * Adds the Cards table. Written by hand against the schema Room exports, and covered by
         * a test that opens a real version 1 database — an existing install has Decks in it
         * that no reinstall would bring back.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cards` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `deck_id` INTEGER NOT NULL,
                        `front` TEXT NOT NULL,
                        `back` TEXT NOT NULL,
                        `mastery_streak` INTEGER NOT NULL,
                        `last_seen_at` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`deck_id`) REFERENCES `decks`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_deck_id` ON `cards` (`deck_id`)")
            }
        }

        /**
         * Adds the Session log. Nothing existing is touched: an install upgrading to this
         * version has no history to reconstruct, and Progress starts from the next Session.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `deck_id` INTEGER,
                        `started_at` INTEGER NOT NULL,
                        `ended_at` INTEGER NOT NULL,
                        `cards_reviewed` INTEGER NOT NULL,
                        `knew_it` INTEGER NOT NULL,
                        FOREIGN KEY(`deck_id`) REFERENCES `decks`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sessions_deck_id` ON `sessions` (`deck_id`)",
                )
            }
        }

        /** Every migration the app ships, in one place so no builder can forget one. */
        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
