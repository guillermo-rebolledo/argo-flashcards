package dev.memoji.flashcards.feature.generate

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.memoji.flashcards.R

/**
 * Where the Cards are headed, said out loud on every step of the flow, with the one control
 * that changes it. Reading it is the point: the Cards are about to be written somewhere, and
 * the user should never have to save to find out where.
 */
@Composable
internal fun DeckTargetRow(
    target: GenerateTarget,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        overlineContent = { Text(stringResource(R.string.generate_target_label)) },
        headlineContent = {
            Text(
                text = when (target) {
                    GenerateTarget.NewDeck -> stringResource(R.string.generate_target_new_deck)
                    // Blank for the moment between opening the flow from a Deck and the Decks
                    // being read; the label above already says the Cards are going somewhere.
                    is GenerateTarget.ExistingDeck -> target.name
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            TextButton(onClick = onChange) {
                Text(stringResource(R.string.generate_target_change))
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The Decks the flow can be aimed at, with a new one at the top — the destination the app had
 * until now, and still the one the Deck list enters this flow with.
 */
@Composable
internal fun DeckTargetDialog(
    target: GenerateTarget,
    decks: List<DeckOption>,
    onPickNewDeck: () -> Unit,
    onPickDeck: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedId = (target as? GenerateTarget.ExistingDeck)?.id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generate_target_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = PickerMaxHeight)) {
                item {
                    TargetOption(
                        name = stringResource(R.string.generate_target_new_deck),
                        supporting = stringResource(R.string.generate_target_new_deck_body),
                        selected = selectedId == null,
                        onSelect = onPickNewDeck,
                    )
                }
                items(items = decks, key = { it.id }) { deck ->
                    TargetOption(
                        name = deck.name,
                        supporting = pluralStringResource(
                            R.plurals.decks_card_count,
                            deck.cardCount,
                            deck.cardCount,
                        ),
                        selected = deck.id == selectedId,
                        onSelect = { onPickDeck(deck.id) },
                    )
                }
            }
        },
        // One tap picks and closes, so there is nothing left to confirm.
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
private fun TargetOption(
    name: String,
    supporting: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { Text(supporting) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        // The dialog's own surface, so the rows do not read as cards laid on top of it.
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        // The row is the target, not just the button on it: a Deck name is easier to hit.
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 4.dp),
    )
}

/** Enough to see there is a list without the dialog growing past the Decks it is listing. */
private val PickerMaxHeight = 360.dp
