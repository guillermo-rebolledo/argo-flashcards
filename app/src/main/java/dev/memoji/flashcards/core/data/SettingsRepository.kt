package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.model.SessionLength
import kotlinx.coroutines.flow.Flow

/**
 * The user's settings. Unlike a Deck, none of this is content — a preference that goes missing
 * costs the user one tap to set again, so every read has a sensible answer even before
 * anything has been written.
 */
interface SettingsRepository {

    /** [SessionLength.DEFAULT] until the user has chosen otherwise. */
    fun observeSessionLength(): Flow<SessionLength>

    suspend fun setSessionLength(length: SessionLength)
}
