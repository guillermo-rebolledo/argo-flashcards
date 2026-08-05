package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.model.Session
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The Session log: what the user has actually studied, one row per sitting. It is written to
 * once, at the end of a Session, and read by the Progress screen — there is no counter anywhere
 * else to keep in step with it.
 */
interface SessionRepository {

    /** Every Session ever recorded, oldest first, re-emitted when a new one is written. */
    fun observeSessions(): Flow<List<Session>>

    /**
     * Records a finished sitting, ending now. [startedAt] is when the Session began rather than
     * when this is called, because that is the day it belongs to — a Session begun before
     * midnight is part of the day the user sat down on.
     */
    suspend fun recordSession(deckId: Long, startedAt: Instant, cardsReviewed: Int, knewIt: Int)
}
