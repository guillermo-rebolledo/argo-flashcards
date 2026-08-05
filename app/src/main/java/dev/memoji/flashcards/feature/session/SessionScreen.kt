package dev.memoji.flashcards.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.model.Grade
import dev.memoji.flashcards.ui.component.EmptyState
import dev.memoji.flashcards.ui.motion.rememberReducedMotion

@Composable
fun SessionScreen(contentPadding: PaddingValues, onFinish: () -> Unit) {
    val viewModel: SessionViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SessionScreen(
        uiState = uiState,
        onToggleReveal = viewModel::toggleReveal,
        onGrade = viewModel::grade,
        onReviewMisses = viewModel::reviewMisses,
        onFinish = onFinish,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun SessionScreen(
    uiState: SessionUiState,
    onToggleReveal: () -> Unit,
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
                onToggleReveal = onToggleReveal,
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
    onToggleReveal: () -> Unit,
    onGrade: (Grade) -> Unit,
    onEnd: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    // The swipe belongs to the Card in hand and to no other: keying on it drops the drag, the
    // hints, and the "already graded" latch the moment the next Card arrives.
    val swipe = key(uiState.card.id) {
        rememberCardSwipe(reducedMotion = reducedMotion, onGrade = onGrade)
    }
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
        ReviewCard(uiState = uiState, swipe = swipe, onToggleReveal = onToggleReveal)
        Text(
            text = stringResource(R.string.session_swipe_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The buttons are the same door as the swipe, not a second one: they hand the Grade to
        // the same place, so a Card can only leave once however the user sent it away.
        OutlinedButton(
            onClick = { swipe.grade(Grade.AGAIN) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.session_again))
        }
        Button(
            onClick = { swipe.grade(Grade.KNEW_IT) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.session_knew_it))
        }
    }
}

/**
 * The Front alone until the user taps. Reading the Back before attempting recall is the one
 * way to get nothing out of a Session, so revealing has to be something they choose to do.
 *
 * The Card is also the control: dragged far enough either way it Grades itself, and says which
 * way it is heading the whole time it is being dragged.
 */
@Composable
private fun ReviewCard(
    uiState: SessionUiState.Reviewing,
    swipe: CardSwipe,
    onToggleReveal: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { swipe.cardWidth = it.width.toFloat() }
            .graphicsLayer {
                translationX = swipe.translation
                rotationZ = swipe.rotation
                alpha = swipe.alpha
            }
            // A tap is left to the click, which is also the way in for anyone driving the
            // screen by accessibility service rather than by finger.
            .clickable(onClick = onToggleReveal)
            // Measured from where the finger landed rather than from where Compose decided a
            // drag had begun, so the distances are the ones the design chose.
            .pointerInput(swipe) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    swipe.start()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            swipe.cancel()
                            break
                        }
                        if (!change.pressed) {
                            swipe.release()
                            break
                        }
                        swipe.drag(change.positionChange().x)
                        // Only once the Card owns the gesture: taking the movement any earlier
                        // would take the tap with it.
                        if (swipe.ownsGesture) change.consume()
                    }
                }
            },
    ) {
        Box {
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
            GradeHint(
                text = stringResource(R.string.session_again),
                alpha = { swipe.hintAlpha(Grade.AGAIN) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.align(Alignment.TopStart),
            )
            GradeHint(
                text = stringResource(R.string.session_knew_it),
                alpha = { swipe.hintAlpha(Grade.KNEW_IT) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/**
 * The Grade a drag is heading towards, fading in as it gets there. It is what makes a swipe
 * safe to start: the verdict is visible while there is still time to change it.
 */
@Composable
private fun GradeHint(
    text: String,
    alpha: () -> Float,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
        modifier = modifier
            // It answers a drag, so it has nothing to say to a screen reader — which would
            // otherwise read both Grades out of the Card on top of the buttons below it.
            .clearAndSetSemantics {}
            .padding(16.dp)
            .graphicsLayer { this.alpha = alpha() }
            .background(color = containerColor, shape = CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
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
