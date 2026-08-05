package dev.memoji.flashcards.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

/** A database that lives only as long as the test holds it open. */
internal fun inMemoryDatabase(): FlashcardsDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        FlashcardsDatabase::class.java,
    ).build()

/**
 * A database on Robolectric's per-test file system, so it can be closed and opened again the
 * way it is across an app restart. In-memory would lose the rows at close and prove nothing.
 */
internal fun onDiskDatabase(): FlashcardsDatabase =
    Room.databaseBuilder(
        ApplicationProvider.getApplicationContext(),
        FlashcardsDatabase::class.java,
        FlashcardsDatabase.DATABASE_NAME,
    ).build()
