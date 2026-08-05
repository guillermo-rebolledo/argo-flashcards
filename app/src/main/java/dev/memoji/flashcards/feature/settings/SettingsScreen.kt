package dev.memoji.flashcards.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.memoji.flashcards.R
import dev.memoji.flashcards.ui.component.PlaceholderScreen

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    PlaceholderScreen(
        title = stringResource(R.string.destination_settings),
        supportingText = stringResource(R.string.placeholder_settings),
        contentPadding = contentPadding,
    )
}
