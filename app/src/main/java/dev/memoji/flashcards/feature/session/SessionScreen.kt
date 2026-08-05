package dev.memoji.flashcards.feature.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.model.Grade
import dev.memoji.flashcards.ui.component.EmptyState

@Composable
fun SessionScreen(contentPadding: PaddingValues, onFinish: () -> Unit) {
    val viewModel: SessionViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SessionScreen(
        uiState = uiState,
        onReveal = viewModel::reveal,
        onGrade = viewModel::grade,
        onReviewMisses = viewModel::reviewMisses,
        onFinish = onFinish,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun SessionScreen(
    uiState: SessionUiState,
    onReveal: () -> Unit,
    onGrade: (Grade) -> Unit,
    onReviewMisses: () -> Unit,
    onFinish: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        when (uiState) {
            // The Cards are one local read away. A spinner that appears for one frame is
            // worse than a screen that fills in on the next one.
            SessionUiState.Loading -> Unit
            SessionUiState.Empty -> EmptySession(onFinish = onFinish)
            is SessionUiState.Reviewing -> Review(
                uiState = uiState,
                onReveal = onReveal,
                onGrade = onGrade,
                onEnd = onFinish,
            )
            is SessionUiState.Finished -> Results(
                uiState = uiState,
                onReviewMisses = onReviewMisses,
                onDone = onFinish,
            )
        }
    }
}

@Composable
private fun ColumnScope.Review(
    uiState: SessionUiState.Reviewing,
    onReveal: () -> Unit,
    onGrade: (Grade) -> Unit,
    onEnd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ending is always one tap away and never costs anything: the Grades already given
        // are kept, and nothing is written for the Cards the user did not reach.
        IconButton(onClick = onEnd) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.session_end),
            )
        }
        Text(
            text = stringResource(R.string.session_position, uiState.position, uiState.total),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        // Balances the close button, so the counter sits in the middle of the screen rather
        // than in the middle of what is left of it.
        Spacer(modifier = Modifier.size(IconButtonSize))
    }
    LinearProgressIndicator(
        progress = { uiState.progress },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        ReviewCard(uiState = uiState, onReveal = onReveal)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = { onGrade(Grade.AGAIN) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.session_again))
        }
        Button(
            onClick = { onGrade(Grade.KNEW_IT) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.session_knew_it))
        }
    }
}

/**
 * The Front alone until the user taps. Reading the Back before attempting recall is the one
 * way to get nothing out of a Session, so revealing has to be something they choose to do.
 */
@Composable
private fun ReviewCard(uiState: SessionUiState.Reviewing, onReveal: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !uiState.revealed, onClick = onReveal),
    ) {
        // Tall enough to be worth tapping and to keep the Card from resizing as the Back
        // appears, but sized to what it says rather than to the screen.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ReviewCardHeight)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text(text = uiState.card.front, style = MaterialTheme.typography.headlineMedium)
            if (uiState.revealed) {
                HorizontalDivider()
                Text(text = uiState.card.back, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = stringResource(R.string.session_tap_to_reveal),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Results(
    uiState: SessionUiState.Finished,
    onReviewMisses: () -> Unit,
    onDone: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 20.dp)) {
                Text(
                    text = stringResource(R.string.session_done_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.session_score, uiState.knewIt, uiState.total),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    text = stringResource(R.string.session_score_caption),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { uiState.score },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                )
            }
        }
        if (uiState.misses.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.session_misses_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(items = uiState.misses, key = { it.id }) { card ->
                ListItem(
                    headlineContent = { Text(card.front) },
                    supportingContent = { Text(card.back) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        item {
            // The point of the whole screen, and the reason it is not just a score: a short
            // Session is a complete Session, and there is no backlog waiting tomorrow.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_reassurance),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.session_done))
        }
        if (uiState.misses.isNotEmpty()) {
            TextButton(onClick = onReviewMisses, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.session_review_misses))
            }
        }
    }
}

/**
 * A Deck with no Cards, or one deleted from under the Session. The screen says so rather than
 * showing a Session of nothing, and the way out is the same door the user came in by.
 */
@Composable
private fun ColumnScope.EmptySession(onFinish: () -> Unit) {
    Row(modifier = Modifier.padding(start = 4.dp, top = 12.dp)) {
        IconButton(onClick = onFinish) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.session_end),
            )
        }
    }
    EmptyState(
        title = stringResource(R.string.session_empty_title),
        body = stringResource(R.string.session_empty_body),
        bottomPadding = 0.dp,
        modifier = Modifier.weight(1f),
    )
}

/** Taken from the design, which sets the Card a minimum height rather than a fixed one. */
private val ReviewCardHeight: Dp = 320.dp

/** The touch target Material gives an icon button, mirrored to keep the counter centred. */
private val IconButtonSize: Dp = 48.dp
