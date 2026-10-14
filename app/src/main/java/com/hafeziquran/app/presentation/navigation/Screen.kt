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
     */
    data object Reader : Screen("reader?$READER_ARG_PAGE=$READER_NO_PAGE") {
        const val ARG_PAGE = READER_ARG_PAGE
        const val NO_PAGE = READER_NO_PAGE

        fun createRoute(page: Int? = null): String =
            if (page == null) {
                "reader?$ARG_PAGE=$NO_PAGE"
            } else {
                "reader?$ARG_PAGE=$page"
            }
    }

    data object SurahList : Screen("surah_list")

    data object ParaList : Screen("para_list")

    /** Opened by the centre button in the bottom bar. */
    data object Search : Screen("search")

    data object Settings : Screen("settings")

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
