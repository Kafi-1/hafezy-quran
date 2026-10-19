package com.hafeziquran.app.presentation.navigation

import android.util.Log
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
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.presentation.components.BottomNavBar
import com.hafeziquran.app.presentation.components.TabRoute
import com.hafeziquran.app.presentation.screens.audioquran.AudioQuranScreen
import com.hafeziquran.app.presentation.screens.books.IslamicBooksScreen
import com.hafeziquran.app.presentation.screens.banglaquran.BanglaQuranScreen
import com.hafeziquran.app.presentation.screens.banglaquran.BanglaQuranViewerScreen
import com.hafeziquran.app.presentation.screens.bookmarks.BookmarksScreen
import com.hafeziquran.app.presentation.screens.home.HomeScreen
import com.hafeziquran.app.presentation.screens.info.InfoScreen
import com.hafeziquran.app.presentation.screens.info.InfoSection
import com.hafeziquran.app.presentation.screens.para.ParaListScreen
import com.hafeziquran.app.presentation.screens.reader.ReaderScreen
import com.hafeziquran.app.presentation.screens.search.SearchScreen
import com.hafeziquran.app.presentation.screens.settings.SettingsScreen
import com.hafeziquran.app.presentation.screens.sobok.SobokScreen
import com.hafeziquran.app.presentation.screens.splash.SplashScreen
import com.hafeziquran.app.presentation.screens.surah.SurahListScreen
import com.hafeziquran.app.presentation.screens.tasbeeh.TasbeehScreen

private const val TRANSITION_MS = 280

/** Slower than the standard transition so the splash dissolves gently into Home. */
private const val SPLASH_FADE_MS = 480

private const val TAG = "HafeziNavHost"

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
            startDestination = Screen.Splash.route,
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
            // ---------- Launch ----------
            composable(
                route = Screen.Splash.route,
                // The splash must not slide away like a pushed screen; it
                // dissolves into Home.
                exitTransition = { fadeOut(tween(SPLASH_FADE_MS)) },
                popExitTransition = { fadeOut(tween(SPLASH_FADE_MS)) },
            ) {
                SplashScreen(
                    onFinished = {
                        navController.navigate(Screen.Home.route) {
                            // Drop the splash so Back from Home exits the app.
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                )
            }

            // ---------- Tabs ----------
            composable(Screen.Home.route) {
                HomeScreen(
                    onResumeReading = { navController.openReader() },
                    onOpenReader = { page -> navController.openReader(page) },
                    onSurahList = { navController.navigate(Screen.SurahList.route) },
                    onParaList = { navController.navigate(Screen.ParaList.route) },
                    onPageSearch = { navController.navigate(Screen.Search.route) },
                    onBookmarks = { navController.switchTab(TabRoute.Bookmarks) },
                    onTasbeeh = { navController.switchTab(TabRoute.Tasbeeh) },
                    onBanglaQuran = { navController.navigate(Screen.BanglaQuran.route) },
                    onAudioQuran = { navController.navigate(Screen.AudioQuran.route) },
                    onSobok = { navController.navigate(Screen.Sobok.route) },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                )
            }

            composable(Screen.Bookmarks.route) {
                BookmarksScreen(
                    onOpenPage = { page -> navController.openReader(page) },
                )
            }

            composable(Screen.IslamicBooks.route) {
                IslamicBooksScreen(
                    onOpenBook = { book ->
                        // Only the bundled mushaf is readable today. Every other
                        // catalogue entry has no PDF in assets yet and is shown
                        // disabled in the list, so it can never reach this point.
                        if (book.asset == AppConstants.QURAN_PDF_ASSET) {
                            navController.openReader()
                        }
                    },
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
                    onOpenPage = { page -> navController.openReader(page) },
                )
            }

            composable(Screen.ParaList.route) {
                ParaListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPage = { page -> navController.openReader(page) },
                )
            }

            composable(Screen.BanglaQuran.route) {
                BanglaQuranScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(Screen.BanglaQuranViewer.route) },
                )
            }

            composable(Screen.BanglaQuranViewer.route) {
                BanglaQuranViewerScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.AudioQuran.route) {
                AudioQuranScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Sobok.route) {
                SobokScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPage = { page -> navController.openReader(page) },
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPrivacy = { navController.navigate(Screen.Privacy.route) },
                    onOpenAbout = { navController.navigate(Screen.About.route) },
                )
            }

            composable(Screen.Privacy.route) {
                InfoScreen(section = InfoSection.PRIVACY, onBack = { navController.popBackStack() })
            }

            composable(Screen.About.route) {
                InfoScreen(section = InfoSection.ABOUT, onBack = { navController.popBackStack() })
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

/**
 * Opens the reader, clamped to the document's real page range.
 *
 * Every page-bearing entry point (surah, para, bookmark, featured shortcut,
 * search, "continue reading") funnels through here, so a bad index entry or a
 * stale bookmark can never hand the graph a page it cannot resolve. If the
 * navigation still throws — e.g. the destination is missing — we log the exact
 * exception and fall back to resuming the last read page instead of letting the
 * crash reach the user.
 */
private fun NavHostController.openReader(page: Int? = null) {
    val safePage = page?.coerceIn(1, AppConstants.PHYSICAL_PAGE_COUNT)
    val route = Screen.Reader.createRoute(safePage)
    runCatching { navigate(route) }.onFailure { throwable ->
        Log.e(TAG, "openReader(page=$page) failed for route '$route'", throwable)
        runCatching { navigate(Screen.Reader.createRoute()) }
            .onFailure { Log.e(TAG, "openReader fallback failed too", it) }
    }
}
