package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.database.SessionDao
import dev.memoji.flashcards.core.database.SessionEntity
import dev.memoji.flashcards.core.model.Session
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class LocalSessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val clock: Clock,
) : SessionRepository {

    override fun observeSessions(): Flow<List<Session>> =
        sessionDao.observeAll().map { entities -> entities.map(SessionEntity::asSession) }

    override suspend fun recordSession(
        deckId: Long,
        startedAt: Instant,
        cardsReviewed: Int,
        knewIt: Int,
    ) {
        sessionDao.insert(
            SessionEntity(
                deckId = deckId,
                startedAt = startedAt.toEpochMilli(),
                endedAt = clock.millis(),
                cardsReviewed = cardsReviewed,
                knewIt = knewIt,
            ),
        )
    }
}

private fun SessionEntity.asSession() = Session(
    id = id,
    deckId = deckId,
    started = Instant.ofEpochMilli(startedAt),
    ended = Instant.ofEpochMilli(endedAt),
    cardsReviewed = cardsReviewed,
    knewIt = knewIt,
)
