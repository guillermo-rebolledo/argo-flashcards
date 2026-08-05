package dev.memoji.flashcards.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.model.DeckProgress
import dev.memoji.flashcards.core.model.ProgressDay
import dev.memoji.flashcards.ui.component.EmptyState
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ProgressScreen(contentPadding: PaddingValues) {
    val viewModel: ProgressViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressScreen(uiState = uiState, contentPadding = contentPadding)
}

@Composable
internal fun ProgressScreen(uiState: ProgressUiState, contentPadding: PaddingValues) {
    when (uiState) {
        // The log is one local read away. A spinner that appears for a frame is worse than a
        // screen that fills in on the next one.
        ProgressUiState.Loading -> Unit
        ProgressUiState.Empty -> Empty(contentPadding)
        is ProgressUiState.Summary -> Summary(uiState = uiState, contentPadding = contentPadding)
    }
}

@Composable
private fun Summary(uiState: ProgressUiState.Summary, contentPadding: PaddingValues) {
    val summary = uiState.summary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle()

        if (!uiState.hideStreak) {
            StreakCard(dayStreak = summary.dayStreak, week = summary.week)
        }

        SectionHeader(stringResource(R.string.progress_last_seven_days))
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Tile(summary.cardsReviewed, stringResource(R.string.progress_tile_cards))
            Tile(summary.minutes, stringResource(R.string.progress_tile_minutes))
            Tile(summary.decksTouched, stringResource(R.string.progress_tile_decks))
        }

        // Hidden along with the streak: this is the streak holding a place, and a screen asked
        // to stop counting must not still be pointing at the day that was missed.
        val skippedDay = summary.skippedDay
        if (!uiState.hideStreak && skippedDay != null) {
            SkippedDayCard(day = skippedDay)
        }

        if (summary.decks.isNotEmpty()) {
            SectionHeader(stringResource(R.string.progress_decks_this_week))
            summary.decks.forEach { deck -> DeckRow(deck) }
        }

        // The navigation bar sits over the end of the scroll; without this the last row ends
        // underneath it.
        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }
}

/**
 * Before the first Session there is nothing to count, and a screen of zeroes would make the
 * app's first word to a new user a report of everything they have not done.
 */
@Composable
private fun Empty(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        ScreenTitle()
        EmptyState(
            title = stringResource(R.string.progress_empty_title),
            body = stringResource(R.string.progress_empty_body),
            bottomPadding = 0.dp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ScreenTitle() {
    Text(
        text = stringResource(R.string.destination_progress),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
    )
}

/**
 * The number of days, and the week behind it, in the one place the design gives the accent
 * colour. It is the only thing on the screen that counts, which is why it is also the one thing
 * the user can switch off.
 */
@Composable
private fun StreakCard(dayStreak: Int, week: List<ProgressDay>) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    text = pluralStringResource(
                        R.plurals.progress_streak_days,
                        dayStreak,
                        dayStreak,
                    ),
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    text = stringResource(R.string.progress_streak_caption),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            WeekRow(week)
        }
    }
}

/** Seven dots, oldest first, filled on the days that were studied. */
@Composable
private fun WeekRow(week: List<ProgressDay>) {
    val locale = currentLocale()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        week.forEach { day ->
            val description = stringResource(
                if (day.studied) {
                    R.string.progress_day_studied
                } else {
                    R.string.progress_day_not_studied
                },
                day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    // Read out as one thing rather than as a shape and then a letter:
                    // "Tuesday, studied" is what a dot means to someone who cannot see the row.
                    .semantics(mergeDescendants = true) { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(
                            color = if (day.studied) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                Color.Transparent
                            },
                            shape = CircleShape,
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    // Said already by the description on the column above.
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
private fun RowScope.Tile(value: Int, label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.weight(1f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = value.toString(), style = MaterialTheme.typography.titleLarge)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The reason this screen is not a scoreboard. It names the day and then says the streak is
 * holding — nothing here is allowed to read as a telling-off.
 */
@Composable
private fun SkippedDayCard(day: LocalDate) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.progress_skipped_title,
                    day.dayOfWeek.getDisplayName(TextStyle.FULL, currentLocale()),
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.progress_skipped_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The Cards are what was reviewed this week, not what the Deck holds. */
@Composable
private fun DeckRow(deck: DeckProgress) {
    ListItem(
        headlineContent = { Text(deck.name) },
        trailingContent = {
            Text(
                text = pluralStringResource(
                    R.plurals.decks_card_count,
                    deck.cardsReviewed,
                    deck.cardsReviewed,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** The device's language, which is what the weekday names are written in. */
@Composable
private fun currentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) { configuration.locales[0] }
}
