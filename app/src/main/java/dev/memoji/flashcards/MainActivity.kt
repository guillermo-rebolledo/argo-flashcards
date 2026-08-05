package dev.memoji.flashcards

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.memoji.flashcards.ui.AppViewModel
import dev.memoji.flashcards.ui.FlashcardsApp
import dev.memoji.flashcards.ui.motion.LocalReducedMotion
import dev.memoji.flashcards.ui.motion.reducedMotion
import dev.memoji.flashcards.ui.motion.rememberSystemReducedMotion
import dev.memoji.flashcards.ui.theme.FlashcardsTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Before `setContent` so the window is already edge-to-edge on the first frame. What
        // colour the bar icons take is decided again below, once the theme is known.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AppViewModel = hiltViewModel()
            val settings by viewModel.uiState.collectAsStateWithLifecycle()

            // Where the theme override and the system setting become one answer. Screens read
            // the answer; only the Settings screen looks at the halves, because only it has
            // to explain which of the two decided.
            val darkTheme = settings.theme.isDark(isSystemInDarkTheme())
            SystemBarIcons(darkTheme)

            FlashcardsTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(
                    LocalReducedMotion provides reducedMotion(
                        systemSetting = rememberSystemReducedMotion(),
                        userOverride = settings.reducedMotion,
                    ),
                ) {
                    FlashcardsApp()
                }
            }
        }
    }

    /**
     * The status and navigation bar icons follow the app's theme, not the system's. Without
     * this a light override on a dark phone draws light icons on the app's light background,
     * where they cannot be seen at all — the override has to reach the bars too.
     *
     * The scrims are what Android falls back to on the versions that cannot draw a
     * three-button navigation bar transparently; they are never seen on gesture navigation.
     */
    @Composable
    private fun SystemBarIcons(darkTheme: Boolean) {
        DisposableEffect(darkTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                    darkTheme
                },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
            )
            onDispose {}
        }
    }

    private companion object {
        /** Android's own defaults for a bar it has to tint. */
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
