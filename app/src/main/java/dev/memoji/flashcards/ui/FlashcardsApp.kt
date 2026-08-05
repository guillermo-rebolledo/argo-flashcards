package dev.memoji.flashcards.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.memoji.flashcards.feature.deckdetail.DeckDetailRoute
import dev.memoji.flashcards.feature.deckdetail.DeckDetailScreen
import dev.memoji.flashcards.feature.decks.DecksScreen
import dev.memoji.flashcards.feature.progress.ProgressScreen
import dev.memoji.flashcards.feature.session.SessionRoute
import dev.memoji.flashcards.feature.session.SessionScreen
import dev.memoji.flashcards.feature.settings.SettingsScreen
import dev.memoji.flashcards.ui.navigation.TopLevelDestination

@Composable
fun FlashcardsApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = TopLevelDestination.forRoute(backStackEntry?.destination?.route)
    // Null before the graph has attached, which is the start destination on its way in — a tab.
    val onTopLevel = backStackEntry == null || currentDestination != null

    Scaffold(
        bottomBar = {
            // Only the three tabs carry the bar. A Deck opened from the list is a screen the
            // user came into and backs out of, not a fourth place to switch between.
            if (onTopLevel) {
                FlashcardsNavigationBar(
                    currentDestination = currentDestination,
                    onSelect = navController::navigateToTopLevel,
                )
            }
        },
    ) { innerPadding ->
        // Scaffold reports the insets the bars occupy; the NavHost hands them to each screen so
        // nothing renders underneath the status or navigation bar.
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.START.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(TopLevelDestination.DECKS.route) {
                DecksScreen(
                    contentPadding = innerPadding,
                    onOpenDeck = { navController.navigate(DeckDetailRoute.of(it)) },
                    onStartSession = { navController.navigate(SessionRoute.of(it)) },
                )
            }
            composable(TopLevelDestination.PROGRESS.route) { ProgressScreen(innerPadding) }
            composable(TopLevelDestination.SETTINGS.route) { SettingsScreen(innerPadding) }
            composable(
                route = DeckDetailRoute.PATTERN,
                arguments = listOf(
                    navArgument(DeckDetailRoute.DECK_ID_ARG) { type = NavType.LongType },
                ),
            ) {
                DeckDetailScreen(
                    contentPadding = innerPadding,
                    onStartSession = { navController.navigate(SessionRoute.of(it)) },
                    // popBackStack rather than navigateUp: this is also how a deleted Deck
                    // leaves, and there is no up-hierarchy to walk beyond the list.
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = SessionRoute.PATTERN,
                arguments = listOf(
                    navArgument(SessionRoute.DECK_ID_ARG) { type = NavType.LongType },
                ),
            ) {
                SessionScreen(
                    contentPadding = innerPadding,
                    // Finishing returns wherever the Session was started from — the Deck
                    // list, or the Deck itself — rather than to a fixed screen.
                    onFinish = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun FlashcardsNavigationBar(
    currentDestination: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == currentDestination
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

/**
 * Switching tabs replaces the current tab rather than stacking, and remembers where each tab
 * was left, so Back from any tab leaves the app rather than walking a history of taps.
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
