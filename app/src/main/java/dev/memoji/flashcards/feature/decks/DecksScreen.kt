package dev.memoji.flashcards.feature.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.model.Deck
import dev.memoji.flashcards.core.model.DeckSummary
import dev.memoji.flashcards.ui.component.DeckNameDialog
import dev.memoji.flashcards.ui.component.DeleteDeckDialog
import dev.memoji.flashcards.ui.component.EmptyState
import dev.memoji.flashcards.ui.component.FabClearance

@Composable
fun DecksScreen(
    contentPadding: PaddingValues,
    onOpenDeck: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    onAddCards: () -> Unit,
) {
    val viewModel: DecksViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DecksScreen(
        uiState = uiState,
        onOpenDeck = onOpenDeck,
        onStartSession = onStartSession,
        onAddCards = onAddCards,
        onRenameDeck = viewModel::renameDeck,
        onDeleteDeck = viewModel::deleteDeck,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun DecksScreen(
    uiState: DecksUiState,
    onOpenDeck: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    onAddCards: () -> Unit,
    onRenameDeck: (Long, String) -> Unit,
    onDeleteDeck: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    // Held by id rather than by Deck, so a dialog left open across a rotation reopens against
    // whatever that Deck says now, and closes by itself if the Deck is gone.
    var renamingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    val decks = (uiState as? DecksUiState.Decks)?.decks.orEmpty().map(DeckSummary::deck)
    val bottomPadding = contentPadding.calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            // Outside the list, as in the design, so the screen keeps its name while the
            // Decks scroll under it — and still has one when there are no Decks to scroll.
            Text(
                text = stringResource(R.string.destination_decks),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            when (uiState) {
                // The first read is a local database query. A spinner that appears for one
                // frame is worse than a screen that fills in on the next one.
                DecksUiState.Loading -> Unit
                DecksUiState.Empty -> EmptyDecks(
                    bottomPadding = bottomPadding,
                    modifier = Modifier.weight(1f),
                )
                is DecksUiState.Decks -> DeckList(
                    decks = uiState.decks,
                    upNext = uiState.upNext,
                    onOpen = { onOpenDeck(it.id) },
                    onStartSession = onStartSession,
                    onRename = { renamingId = it.id },
                    onDelete = { deletingId = it.id },
                    bottomPadding = bottomPadding,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Shown while loading too: the empty screen this lands on is the one the user has to
        // be able to act from, and it must not arrive a frame after the screen does. It opens
        // the Add Cards flow, which is where both a generated Deck and a hand-written one
        // start — the design has one button here, not two.
        ExtendedFloatingActionButton(
            onClick = onAddCards,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.decks_add_cards)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPadding)
                .padding(16.dp),
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
    decks: List<DeckSummary>,
    upNext: DeckSummary?,
    onOpen: (Deck) -> Unit,
    onStartSession: (Long) -> Unit,
    onRename: (Deck) -> Unit,
    onDelete: (Deck) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        // Clears the navigation bar and then the extended FAB, so the last Deck in the list
        // stays reachable rather than sitting under the button.
        contentPadding = PaddingValues(bottom = bottomPadding + FabClearance),
    ) {
        // Scrolls with the list rather than sitting above it: on a phone full of Decks the
        // list is what the screen is for, and one tap to study is already at the top.
        if (upNext != null) {
            item(key = UP_NEXT_KEY) {
                UpNextCard(
                    summary = upNext,
                    onStartSession = { onStartSession(upNext.deck.id) },
                    onOpen = { onOpen(upNext.deck) },
                )
            }
        }
        item(key = YOUR_DECKS_KEY) {
            Text(
                text = stringResource(R.string.decks_your_decks),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
            )
        }
        items(items = decks, key = { it.deck.id }) { summary ->
            DeckRow(
                deck = summary.deck,
                onOpen = { onOpen(summary.deck) },
                onRename = { onRename(summary.deck) },
                onDelete = { onDelete(summary.deck) },
            )
        }
    }
}

/**
 * One tap between deciding to study and studying. It names the Deck it will open so the user
 * can guess what will happen before they tap, and shows how far through it they are.
 */
@Composable
private fun UpNextCard(
    summary: DeckSummary,
    onStartSession: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.decks_up_next),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = summary.deck.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.decks_up_next_summary,
                    pluralStringResource(
                        R.plurals.decks_card_count,
                        summary.cardCount,
                        summary.cardCount,
                    ),
                    summary.masteredCount,
                    summary.cardCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { summary.masteredFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartSession) {
                    Text(stringResource(R.string.session_start))
                }
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.decks_see_cards))
                }
            }
        }
    }
}

@Composable
private fun DeckRow(
    deck: Deck,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(deck.name) },
        modifier = Modifier.clickable(onClick = onOpen),
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
                        text = { Text(stringResource(R.string.action_delete)) },
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
 * The design defines no empty state. On first launch the list would otherwise be a blank
 * screen with a button in the corner, so this says what the screen is for and names the button.
 */
@Composable
private fun EmptyDecks(
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = stringResource(R.string.decks_empty_title),
        body = stringResource(R.string.decks_empty_body),
        bottomPadding = bottomPadding,
        modifier = modifier,
    )
}

/** Stable keys for the two rows that are not Decks, so neither is confused for one. */
private const val UP_NEXT_KEY = "up-next"
private const val YOUR_DECKS_KEY = "your-decks"
