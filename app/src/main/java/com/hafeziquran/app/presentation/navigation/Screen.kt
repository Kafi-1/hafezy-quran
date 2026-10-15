package com.hafeziquran.app.presentation.navigation

/** Reader nav-argument contract, shared by the route builder and ViewModel. */
private const val READER_ARG_PAGE = "page"

/** Sentinel for "no explicit page — resume the last read page". */
private const val READER_NO_PAGE = -1

/**
 * Every destination in the app.
 *
 * Routes are plain strings so they survive process death (Navigation Compose
 * only guarantees string round-tripping for the back stack). Each destination is
 * an `object`, so `Screen.X.route` is valid — a `data class` would have made the
 * inherited `route` property unreachable from the type name.
 */
sealed class Screen(val route: String) {

    // --- Launch ---

    /** Animated launch screen; the graph's start destination. Never shown twice. */
    data object Splash : Screen("splash")

    // --- Bottom-bar tabs (the bar stays visible on exactly these) ---

    data object Home : Screen("home")

    data object Bookmarks : Screen("bookmarks")

    /** The "ইসলামিক বুক" tab. */
    data object IslamicBooks : Screen("islamic_books")

    data object Tasbeeh : Screen("tasbeeh")

    // --- Full-screen destinations (bar hidden) ---

    /**
     * The mushaf reader. Omit the page argument to resume the last read page, or
     * pass a 1-based number to jump straight there (from a bookmark, a surah, a
     * para, or "go to page").
     *
     * The pattern MUST contain the `{page}` placeholder. Registering the default
     * value into the pattern instead (`reader?page=-1`) creates a single *literal*
     * destination, so every `navigate("reader?page=N")` with N != -1 matches
     * nothing and throws `IllegalArgumentException: ... cannot be found in the
     * navigation graph` — which crashed the app on every surah/para/bookmark tap.
     */
    data object Reader : Screen("reader?$READER_ARG_PAGE={$READER_ARG_PAGE}") {
        const val ARG_PAGE = READER_ARG_PAGE
        const val NO_PAGE = READER_NO_PAGE

        fun createRoute(page: Int? = null): String =
            "reader?$ARG_PAGE=${page ?: NO_PAGE}"
    }

    data object SurahList : Screen("surah_list")

    data object ParaList : Screen("para_list")

    /** Opened by the centre button in the bottom bar. */
    data object Search : Screen("search")

    data object Settings : Screen("settings")

    /** In-app policy page (no external browser). */
    data object Privacy : Screen("privacy")

    /** In-app "about the app" page. */
    data object About : Screen("about")

    companion object {
        /** Routes on which the bottom navigation bar is shown. */
        val bottomBarRoutes: Set<String> = setOf(
            Home.route,
            Bookmarks.route,
            IslamicBooks.route,
            Tasbeeh.route,
        )
    }
}
