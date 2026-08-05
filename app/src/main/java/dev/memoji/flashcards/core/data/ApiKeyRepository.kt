package dev.memoji.flashcards.core.data

import kotlinx.coroutines.flow.Flow

/**
 * The user's own Anthropic API key — the one credential the app holds.
 *
 * It is kept apart from the rest of the settings on purpose: preferences are backed up and a
 * lost one costs a tap, while this is a secret that is stored encrypted, excluded from backup,
 * and never written to a log. See ADR 0002.
 */
interface ApiKeyRepository {

    /**
     * Whether a key is stored, which is all Settings needs to show — the key itself is never
     * read back into the UI.
     */
    fun observeHasKey(): Flow<Boolean>

    /** The stored key, or null if none has been entered. Only Generation asks for this. */
    suspend fun apiKey(): String?

    /** Stores [key] with surrounding whitespace trimmed, replacing any existing one. */
    suspend fun setApiKey(key: String)

    suspend fun clearApiKey()
}
