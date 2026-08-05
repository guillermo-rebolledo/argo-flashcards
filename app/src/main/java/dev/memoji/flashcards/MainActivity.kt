package dev.memoji.flashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AppViewModel = hiltViewModel()
            val settings by viewModel.uiState.collectAsStateWithLifecycle()

            // Both settings are resolved once, here, against what the system is asking for.
            // Below this point a screen reads the one answer and never the two halves of it.
            FlashcardsTheme(darkTheme = settings.theme.isDark(isSystemInDarkTheme())) {
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
}
