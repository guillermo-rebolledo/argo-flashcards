package dev.memoji.flashcards.feature.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.model.Deck

@Composable
fun DecksScreen(
    contentPadding: PaddingValues,
    viewModel: DecksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DecksScreen(
        uiState = uiState,
        onCreateDeck = viewModel::createDeck,
        onRenameDeck = viewModel::renameDeck,
        onDeleteDeck = viewModel::deleteDeck,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun DecksScreen(
    uiState: DecksUiState,
    onCreateDeck: (String) -> Unit,
    onRenameDeck: (Long, String) -> Unit,
    onDeleteDeck: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Held by id rather than by Deck, so a dialog left open across a rotation reopens against
    // whatever that Deck says now, and closes by itself if the Deck is gone.
    var creating by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    val decks = (uiState as? DecksUiState.Decks)?.decks.orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            // Nothing yet: the first read is a local database query, and a spinner that
            // appears for one frame is worse than a screen that stays still for one.
            DecksUiState.Loading -> Unit
            DecksUiState.Empty -> EmptyDecks(contentPadding)
            is DecksUiState.Decks -> DeckList(
                decks = uiState.decks,
                onRename = { renamingId = it.id },
                onDelete = { deletingId = it.id },
                contentPadding = contentPadding,
            )
        }

        if (uiState != DecksUiState.Loading) {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.decks_new_deck)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(contentPadding)
                    .padding(16.dp),
            )
        }
    }

    if (creating) {
        DeckNameDialog(
            title = stringResource(R.string.decks_new_deck),
            confirmLabel = stringResource(R.string.decks_create),
            initialName = "",
            onConfirm = {
                onCreateDeck(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }

    decks.find { it.id == renamingId }?.let { deck ->
        DeckNameDialog(
            title = stringResource(R.string.decks_rename),
            confirmLabel = stringResource(R.string.decks_rename),
            initialName = deck.name,
            onConfirm = {
                onRenameDeck(deck.id, it)
                renamingId = null
            },
            onDismiss = { renamingId = null },
        )
    }

    decks.find { it.id == deletingId }?.let { deck ->
        DeleteDeckDialog(
            deckName = deck.name,
            onConfirm = {
                onDeleteDeck(deck.id)
                deletingId = null
            },
            onDismiss = { deletingId = null },
        )
    }
}

@Composable
private fun DeckList(
    decks: List<Deck>,
    onRename: (Deck) -> Unit,
    onDelete: (Deck) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            // Clears the bars and then the extended FAB, so the last Deck stays reachable.
            bottom = contentPadding.calculateBottomPadding() + FabClearance,
        ),
    ) {
        item {
            Text(
                text = stringResource(R.string.destination_decks),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        items(items = decks, key = { it.id }) { deck ->
            DeckRow(
                deck = deck,
                onRename = { onRename(deck) },
                onDelete = { onDelete(deck) },
            )
        }
    }
}

@Composable
private fun DeckRow(deck: Deck, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(deck.name) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.decks_options_for, deck.name),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.decks_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.decks_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

/**
 * The design defines no empty state. On first launch the list would otherwise be a blank screen
 * with a button in the corner, so this says what a Deck is and points at that button.
 */
@Composable
private fun EmptyDecks(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.decks_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.decks_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val FabClearance = 88.dp
