package dev.memoji.flashcards.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The teal scheme is the fallback below Android 12, so it is the only colour the app can
 * guarantee. These tests pin it to the design's seed and hold it to WCAG AA contrast.
 */
class TealColorSchemeTest {

    @Test
    fun `light teal keeps the seed colour from the design as its primary`() {
        assertEquals(TealSeed, TealLightColorScheme.primary)
    }

    @Test
    fun `dark teal lightens the primary so it reads on a dark surface`() {
        assertEquals(Color(0xFF82D5C8), TealDarkColorScheme.primary)
    }

    @Test
    fun `light and dark are genuinely different schemes`() {
        assertNotEquals(TealLightColorScheme.surface, TealDarkColorScheme.surface)
        assertTrue(TealLightColorScheme.surface.luminance() > TealDarkColorScheme.surface.luminance())
    }

    @Test
    fun `light teal meets AA contrast on every foreground pair`() {
        assertAccessibleContrast(TealLightColorScheme)
    }

    @Test
    fun `dark teal meets AA contrast on every foreground pair`() {
        assertAccessibleContrast(TealDarkColorScheme)
    }

    private fun assertAccessibleContrast(scheme: ColorScheme) {
        val pairs = listOf(
            "onSurface / surface" to (scheme.onSurface to scheme.surface),
            "onBackground / background" to (scheme.onBackground to scheme.background),
            "onPrimary / primary" to (scheme.onPrimary to scheme.primary),
            "onSecondary / secondary" to (scheme.onSecondary to scheme.secondary),
            "onTertiary / tertiary" to (scheme.onTertiary to scheme.tertiary),
            "onError / error" to (scheme.onError to scheme.error),
            "onPrimaryContainer / primaryContainer" to
                (scheme.onPrimaryContainer to scheme.primaryContainer),
            "onSecondaryContainer / secondaryContainer" to
                (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "onTertiaryContainer / tertiaryContainer" to
                (scheme.onTertiaryContainer to scheme.tertiaryContainer),
            "onErrorContainer / errorContainer" to
                (scheme.onErrorContainer to scheme.errorContainer),
            "onSurfaceVariant / surfaceVariant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
            "inverseOnSurface / inverseSurface" to
                (scheme.inverseOnSurface to scheme.inverseSurface),
        )
        pairs.forEach { (name, colors) ->
            val (foreground, background) = colors
            val ratio = contrastRatio(foreground, background)
            assertTrue(
                "$name has a contrast ratio of $ratio, below the AA minimum of 4.5",
                ratio >= 4.5,
            )
        }
    }
}

/** WCAG 2.1 relative luminance. */
private fun Color.luminance(): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

private fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}
