package dev.memoji.flashcards.feature.progress

import dev.memoji.flashcards.core.model.ProgressSummary

internal sealed interface ProgressUiState {

    data object Loading : ProgressUiState

    /**
     * No Session has been finished yet. A screen of zeroes would be the app's first word to a
     * new user being a report of everything they have not done.
     */
    data object Empty : ProgressUiState

    /**
     * [hideDayStreak] takes the streak and the seven-day grid off the screen — and with them the
     * line about the day that was skipped, which is only meaningful next to a streak. What is
     * left is what the user did, with nothing counting up.
     */
    data class Summary(
        val summary: ProgressSummary,
        val hideDayStreak: Boolean,
    ) : ProgressUiState
}
