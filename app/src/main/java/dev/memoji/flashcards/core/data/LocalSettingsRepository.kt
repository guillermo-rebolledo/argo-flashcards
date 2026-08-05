package dev.memoji.flashcards.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dev.memoji.flashcards.core.model.SessionLength
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class LocalSettingsRepository @Inject constructor(
    private val preferences: DataStore<Preferences>,
) : SettingsRepository {

    /**
     * Stored as the number of Cards rather than the enum's name, so the file says `5` and a
     * later value the app no longer offers still reads as something sensible.
     */
    override fun observeSessionLength(): Flow<SessionLength> = preferences.data.map { stored ->
        stored[SESSION_LENGTH]?.let(SessionLength::ofCards) ?: SessionLength.DEFAULT
    }

    override suspend fun setSessionLength(length: SessionLength) {
        preferences.edit { it[SESSION_LENGTH] = length.cards }
    }

    private companion object {
        val SESSION_LENGTH = intPreferencesKey("session_length")
    }
}
