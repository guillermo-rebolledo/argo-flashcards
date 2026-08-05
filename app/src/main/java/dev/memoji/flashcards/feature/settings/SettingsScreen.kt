package dev.memoji.flashcards.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.model.SessionLength
import dev.memoji.flashcards.core.model.UserSettings
import dev.memoji.flashcards.ui.motion.rememberSystemReducedMotion

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onSetSessionLength = viewModel::setSessionLength,
        onSetDarkTheme = viewModel::setDarkTheme,
        onSetReducedMotion = viewModel::setReducedMotion,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun SettingsScreen(
    uiState: UserSettings,
    onSetSessionLength: (SessionLength) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetReducedMotion: (Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
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
        ReducedMotionRow(override = uiState.reducedMotion, onSet = onSetReducedMotion)
        SessionLengthChips(selected = uiState.sessionLength, onSelect = onSetSessionLength)

        SectionHeader(stringResource(R.string.settings_appearance))
        // The switch shows the theme the app is actually in, which before the user has chosen
        // is whatever the system asked for — so moving it is always a change they can see.
        SettingRow(
            title = stringResource(R.string.settings_dark_theme),
            body = stringResource(R.string.settings_dark_theme_body),
            checked = uiState.theme.isDark(isSystemInDarkTheme()),
            onCheckedChange = onSetDarkTheme,
        )

        // The navigation bar sits over the end of the scroll; without this the last row ends
        // underneath it.
        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }
}

/**
 * Reduced motion the user can turn on, and cannot turn off while their device is asking for
 * it — so the row says why rather than swallowing the tap. The switch reads on either way,
 * because it describes what the app is doing, not which setting said so.
 */
@Composable
private fun ReducedMotionRow(override: Boolean, onSet: (Boolean) -> Unit) {
    val system = rememberSystemReducedMotion()
    SettingRow(
        title = stringResource(R.string.settings_reduced_motion),
        body = if (system) {
            stringResource(R.string.settings_reduced_motion_system)
        } else {
            stringResource(R.string.settings_reduced_motion_body)
        },
        checked = system || override,
        enabled = !system,
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

@Composable
private fun SettingRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 56.dp)
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
