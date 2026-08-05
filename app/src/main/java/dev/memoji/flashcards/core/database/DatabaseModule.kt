package dev.memoji.flashcards.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FlashcardsDatabase =
        Room.databaseBuilder(
            context,
            FlashcardsDatabase::class.java,
            FlashcardsDatabase.DATABASE_NAME,
        ).addMigrations(*FlashcardsDatabase.MIGRATIONS).build()

    @Provides
    fun provideDeckDao(database: FlashcardsDatabase): DeckDao = database.deckDao()

    @Provides
    fun provideCardDao(database: FlashcardsDatabase): CardDao = database.cardDao()

    @Provides
    fun provideSessionDao(database: FlashcardsDatabase): SessionDao = database.sessionDao()

    /**
     * Injected rather than read statically so tests can decide what "now" is. The device's own
     * zone, not UTC: a day streak counts the user's days, and someone studying at eleven at
     * night in Santiago has not studied tomorrow.
     */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
