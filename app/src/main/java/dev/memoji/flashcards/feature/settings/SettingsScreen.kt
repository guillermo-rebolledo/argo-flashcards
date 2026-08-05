package dev.memoji.flashcards.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.model.ReminderTime
import dev.memoji.flashcards.core.model.SessionLength
import dev.memoji.flashcards.ui.motion.rememberSystemReducedMotion
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Android has no notification-permission flow to watch, so the screen asks again every
    // time it comes back — which is what closes the loop when the user grants or revokes it
    // in system settings and walks straight back in here.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshNotificationsAllowed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScreen(
        uiState = uiState,
        onSetSessionLength = viewModel::setSessionLength,
        onSetDarkTheme = viewModel::setDarkTheme,
        onSetReducedMotion = viewModel::setReducedMotion,
        onSetHideDayStreak = viewModel::setHideDayStreak,
        onSetRemindersEnabled = viewModel::setRemindersEnabled,
        onSetReminderTime = viewModel::setReminderTime,
        onSetApiKey = viewModel::setApiKey,
        onClearApiKey = viewModel::clearApiKey,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onSetSessionLength: (SessionLength) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetReducedMotion: (Boolean) -> Unit,
    onSetHideDayStreak: (Boolean) -> Unit,
    onSetRemindersEnabled: (Boolean) -> Unit,
    onSetReminderTime: (ReminderTime) -> Unit,
    onSetApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    contentPadding: PaddingValues,
) {
    var editingApiKey by rememberSaveable { mutableStateOf(false) }
    var pickingReminderTime by rememberSaveable { mutableStateOf(false) }
    val settings = uiState.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.destination_settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )

        SectionHeader(stringResource(R.string.settings_focus))
        ReducedMotionRow(override = settings.reducedMotion, onSet = onSetReducedMotion)
        // Under Focus rather than under Appearance: a counter someone finds stressful is not a
        // decoration they dislike, it is a thing in the way of them studying.
        SettingRow(
            title = stringResource(R.string.settings_hide_streak),
            body = stringResource(R.string.settings_hide_streak_body),
            checked = settings.hideDayStreak,
            onCheckedChange = onSetHideDayStreak,
        )
        SessionLengthChips(selected = settings.sessionLength, onSelect = onSetSessionLength)

        // Between Focus and Appearance: a reminder is about when the app asks for attention,
        // which is neither a preference about a Session nor about how it looks.
        SectionHeader(stringResource(R.string.settings_reminders))
        ReminderRows(
            status = uiState.reminderStatus,
            time = settings.reminderTime,
            onSetEnabled = onSetRemindersEnabled,
            onPickTime = { pickingReminderTime = true },
        )

        SectionHeader(stringResource(R.string.settings_appearance))
        // The switch shows the theme the app is actually in, which before the user has chosen
        // is whatever the system asked for — so moving it is always a change they can see.
        SettingRow(
            title = stringResource(R.string.settings_dark_theme),
            body = stringResource(R.string.settings_dark_theme_body),
            checked = settings.theme.isDark(isSystemInDarkTheme()),
            onCheckedChange = onSetDarkTheme,
        )

        // Its own section: this is the one setting that is a credential rather than a
        // preference, and the one the Add Cards flow sends people here for.
        SectionHeader(stringResource(R.string.settings_generation))
        ApiKeyRow(hasApiKey = uiState.hasApiKey, onEdit = { editingApiKey = true })

        // The navigation bar sits over the end of the scroll; without this the last row ends
        // underneath it.
        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }

    if (pickingReminderTime) {
        ReminderTimeDialog(
            time = settings.reminderTime,
            onConfirm = {
                onSetReminderTime(it)
                pickingReminderTime = false
            },
            onDismiss = { pickingReminderTime = false },
        )
    }

    if (editingApiKey) {
        ApiKeyDialog(
            hasApiKey = uiState.hasApiKey,
            onConfirm = {
                onSetApiKey(it)
                editingApiKey = false
            },
            onRemove = {
                onClearApiKey()
                editingApiKey = false
            },
            onDismiss = { editingApiKey = false },
        )
    }
}

/**
 * The switch, and — once reminders are asked for — the hour they arrive at.
 *
 * The permission is requested here, at the moment the user turns the switch on, and nowhere
 * else. A prompt at first launch would be asking before the user has any reason to say yes,
 * which is the fastest way to get a permanent no.
 */
