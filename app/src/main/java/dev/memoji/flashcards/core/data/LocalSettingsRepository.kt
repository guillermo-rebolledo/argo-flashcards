package dev.memoji.flashcards.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.memoji.flashcards.core.model.SessionLength
import dev.memoji.flashcards.core.model.ThemePreference
import dev.memoji.flashcards.core.model.UserSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class LocalSettingsRepository @Inject constructor(
    private val preferences: DataStore<Preferences>,
) : SettingsRepository {

    /**
     * Every key falls back on its own, so a value this version of the app does not understand
     * costs the user that one setting rather than all of them.
     */
    override fun observeSettings(): Flow<UserSettings> = preferences.data.map { stored ->
        UserSettings(
            // Stored as the number of Cards rather than the enum's name, so the file says `5`
            // and a later value the app no longer offers still reads as something sensible.
            sessionLength = stored[SESSION_LENGTH]?.let(SessionLength::ofCards)
                ?: SessionLength.DEFAULT,
            theme = stored[THEME]?.let(ThemePreference::ofName) ?: ThemePreference.DEFAULT,
            reducedMotion = stored[REDUCED_MOTION] ?: UserSettings.DEFAULT.reducedMotion,
            hideStreak = stored[HIDE_STREAK] ?: UserSettings.DEFAULT.hideStreak,
        )
    }

    override suspend fun setSessionLength(length: SessionLength) {
        preferences.edit { it[SESSION_LENGTH] = length.cards }
    }

    override suspend fun setTheme(theme: ThemePreference) {
        preferences.edit { it[THEME] = theme.name }
    }

    override suspend fun setReducedMotion(reducedMotion: Boolean) {
        preferences.edit { it[REDUCED_MOTION] = reducedMotion }
    }

    override suspend fun setHideStreak(hideStreak: Boolean) {
        preferences.edit { it[HIDE_STREAK] = hideStreak }
    }

    private companion object {
        val SESSION_LENGTH = intPreferencesKey("session_length")
        val THEME = stringPreferencesKey("theme")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val HIDE_STREAK = booleanPreferencesKey("hide_streak")
    }
}
