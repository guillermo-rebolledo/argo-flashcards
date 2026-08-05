package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.database.DeckDao
import dev.memoji.flashcards.core.database.DeckEntity
import dev.memoji.flashcards.core.model.Deck
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Trimming names in one place here, rather than at each call site, keeps a Deck named
 * `"Git basics"` and one named `" Git basics "` from ever being stored as different things.
 */
internal class LocalDeckRepository @Inject constructor(
    private val deckDao: DeckDao,
    private val clock: Clock,
) : DeckRepository {

    override fun observeDecks(): Flow<List<Deck>> =
        deckDao.observeAll().map { entities -> entities.map(DeckEntity::asDeck) }

    override suspend fun createDeck(name: String): Long =
        deckDao.insert(DeckEntity(name = name.trim(), createdAt = clock.millis()))

    override suspend fun renameDeck(id: Long, name: String) = deckDao.rename(id, name.trim())

    override suspend fun deleteDeck(id: Long) = deckDao.delete(id)
}

private fun DeckEntity.asDeck() = Deck(
    id = id,
    name = name,
    createdAt = Instant.ofEpochMilli(createdAt),
)
