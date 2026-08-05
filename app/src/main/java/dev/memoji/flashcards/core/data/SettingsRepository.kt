package dev.memoji.flashcards.core.data

import dev.memoji.flashcards.core.model.ReminderTime
import dev.memoji.flashcards.core.model.SessionLength
import dev.memoji.flashcards.core.model.ThemePreference
import dev.memoji.flashcards.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * The user's settings. Unlike a Deck, none of this is content — a preference that goes missing
 * costs the user one tap to set again, so every read has a sensible answer even before
 * anything has been written.
 */
interface SettingsRepository {

    /**
     * [UserSettings.DEFAULT] until the user has chosen otherwise, and a new value on every
     * write — the Settings screen and the screens a setting governs watch the same flow, so a
     * change lands everywhere without anything being restarted.
     */
    fun observeSettings(): Flow<UserSettings>

    suspend fun setSessionLength(length: SessionLength)

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setReducedMotion(reducedMotion: Boolean)

    suspend fun setHideDayStreak(hideDayStreak: Boolean)

    suspend fun setRemindersEnabled(enabled: Boolean)

    suspend fun setReminderTime(time: ReminderTime)
}
