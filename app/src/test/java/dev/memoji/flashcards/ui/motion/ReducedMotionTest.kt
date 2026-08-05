package dev.memoji.flashcards.ui.motion

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReducedMotionTest {

    private val resolver =
        ApplicationProvider.getApplicationContext<Context>().contentResolver

    @Test
    fun `animations turned off system-wide means reduced motion`() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

        assertTrue(isReducedMotion(resolver))
    }

    @Test
    fun `animations at normal speed do not mean reduced motion`() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

        assertFalse(isReducedMotion(resolver))
    }

    @Test
    fun `animations slowed down for debugging do not mean reduced motion`() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 10f)

        assertFalse(isReducedMotion(resolver))
    }

    @Test
    fun `the in-app toggle turns motion off when the system has not`() {
        assertTrue(reducedMotion(systemSetting = false, userOverride = true))
    }

    /** The point of the rule: the toggle is an override on, never an override off. */
    @Test
    fun `the system setting keeps motion off whatever the toggle says`() {
        assertTrue(reducedMotion(systemSetting = true, userOverride = false))
        assertTrue(reducedMotion(systemSetting = true, userOverride = true))
    }

    @Test
    fun `with neither asking for it the app animates`() {
        assertFalse(reducedMotion(systemSetting = false, userOverride = false))
    }
}