@Composable
private fun ReminderRows(
    status: ReminderStatus,
    time: ReminderTime,
    onSetEnabled: (Boolean) -> Unit,
    onPickTime: () -> Unit,
) {
    val context = LocalContext.current
    val blocked = status == ReminderStatus.BLOCKED
    // Either answer turns the setting on: a denial does not change what the user asked for,
    // and the row goes to BLOCKED, which says who is refusing and offers the way out.
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onSetEnabled(true) }

    SettingRow(
        title = stringResource(R.string.settings_reminder),
        body = stringResource(
            if (blocked) R.string.settings_reminder_blocked else R.string.settings_reminder_body,
        ),
        checked = status == ReminderStatus.ON,
        onCheckedChange = {
            val action = reminderSwitchAction(
                status = status,
                permissionNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            )
            when (action) {
                ReminderSwitchAction.TURN_ON -> onSetEnabled(true)
                ReminderSwitchAction.TURN_OFF -> onSetEnabled(false)
                ReminderSwitchAction.ASK_FOR_PERMISSION ->
                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        // Only when blocked is there anywhere useful for a tap on the row to go — and it is
        // the only thing that still works once Android has stopped showing the prompt. The
        // app cannot undo this itself, and saying so without saying where is no help at all.
        onClick = if (blocked) {
            { context.startActivity(appNotificationSettings(context)) }
        } else {
            null
        },
    )

    // Hidden while reminders are off rather than greyed out: an hour for a nudge that is not
    // happening is a row asking to be read and then ignored.
    if (status != ReminderStatus.OFF) {
        SettingRow(
            title = stringResource(R.string.settings_reminder_time),
            body = stringResource(R.string.settings_reminder_time_body),
            trailing = { Text(text = formatTime(time), style = MaterialTheme.typography.bodyLarge) },
            onClick = onPickTime,
            contentDescription = stringResource(R.string.settings_reminder_time_pick),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    time: ReminderTime,
    onConfirm: (ReminderTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        // What the phone is set to, not what the locale usually does — someone who has put
        // their device on 24-hour time has already answered this question once.
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(ReminderTime(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The device's own clock format, so a phone on 24-hour time is not told its reminder is at
 * 8:00 PM. Any date will do — only the time of it is ever shown.
 */
@Composable
private fun formatTime(time: ReminderTime): String {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    return remember(format, time) {
        format.format(
            Date.from(
                // Any date at all; only the time of it is ever read back out.
                LocalDate.of(2000, 1, 1).atTime(time.hour, time.minute)
                    .atZone(ZoneId.systemDefault())
                    .toInstant(),
            ),
        )
    }
}

/**
 * Where Android keeps this app's notification switch. The app cannot turn its own
 * notifications back on, so pointing at the one place that can is the whole of what it can do.
 */
private fun appNotificationSettings(context: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/**
 * Opens empty even when a key is stored: the stored one is never read back, so there is
 * nothing to prefill. Entering a key replaces whatever was there.
 */
@Composable
private fun ApiKeyDialog(
    hasApiKey: Boolean,
    onConfirm: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var key by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_api_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_api_key_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.settings_api_key_label)) },
                    singleLine = true,
                    // Nothing on this screen shows the key, including while it is typed.
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(key) }, enabled = key.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            // Removing is only offered when there is something to remove.
            if (hasApiKey) {
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.action_remove))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

/**
 * Says whether a key is stored and nothing more — the key itself is never read back into the
 * UI, so there is no screen anywhere that a shoulder can read it off.
 */
@Composable
private fun ApiKeyRow(hasApiKey: Boolean, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_api_key),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    if (hasApiKey) {
                        R.string.settings_api_key_set
                    } else {
                        R.string.settings_api_key_unset
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The switch shows the user's own answer and nothing else, so a device that has motion off
 * today cannot record a preference on their behalf that surprises them when they turn it back
 * on. That the system is already asking for reduced motion is said in the supporting line,
 * where it belongs — it is context, not the state of this control.
 */
@Composable
private fun ReducedMotionRow(override: Boolean, onSet: (Boolean) -> Unit) {
    SettingRow(
        title = stringResource(R.string.settings_reduced_motion),
        body = if (rememberSystemReducedMotion()) {
            stringResource(R.string.settings_reduced_motion_system)
        } else {
            stringResource(R.string.settings_reduced_motion_body)
        },
        checked = override,
        onCheckedChange = onSet,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

/**
 * A row with a switch on the end. [onClick] is for the rows where the body has somewhere to
 * send the user — tapping the row goes there, while the switch stays the switch.
 */
@Composable
private fun SettingRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    SettingRow(title = title, body = body, onClick = onClick) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingRow(
    title: String,
    body: String,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(onClickLabel = contentDescription, onClick = onClick)
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/**
 * Three chips rather than a number field or a slider: a Session the user can pick any length
 * for is a Session they can make too long, which is the one thing it must never be.
 */
@Composable
private fun SessionLengthChips(selected: SessionLength, onSelect: (SessionLength) -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Text(
            text = stringResource(R.string.settings_session_length),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.settings_session_length_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SessionLength.entries.forEach { length ->
                FilterChip(
                    selected = length == selected,
                    onClick = { onSelect(length) },
                    label = {
                        Text(
                            stringResource(
                                R.string.settings_session_length_option,
                                length.cards,
                            ),
                        )
                    },
                )
            }
        }
    }
}
