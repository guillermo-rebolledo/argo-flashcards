package dev.memoji.flashcards.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Someone already has an older version of this database on their phone, with Decks in it that no
 * reinstall would bring back. These open a real file at each shipped version — written by hand,
 * not by Room — and check that the app comes up on it. Room verifies the migrated schema against
 * the one it expects while opening, so a migration that drifts from the entities fails here.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `Decks from version 1 survive the upgrade, and Cards can be written after it`() = runTest {
        writeVersion1Database()

        val database = Room.databaseBuilder(context, FlashcardsDatabase::class.java, DATABASE_NAME)
            .addMigrations(*FlashcardsDatabase.MIGRATIONS)
            .build()

        try {
            val deck = database.deckDao().observeAll().first().single()
            assertEquals("Big-O notation", deck.name)
            assertEquals(1_000L, deck.createdAt)

            database.cardDao().insert(
                CardEntity(
                    deckId = deck.id,
                    front = "O(1)",
                    back = "Constant time.",
                    createdAt = 2_000L,
                ),
            )
            assertEquals(
                listOf("O(1)"),
                database.cardDao().observeByDeck(deck.id).first().map { it.front },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `the cascade is in place on a migrated database, not only on a fresh one`() = runTest {
        writeVersion1Database()

        val database = Room.databaseBuilder(context, FlashcardsDatabase::class.java, DATABASE_NAME)
            .addMigrations(*FlashcardsDatabase.MIGRATIONS)
            .build()

        try {
            val deckId = database.deckDao().observeAll().first().single().id
            database.cardDao().insert(
                CardEntity(deckId = deckId, front = "O(1)", back = "b", createdAt = 2_000L),
            )

            database.deckDao().delete(deckId)

            assertEquals(
                emptyList<CardEntity>(),
                database.cardDao().observeByDeck(deckId).first(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `a version 2 install keeps its Decks and Cards, and can record Sessions after`() =
        runTest {
            writeVersion2Database()

            val database =
                Room.databaseBuilder(context, FlashcardsDatabase::class.java, DATABASE_NAME)
                    .addMigrations(*FlashcardsDatabase.MIGRATIONS)
                    .build()

            try {
                val deck = database.deckDao().observeAll().first().single()
                assertEquals("O(1)", database.cardDao().getByDeck(deck.id).single().front)

                database.sessionDao().insert(
                    SessionEntity(
                        deckId = deck.id,
                        startedAt = 3_000L,
                        endedAt = 4_000L,
                        cardsReviewed = 5,
                        knewIt = 3,
                    ),
                )
                assertEquals(
                    listOf(5),
                    database.sessionDao().observeAll().first().map { it.cardsReviewed },
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun `the Session log survives the Deck it was studied on being deleted`() = runTest {
        writeVersion2Database()

        val database = Room.databaseBuilder(context, FlashcardsDatabase::class.java, DATABASE_NAME)
            .addMigrations(*FlashcardsDatabase.MIGRATIONS)
            .build()

        try {
            val deckId = database.deckDao().observeAll().first().single().id
            database.sessionDao().insert(
                SessionEntity(
                    deckId = deckId,
                    startedAt = 3_000L,
                    endedAt = 4_000L,
                    cardsReviewed = 5,
                    knewIt = 3,
                ),
            )

            database.deckDao().delete(deckId)

            // `ON DELETE SET NULL` on a migrated database, not only on a fresh one: the row is
            // still there and no longer points at anything.
            assertEquals(
                listOf(null),
                database.sessionDao().observeAll().first().map { it.deckId },
            )
        } finally {
            database.close()
        }
    }

    /**
     * The version 1 schema as Room exported it, plus the identity row Room checks on open. Room
     * refuses to open a database it cannot verify, so the hash has to be the real one.
     */
    private fun writeVersion1Database() {
        val db = openBlankDatabase()
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `decks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                " `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
        )
        db.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
            arrayOf(VERSION_1_IDENTITY_HASH),
        )
        db.execSQL(
            "INSERT INTO decks (name, created_at) VALUES (?, ?)",
            arrayOf<Any>("Big-O notation", 1_000L),
        )
        db.version = 1
        db.close()
    }

    /** The same, one version on: a Deck with a Card in it, as the app shipped it. */
    private fun writeVersion2Database() {
        val db = openBlankDatabase()
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `decks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                " `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                " `deck_id` INTEGER NOT NULL, `front` TEXT NOT NULL, `back` TEXT NOT NULL," +
                " `mastery_streak` INTEGER NOT NULL, `last_seen_at` INTEGER," +
                " `created_at` INTEGER NOT NULL, FOREIGN KEY(`deck_id`) REFERENCES `decks`(`id`)" +
                " ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_deck_id` ON `cards` (`deck_id`)")
        db.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
            arrayOf(VERSION_2_IDENTITY_HASH),
        )
        db.execSQL(
            "INSERT INTO decks (name, created_at) VALUES (?, ?)",
            arrayOf<Any>("Big-O notation", 1_000L),
        )
        db.execSQL(
            "INSERT INTO cards (deck_id, front, back, mastery_streak, created_at)" +
                " VALUES (1, 'O(1)', 'Constant time.', 0, 2000)",
        )
        db.version = 2
        db.close()
    }

    /** A fresh file with the identity table Room looks for and nothing else. */
    private fun openBlankDatabase(): SQLiteDatabase {
        val file = context.getDatabasePath(DATABASE_NAME)
        file.parentFile?.mkdirs()
        file.delete()

        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS room_master_table" +
                " (id INTEGER PRIMARY KEY, identity_hash TEXT)",
        )
        return db
    }

    private companion object {
        /** Its own file, so a failure here cannot be a leftover from another test. */
        const val DATABASE_NAME = "migration-test.db"

        /** Copied from `app/schemas/…/1.json`; Room compares it with what it computes. */
        const val VERSION_1_IDENTITY_HASH = "ab667ca0ad129b3be925ace23ee7e700"

        /** Likewise from `2.json` — the version the Session log migrates from. */
        const val VERSION_2_IDENTITY_HASH = "a94a547d075c4b820cac93cf7b421386"
    }
}
