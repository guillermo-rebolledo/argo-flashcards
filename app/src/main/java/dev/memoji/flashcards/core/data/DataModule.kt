package dev.memoji.flashcards.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindDeckRepository(repository: LocalDeckRepository): DeckRepository

    @Binds
    @Singleton
    abstract fun bindCardRepository(repository: LocalCardRepository): CardRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(repository: LocalSessionRepository): SessionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(repository: LocalSettingsRepository): SettingsRepository
}
