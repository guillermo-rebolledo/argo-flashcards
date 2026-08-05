package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.database.CardDao
import dev.memoji.flashcards.core.database.CardEntity
import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Grade
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Trimming both sides here, as [LocalDeckRepository] does for names, keeps a stray newline
 * from the keyboard out of what the Front of a Card is compared and shown as.
 */
internal class LocalCardRepository @Inject constructor(
    private val cardDao: CardDao,
    private val clock: Clock,
) : CardRepository {

    override fun observeCards(deckId: Long): Flow<List<Card>> =
        cardDao.observeByDeck(deckId).map { entities -> entities.map(CardEntity::asCard) }

    override suspend fun cardsInDeck(deckId: Long): List<Card> =
        cardDao.getByDeck(deckId).map(CardEntity::asCard)

    override suspend fun createCard(deckId: Long, front: String, back: String): Long =
        cardDao.insert(
            CardEntity(
                deckId = deckId,
                front = front.trim(),
                back = back.trim(),
                createdAt = clock.millis(),
            ),
        )

    override suspend fun updateCard(id: Long, front: String, back: String) =
        cardDao.updateContent(id, front.trim(), back.trim())

    override suspend fun deleteCard(id: Long) = cardDao.delete(id)

    override suspend fun recordGrade(id: Long, grade: Grade) = when (grade) {
        Grade.KNEW_IT -> cardDao.recordKnewIt(id, clock.millis())
        Grade.AGAIN -> cardDao.recordAgain(id, clock.millis())
    }
}

private fun CardEntity.asCard() = Card(
    id = id,
    deckId = deckId,
    front = front,
    back = back,
    masteryStreak = masteryStreak,
    lastSeenAt = lastSeenAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
)
