package dev.memoji.flashcards.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.memoji.flashcards.core.model.Card
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Someone already has version 1 of this database on their phone with Decks in it that no
 * reinstall would bring back. This opens a real version 1 file — written by hand, not by Room —
 * and checks that the app comes up on it. Room verifies the migrated schema against the one it
 * expects while opening, so a migration that drifts from the entities fails here.
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
            val deck = database.deckDao().observeSummaries(Card.MASTERY_THRESHOLD).first().single()
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
            val deckId = database.deckDao().observeSummaries(Card.MASTERY_THRESHOLD).first().single().id
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

    /**
     * The version 1 schema as Room exported it, plus the identity row Room checks on open. Room
     * refuses to open a database it cannot verify, so the hash has to be the real one.
     */
    private fun writeVersion1Database() {
        val file = context.getDatabasePath(DATABASE_NAME)
        file.parentFile?.mkdirs()
        file.delete()

        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `decks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                " `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS room_master_table" +
                " (id INTEGER PRIMARY KEY, identity_hash TEXT)",
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

    private companion object {
        /** Its own file, so a failure here cannot be a leftover from another test. */
        const val DATABASE_NAME = "migration-test.db"

        /** Copied from `app/schemas/…/1.json`; Room compares it with what it computes. */
        const val VERSION_1_IDENTITY_HASH = "ab667ca0ad129b3be925ace23ee7e700"
    }
}
