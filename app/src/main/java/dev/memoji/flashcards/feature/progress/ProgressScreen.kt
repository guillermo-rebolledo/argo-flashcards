package dev.memoji.flashcards.feature.progress

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.memoji.flashcards.R
import dev.memoji.flashcards.ui.component.PlaceholderScreen

@Composable
fun ProgressScreen(contentPadding: PaddingValues) {
    PlaceholderScreen(
        title = stringResource(R.string.destination_progress),
        supportingText = stringResource(R.string.placeholder_progress),
        contentPadding = contentPadding,
    )
}
