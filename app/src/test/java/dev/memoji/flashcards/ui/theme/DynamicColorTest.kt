package dev.memoji.flashcards.ui.theme

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicColorTest {

    @Test
    fun `wallpaper colour is used from Android 12 onwards`() {
        assertTrue(supportsDynamicColor(Build.VERSION_CODES.S))
        assertTrue(supportsDynamicColor(Build.VERSION_CODES.TIRAMISU))
    }

    @Test
    fun `older devices fall back to the teal scheme`() {
        assertFalse(supportsDynamicColor(Build.VERSION_CODES.R))
        assertFalse(supportsDynamicColor(Build.VERSION_CODES.O))
    }
}
