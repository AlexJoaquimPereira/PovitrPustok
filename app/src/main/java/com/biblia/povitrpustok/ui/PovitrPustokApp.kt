package com.biblia.povitrpustok.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.core.navigation.AppDestination
import com.biblia.povitrpustok.core.navigation.PovitrPustokNavHost
import com.biblia.povitrpustok.ui.adaptive.AppBreakpoints

/**
 * Application shell (F2-01/F2-02).
 *
 * The shell owns the single [NavHostController] and the adaptive navigation
 * suite, so the graph in [PovitrPustokNavHost] stays exactly the route contract
 * it already was. [MainActivity] only supplies the theme and this composable.
 *
 * The suite is visible only on [AppDestination.TOP_LEVEL] destinations:
 *
 *  - compact width (< 600 dp) uses the Material bottom navigation bar;
 *  - medium and expanded widths use the navigation rail;
 *  - detail screens (Reader, Search, Bookmarks, Daily Reading, Reference,
 *    Settings, About, chapters, introduction) render without the suite and
 *    keep their existing top bars and back handling.
 *
 * Tab taps use the standard top-level pattern — pop to the graph start with
 * saved state, single-top, restore state — so switching tabs never piles up
 * duplicate destinations and Back from a tab returns Home instead of exiting.
 */
@Composable
fun PovitrPustokApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Home is the graph start; treating the first frame as Home avoids a
    // one-frame suite flash before the NavHost reports its destination.
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.HOME

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        AppNavigationSuite(
            windowWidth = maxWidth,
            selectedRoute = currentRoute,
            onSelect = { route -> navController.navigateToTopLevel(route) }
        ) {
            PovitrPustokNavHost(navController = navController)
        }
    }
}

/**
 * Stateless suite wrapper, kept separate from the controller so it can be
 * exercised directly by Compose tests at both breakpoints.
 */
@Composable
internal fun AppNavigationSuite(
    windowWidth: Dp,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val layoutType = when {
        selectedRoute !in AppDestination.TOP_LEVEL -> NavigationSuiteType.None
        AppBreakpoints.usesNavigationRail(windowWidth) -> NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = layoutType,
        navigationSuiteItems = {
            item(
                selected = selectedRoute == AppDestination.HOME,
                onClick = { onSelect(AppDestination.HOME) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.nav_home)) },
                modifier = Modifier.testTag("nav.home")
            )
            item(
                selected = selectedRoute == AppDestination.BOOKS,
                onClick = { onSelect(AppDestination.BOOKS) },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.nav_bible)) },
                modifier = Modifier.testTag("nav.bible")
            )
            item(
                selected = selectedRoute == AppDestination.MORE,
                onClick = { onSelect(AppDestination.MORE) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.nav_more)) },
                modifier = Modifier.testTag("nav.more")
            )
        },
        content = content
    )
}

/**
 * Standard top-level navigation: pop back to Home while saving the outgoing
 * tab's state, keep one copy of the target, and restore it if it was saved.
 */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
