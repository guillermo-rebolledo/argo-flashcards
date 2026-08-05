package dev.memoji.flashcards.feature.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.memoji.flashcards.R
import dev.memoji.flashcards.ui.component.DeckNameDialog

@Composable
fun GenerateScreen(
    contentPadding: PaddingValues,
    onOpenDeck: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val viewModel: GenerateViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedDeckId by viewModel.savedDeckId.collectAsStateWithLifecycle()

    // Saving, or writing a Deck by hand, both end the same way: in the Deck that was made.
    LaunchedEffect(savedDeckId) {
        savedDeckId?.let {
            viewModel.deckOpened()
            onOpenDeck(it)
        }
    }

    GenerateScreen(
        uiState = uiState,
        onSetText = viewModel::setText,
        onGenerate = viewModel::generate,
        onSetKept = viewModel::setKept,
        onSetDeckName = viewModel::setDeckName,
        onSave = viewModel::save,
        onBackToEntry = viewModel::backToEntry,
        onPasteTextInstead = viewModel::pasteTextInstead,
        onCreateEmptyDeck = viewModel::createEmptyDeck,
        onOpenSettings = onOpenSettings,
        onClose = onClose,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun GenerateScreen(
    uiState: GenerateUiState,
    onSetText: (String) -> Unit,
    onGenerate: () -> Unit,
    onSetKept: (Int, Boolean) -> Unit,
    onSetDeckName: (String) -> Unit,
    onSave: () -> Unit,
    onBackToEntry: (String) -> Unit,
    onPasteTextInstead: () -> Unit,
    onCreateEmptyDeck: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    contentPadding: PaddingValues,
) {
    var namingDeck by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        // Pasting and waiting are the same screen mid-thought, so they keep the same header:
        // only the step that has something to go back to gets a back arrow.
        if (uiState is GenerateUiState.Proposed) {
            Header(
                title = stringResource(R.string.generate_review_title),
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_go_back),
                    )
                },
                // Back goes to the box rather than out of the flow, so a Generation the user
                // did not like is one tap from being tried again.
                onNavigate = { onBackToEntry(uiState.sourceText) },
            )
        } else {
            Header(
                title = stringResource(R.string.generate_title),
                icon = { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) },
                onNavigate = onClose,
            )
        }

        when (uiState) {
            is GenerateUiState.Entry -> {
                SourceEntry(
                    uiState = uiState,
                    onSetText = onSetText,
                    onGenerate = onGenerate,
                    onOpenSettings = onOpenSettings,
                    onPasteTextInstead = onPasteTextInstead,
                    onWriteThemMyself = { namingDeck = true },
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    modifier = Modifier.weight(1f),
                )
            }

            GenerateUiState.Busy -> Busy(modifier = Modifier.weight(1f))

            is GenerateUiState.Proposed -> {
                ProposedCards(
                    uiState = uiState,
                    onSetKept = onSetKept,
                    onSetDeckName = onSetDeckName,
                    onSave = onSave,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (namingDeck) {
        DeckNameDialog(
            title = stringResource(R.string.decks_new_deck),
            confirmLabel = stringResource(R.string.decks_create),
            initialName = "",
            onConfirm = {
                onCreateEmptyDeck(it)
                namingDeck = false
            },
            onDismiss = { namingDeck = false },
        )
    }
}

@Composable
private fun Header(title: String, icon: @Composable () -> Unit, onNavigate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigate) { icon() }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SourceEntry(
    uiState: GenerateUiState.Entry,
    onSetText: (String) -> Unit,
    onGenerate: () -> Unit,
    onOpenSettings: () -> Unit,
    onPasteTextInstead: () -> Unit,
    onWriteThemMyself: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.generate_headline),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.generate_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = uiState.text,
            onValueChange = onSetText,
            placeholder = { Text(stringResource(R.string.generate_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp)
                .padding(20.dp),
        )

        // What the app made of what is in the box, in the user's terms: a link it recognised
        // as one, or a count of what they pasted. Only once there is something to say.
        if (uiState.isLink) {
            Text(
                text = stringResource(R.string.generate_link_detected),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else if (uiState.wordCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.generate_word_count,
                    uiState.wordCount,
                    uiState.wordCount,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        uiState.failureCopy?.let { copy ->
            GenerationFailureMessage(
                copy = copy,
                onOpenSettings = onOpenSettings,
                onTryAgain = onGenerate,
                onPasteTextInstead = onPasteTextInstead,
            )
        }

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                bottom = bottomPadding + 24.dp,
            ),
        ) {
            Button(
                onClick = onGenerate,
                enabled = uiState.canGenerate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.generate_action))
            }
            TextButton(onClick = onWriteThemMyself, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.generate_write_them_myself))
            }
        }
    }
}

/**
 * Every failure gets its own message and its own way forward. The one that matters most is the
 * first launch with no key: it routes to Settings rather than reading as something being broken.
 */
@Composable
private fun GenerationFailureMessage(
    copy: FailureCopy,
    onOpenSettings: () -> Unit,
    onTryAgain: () -> Unit,
    onPasteTextInstead: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(copy.messageRes),
                style = MaterialTheme.typography.bodyMedium,
            )
            when (copy.action) {
                FailureAction.OPEN_SETTINGS -> TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.generate_open_settings))
                }
                FailureAction.TRY_AGAIN -> TextButton(onClick = onTryAgain) {
                    Text(stringResource(R.string.generate_try_again))
                }
                FailureAction.PASTE_TEXT -> TextButton(onClick = onPasteTextInstead) {
                    Text(stringResource(R.string.generate_paste_text_instead))
                }
                FailureAction.NONE -> Unit
            }
        }
    }
}

/** The whole point of this state is to say that the wait is expected and nothing is stuck. */
@Composable
private fun Busy(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.generate_busy_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.generate_busy_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProposedCards(
    uiState: GenerateUiState.Proposed,
    onSetKept: (Int, Boolean) -> Unit,
    onSetDeckName: (String) -> Unit,
    onSave: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = uiState.deckName,
            onValueChange = onSetDeckName,
            label = { Text(stringResource(R.string.decks_name_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
        Text(
            text = stringResource(
                R.string.generate_kept_summary,
                uiState.keptCount,
                uiState.cards.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(uiState.cards) { index, proposed ->
                ProposedCardRow(
                    proposed = proposed,
                    onSetKept = { onSetKept(index, it) },
                )
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = bottomPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = onSave,
                    enabled = uiState.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.generate_save,
                            uiState.keptCount,
                            uiState.keptCount,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProposedCardRow(proposed: ProposedCard, onSetKept: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = proposed.kept, onCheckedChange = onSetKept)
        Column(modifier = Modifier.padding(start = 4.dp, top = 12.dp)) {
            // Unkept Cards stay legible but visibly out — they are still there to be put back.
            val alpha = if (proposed.kept) 1f else 0.4f
            Text(
                text = proposed.card.front,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = proposed.card.back,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}
