package dev.memoji.flashcards.feature.decks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.memoji.flashcards.R
import dev.memoji.flashcards.ui.component.PlaceholderScreen

@Composable
fun DecksScreen(contentPadding: PaddingValues) {
    PlaceholderScreen(
        title = stringResource(R.string.destination_decks),
        supportingText = stringResource(R.string.placeholder_decks),
        contentPadding = contentPadding,
    )
}
