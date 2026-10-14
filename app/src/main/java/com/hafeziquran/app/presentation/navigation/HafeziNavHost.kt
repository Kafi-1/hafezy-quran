package com.hafeziquran.app.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hafeziquran.app.presentation.components.BottomNavBar
import com.hafeziquran.app.presentation.components.TabRoute
import com.hafeziquran.app.presentation.screens.books.IslamicBooksScreen
import com.hafeziquran.app.presentation.screens.bookmarks.BookmarksScreen
import com.hafeziquran.app.presentation.screens.home.HomeScreen
import com.hafeziquran.app.presentation.screens.para.ParaListScreen
import com.hafeziquran.app.presentation.screens.reader.ReaderScreen
import com.hafeziquran.app.presentation.screens.search.SearchScreen
import com.hafeziquran.app.presentation.screens.settings.SettingsScreen
import com.hafeziquran.app.presentation.screens.surah.SurahListScreen
import com.hafeziquran.app.presentation.screens.tasbeeh.TasbeehScreen

private const val TRANSITION_MS = 280

/**
 * The app's navigation graph.
 *
 * The bottom bar is owned by this outer [Scaffold], not by the individual
 * screens, so it can be shown on exactly the tab routes and hidden the moment the
 * user opens the reader, a list, or settings.
 *
 * [WindowInsets] are zeroed on the outer Scaffold: the bottom bar manages its own
 * navigation-bar inset, and every destination deals with the status bar itself
 * (a Scaffold + TopAppBar for full screens, `statusBarsPadding` for the tab
 * headers that deliberately draw under it).
 */
@Composable
fun HafeziNavHost() {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Screen.bottomBarRoutes

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab -> navController.switchTab(tab) },
                    onSearchClicked = { navController.navigate(Screen.Search.route) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = {
                slideInHorizontally(tween(TRANSITION_MS)) { it / 6 } + fadeIn(tween(TRANSITION_MS))
            },
            exitTransition = { fadeOut(tween(TRANSITION_MS / 2)) },
            popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
            popExitTransition = {
                slideOutHorizontally(tween(TRANSITION_MS)) { it / 6 } + fadeOut(tween(TRANSITION_MS))
            },
        ) {
            // ---------- Tabs ----------
            composable(Screen.Home.route) {
                HomeScreen(
                    onResumeReading = { navController.navigate(Screen.Reader.createRoute()) },
                    onOpenReader = { page -> navController.navigate(Screen.Reader.createRoute(page)) },
                    onSurahList = { navController.navigate(Screen.SurahList.route) },
                    onParaList = { navController.navigate(Screen.ParaList.route) },
                    onPageSearch = { navController.navigate(Screen.Search.route) },
                    onBookmarks = { navController.switchTab(TabRoute.Bookmarks) },
                    onTasbeeh = { navController.switchTab(TabRoute.Tasbeeh) },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                )
            }

            composable(Screen.Bookmarks.route) {
                BookmarksScreen(
                    onOpenPage = { page -> navController.navigate(Screen.Reader.createRoute(page)) },
                )
            }

            composable(Screen.IslamicBooks.route) {
                IslamicBooksScreen(
                    onOpenBooks = { navController.navigate(Screen.Reader.createRoute(1)) },
                )
            }

            composable(Screen.Tasbeeh.route) {
                TasbeehScreen()
            }

            // ---------- Full-screen ----------
            composable(
                route = Screen.Reader.route,
                arguments = listOf(
                    navArgument(Screen.Reader.ARG_PAGE) {
                        type = NavType.IntType
                        defaultValue = Screen.Reader.NO_PAGE
                    },
                ),
            ) {
                // The page argument is read by ReaderViewModel from SavedStateHandle,
                // so this composable needs no navigation knowledge at all.
                ReaderScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.SurahList.route) {
                SurahListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPage = { page -> navController.navigate(Screen.Reader.createRoute(page)) },
                )
            }

            composable(Screen.ParaList.route) {
                ParaListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPage = { page -> navController.navigate(Screen.Reader.createRoute(page)) },
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPage = { page -> navController.navigate(Screen.Reader.createRoute(page)) },
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Standard tab navigation: never stack duplicates, keep one entry per tab, and
 * restore each tab's scroll position when returning to it.
 */
private fun NavHostController.switchTab(tab: TabRoute) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
