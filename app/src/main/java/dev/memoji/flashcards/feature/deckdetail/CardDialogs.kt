package dev.memoji.flashcards.feature.deckdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.memoji.flashcards.R

/**
 * One editor serves writing a Card and fixing one: a Card is its Front and its Back, and both
 * cases ask for exactly those two. Editing starts from what the Card already says.
 *
 * The Front carries the design's "one idea per card" guidance, which is the whole rule the app
 * is built around and the easiest one to break while typing.
 */
@Composable
internal fun CardEditorDialog(
    title: String,
    confirmLabel: String,
    initialFront: String,
    initialBack: String,
    onConfirm: (front: String, back: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var front by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialFront, TextRange(initialFront.length)))
    }
    var back by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialBack, TextRange(initialBack.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val canConfirm = front.text.isNotBlank() && back.text.isNotBlank()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = front,
                    onValueChange = { front = it },
                    label = { Text(stringResource(R.string.cards_front_label)) },
                    supportingText = { Text(stringResource(R.string.cards_front_supporting)) },
                    // Both sides can run past one line, and a Back cut off mid-sentence is
                    // worse than a dialog that grows.
                    maxLines = MaxLines,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                OutlinedTextField(
                    value = back,
                    onValueChange = { back = it },
                    label = { Text(stringResource(R.string.cards_back_label)) },
                    maxLines = MaxLines,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(front.text, back.text) },
                enabled = canConfirm,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Deleting a Card is final, as deleting a Deck is, so it is confirmed the same way. */
@Composable
internal fun DeleteCardDialog(
    front: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cards_delete_title, front)) },
        text = { Text(stringResource(R.string.cards_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val MaxLines = 4
