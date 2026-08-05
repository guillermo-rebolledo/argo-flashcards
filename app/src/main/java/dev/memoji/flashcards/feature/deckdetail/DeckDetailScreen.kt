package dev.memoji.flashcards.feature.deckdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.ui.component.DeckNameDialog
import dev.memoji.flashcards.ui.component.DeleteDeckDialog
import dev.memoji.flashcards.ui.component.EmptyState
import dev.memoji.flashcards.ui.component.FabClearance

@Composable
fun DeckDetailScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val viewModel: DeckDetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DeckDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onSetFilter = viewModel::setFilter,
        onAddCard = viewModel::addCard,
        onEditCard = viewModel::editCard,
        onDeleteCard = viewModel::deleteCard,
        onRenameDeck = viewModel::renameDeck,
        onDeleteDeck = viewModel::deleteDeck,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun DeckDetailScreen(
    uiState: DeckDetailUiState,
    onBack: () -> Unit,
    onSetFilter: (CardFilter) -> Unit,
    onAddCard: (front: String, back: String) -> Unit,
    onEditCard: (id: Long, front: String, back: String) -> Unit,
    onDeleteCard: (Long) -> Unit,
    onRenameDeck: (String) -> Unit,
    onDeleteDeck: () -> Unit,
    contentPadding: PaddingValues,
) {
    // Held by id rather than by Card, so a dialog left open across a rotation reopens against
    // whatever that Card says now, and closes by itself if the Card is gone.
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renamingDeck by rememberSaveable { mutableStateOf(false) }
    var deletingDeck by rememberSaveable { mutableStateOf(false) }

    val ready = uiState as? DeckDetailUiState.Ready
    val bottomPadding = contentPadding.calculateBottomPadding()

    // The Deck can go while this screen is on it — deleted here, or from the Deck list behind
    // it. There is nothing left to show either way.
    if (uiState is DeckDetailUiState.DeckGone) {
        LaunchedEffect(Unit) { onBack() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            DeckDetailHeader(
                deckName = ready?.deck?.name.orEmpty(),
                onBack = onBack,
                onRename = { renamingDeck = true },
                onDelete = { deletingDeck = true },
            )
            // The first read is a local database query. A spinner that appears for one frame
            // is worse than a screen that fills in on the next one.
            if (ready != null) {
                MasterySummary(mastered = ready.masteredCount, total = ready.cardCount)
                CardFilterChips(selected = ready.filter, onSelect = onSetFilter)
                when {
                    ready.isDeckEmpty -> EmptyDeck(
                        bottomPadding = bottomPadding,
                        modifier = Modifier.weight(1f),
                    )
                    // A Deck that has Cards, none of which the chosen chip matches. Not the
                    // same thing as an empty Deck, and it must not read as one.
                    ready.cards.isEmpty() -> EmptyState(
                        title = stringResource(ready.filter.emptyTitleRes),
                        body = stringResource(
                            ready.filter.emptyBodyRes,
                            Card.MASTERY_THRESHOLD,
                        ),
                        bottomPadding = bottomPadding,
                        modifier = Modifier.weight(1f),
                    )
                    else -> CardList(
                        cards = ready.cards,
                        onEdit = { editingId = it.id },
                        onDelete = { deletingId = it.id },
                        bottomPadding = bottomPadding,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Shown while loading too: an empty Deck is the screen this most often lands on, and
        // the way out of it must not arrive a frame after the screen does. Not shown once the
        // Deck is gone — there would be nothing to write the Card into.
        if (uiState !is DeckDetailUiState.DeckGone) {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.cards_add_card)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = bottomPadding)
                    .padding(16.dp),
            )
        }
    }

    if (adding) {
        CardEditorDialog(
            title = stringResource(R.string.cards_new_card),
            confirmLabel = stringResource(R.string.cards_add),
            initialFront = "",
            initialBack = "",
            onConfirm = { front, back ->
                onAddCard(front, back)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }

    ready?.cards?.find { it.id == editingId }?.let { card ->
        CardEditorDialog(
            title = stringResource(R.string.cards_edit_card),
            confirmLabel = stringResource(R.string.action_save),
            initialFront = card.front,
            initialBack = card.back,
            onConfirm = { front, back ->
                onEditCard(card.id, front, back)
                editingId = null
            },
            onDismiss = { editingId = null },
        )
    }

    ready?.cards?.find { it.id == deletingId }?.let { card ->
        DeleteCardDialog(
            front = card.front,
            onConfirm = {
                onDeleteCard(card.id)
                deletingId = null
            },
            onDismiss = { deletingId = null },
        )
    }

    if (ready != null && renamingDeck) {
        DeckNameDialog(
            title = stringResource(R.string.decks_rename),
            confirmLabel = stringResource(R.string.decks_rename),
            initialName = ready.deck.name,
            onConfirm = {
                onRenameDeck(it)
                renamingDeck = false
            },
            onDismiss = { renamingDeck = false },
        )
    }

    if (ready != null && deletingDeck) {
        DeleteDeckDialog(
            deckName = ready.deck.name,
            onConfirm = {
                onDeleteDeck()
                deletingDeck = false
            },
            onDismiss = { deletingDeck = false },
        )
    }
}

@Composable
private fun DeckDetailHeader(
    deckName: String,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_go_back),
            )
        }
        Text(
            text = deckName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.decks_options_for, deckName),
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
    }
}

/**
 * Counts the whole Deck, not what the chips are showing — the summary is about progress, and
 * it must not appear to change because the user tapped a filter.
 */
@Composable
private fun MasterySummary(mastered: Int, total: Int) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.cards_mastery_summary, mastered, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else mastered.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CardFilterChips(selected: CardFilter, onSelect: (CardFilter) -> Unit) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes)) },
            )
        }
    }
}

@Composable
private fun CardList(
    cards: List<Card>,
    onEdit: (Card) -> Unit,
    onDelete: (Card) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        // Clears the extended FAB, so the last Card in the list stays reachable rather than
        // sitting under the button.
        contentPadding = PaddingValues(bottom = bottomPadding + FabClearance),
    ) {
        items(items = cards, key = { it.id }) { card ->
            CardRow(
                card = card,
                onEdit = { onEdit(card) },
                onDelete = { onDelete(card) },
            )
        }
    }
}

@Composable
private fun CardRow(card: Card, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(card.front) },
        supportingContent = {
            Text(card.back, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        // Tapping the row edits it: fixing a typo is the common reason to touch a Card, and
        // the overflow menu still names the action for anyone looking for it.
        modifier = Modifier.clickable(onClick = onEdit),
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (card.isMastered) {
                            R.string.cards_filter_mastered
                        } else {
                            R.string.cards_filter_learning
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(
                                R.string.cards_options_for,
                                card.front,
                            ),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            onClick = {
                                menuOpen = false
                                onEdit()
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
            }
        },
    )
}

/**
 * The design defines no empty state. A Deck with no Cards would otherwise be a title over
 * nothing, so this says what a Card is and points at the button that writes one.
 */
@Composable
private fun EmptyDeck(bottomPadding: Dp, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.cards_empty_title),
        body = stringResource(R.string.cards_empty_body),
        bottomPadding = bottomPadding,
        modifier = modifier,
    )
}
