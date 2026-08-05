package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.database.CardDao
import dev.memoji.flashcards.core.database.DeckDao
import dev.memoji.flashcards.core.database.DeckEntity
import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Deck
import dev.memoji.flashcards.core.model.DeckSummary
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Trimming names in one place here, rather than at each call site, keeps a Deck named
 * `"Git basics"` and one named `" Git basics "` from ever being stored as different things.
 */
internal class LocalDeckRepository @Inject constructor(
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val clock: Clock,
) : DeckRepository {

    /**
     * Counted in Kotlin over the Cards themselves rather than by a `COUNT` in SQL. ADR 0003
     * makes Mastered a derived value with exactly one definition, and a `WHERE
     * mastery_streak >= 3` in a query would be a second one — cheaper to run and free to
     * disagree with the first. A Deck holds tens of Cards; there is nothing here to buy.
     */
    override fun observeDeckSummaries(): Flow<List<DeckSummary>> =
        combine(deckDao.observeAll(), cardDao.observeAll()) { decks, cards ->
            val byDeck = cards.groupBy { it.deckId }
            decks.map { deck ->
                val inDeck = byDeck[deck.id].orEmpty().map { it.asCard() }
                DeckSummary(
                    deck = deck.asDeck(),
                    cardCount = inDeck.size,
                    masteredCount = inDeck.count(Card::isMastered),
                    // When the Deck was last studied is when the most recent of its Cards was
                    // last seen. Nothing else records a sitting, and nothing needs to.
                    lastStudiedAt = inDeck.mapNotNull(Card::lastSeenAt).maxOrNull(),
                )
            }
        }

    override fun observeDeck(id: Long): Flow<Deck?> =
        deckDao.observeById(id).map { entity -> entity?.asDeck() }

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
