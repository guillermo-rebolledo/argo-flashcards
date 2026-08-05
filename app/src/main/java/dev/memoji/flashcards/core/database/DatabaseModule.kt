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
        ).build()

    @Provides
    fun provideDeckDao(database: FlashcardsDatabase): DeckDao = database.deckDao()

    /** Injected rather than read statically so tests can decide what "now" is. */
    @Provides
    fun provideClock(): Clock = Clock.systemUTC()
}
