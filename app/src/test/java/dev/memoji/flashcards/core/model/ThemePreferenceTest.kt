package dev.memoji.flashcards.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferenceTest {

    @Test
    fun `with no override the system setting decides`() {
        assertTrue(ThemePreference.FOLLOW_SYSTEM.isDark(systemInDarkTheme = true))
        assertFalse(ThemePreference.FOLLOW_SYSTEM.isDark(systemInDarkTheme = false))
    }

    @Test
    fun `an override wins over the system setting both ways`() {
        assertTrue(ThemePreference.DARK.isDark(systemInDarkTheme = false))
        assertFalse(ThemePreference.LIGHT.isDark(systemInDarkTheme = true))
    }

    @Test
    fun `following the system is what the app does until asked otherwise`() {
        assertEquals(ThemePreference.FOLLOW_SYSTEM, ThemePreference.DEFAULT)
    }

    @Test
    fun `each preference round-trips through its stored name`() {
        ThemePreference.entries.forEach { preference ->
            assertEquals(preference, ThemePreference.ofName(preference.name))
        }
    }

    @Test
    fun `a stored name this version does not know falls back to the system`() {
        assertEquals(ThemePreference.DEFAULT, ThemePreference.ofName("HIGH_CONTRAST"))
        assertEquals(ThemePreference.DEFAULT, ThemePreference.ofName(""))
    }
}
