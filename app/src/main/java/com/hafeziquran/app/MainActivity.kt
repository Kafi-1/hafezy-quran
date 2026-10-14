package com.hafeziquran.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafeziquran.app.data.preferences.ThemeMode
import com.hafeziquran.app.presentation.navigation.HafeziNavHost
import com.hafeziquran.app.presentation.theme.HafeziQuranTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. All UI is Compose; the Activity only wires the theme,
 * edge-to-edge insets and the navigation graph.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the splash icon animates out of the
        // window background rather than flashing.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            ApplySystemBarAppearance(darkTheme = darkTheme)

            HafeziQuranTheme(themeMode = themeMode, colorTheme = colorTheme) {
                HafeziNavHost()
            }
        }
    }
}

/**
 * Keeps status/navigation bar icons legible against the app background.
 * Edge-to-edge means the bars are transparent, so the *icons* are the only
 * thing we control.
 */
@androidx.compose.runtime.Composable
private fun ApplySystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}
