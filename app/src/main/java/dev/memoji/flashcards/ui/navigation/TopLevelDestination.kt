package dev.memoji.flashcards.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.ui.graphics.vector.ImageVector
import dev.memoji.flashcards.R

/** The three destinations reachable from the bottom navigation bar, in bar order. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DECKS(
        route = "decks",
        labelRes = R.string.destination_decks,
        selectedIcon = Icons.Filled.Style,
        unselectedIcon = Icons.Outlined.Style,
    ),
    PROGRESS(
        route = "progress",
        labelRes = R.string.destination_progress,
        selectedIcon = Icons.Filled.Insights,
        unselectedIcon = Icons.Outlined.Insights,
    ),
    SETTINGS(
        route = "settings",
        labelRes = R.string.destination_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
    ;

    companion object {
        /** Where the app opens. */
        val START = DECKS

        /** The destination owning [route], or null for a route deeper in the graph. */
        fun forRoute(route: String?): TopLevelDestination? = entries.find { it.route == route }
    }
}
