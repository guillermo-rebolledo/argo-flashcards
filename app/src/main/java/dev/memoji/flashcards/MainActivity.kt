package dev.memoji.flashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.memoji.flashcards.ui.FlashcardsApp
import dev.memoji.flashcards.ui.theme.FlashcardsTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FlashcardsTheme {
                FlashcardsApp()
            }
        }
    }
}
