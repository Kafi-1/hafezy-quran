package com.hafeziquran.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hafeziquran.app.data.preferences.ThemeMode

private val OnLight = Color(0xFFFFFFFF)
private val OnDark = Color(0xFF0A0A0A)

/**
 * Builds a full Material colour scheme from a palette + light/dark flag.
 *
 * Kept as a function (not two `val`s) because there are six palettes: hardcoding
 * twelve schemes would be unmaintainable for no benefit.
 */
@Composable
private fun schemeFor(theme: AppColorTheme, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = theme.nightAccent,
            onPrimary = OnDark,
            primaryContainer = theme.primaryDark,
            onPrimaryContainer = OnLight,
            secondary = GoldOnDark,
            onSecondary = OnDark,
            secondaryContainer = GoldDark,
            onSecondaryContainer = OnLight,
            tertiary = theme.nightAccent,
            onTertiary = OnDark,
            background = NightBackground,
            onBackground = NightOnSurface,
            surface = NightSurface,
            onSurface = NightOnSurface,
            surfaceVariant = NightSurfaceVariant,
            onSurfaceVariant = NightOnSurfaceVariant,
            outline = NightOutline,
            outlineVariant = NightOutline,
        )
    } else {
        lightColorScheme(
            primary = theme.primary,
            onPrimary = OnLight,
            primaryContainer = theme.primaryDark,
            onPrimaryContainer = OnLight,
            secondary = GoldDark,
            onSecondary = OnLight,
            secondaryContainer = Gold,
            onSecondaryContainer = OnDark,
            tertiary = theme.primaryDark,
            onTertiary = OnLight,
            background = LightBackground,
            onBackground = LightOnSurface,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = LightOutline,
            outlineVariant = LightOutline,
        )
    }

/**
 * App-wide spacing tokens, exposed via CompositionLocal so screens share one
 * rhythm instead of scattering magic numbers.
 */
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

/**
 * Root theme. `dynamicColor` is intentionally never enabled — the brand palette
 * is the product's identity and must not be recoloured by the user's wallpaper.
 */
@Composable
fun HafeziQuranTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorTheme: AppColorTheme = AppColorTheme.Default,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val typography = remember { buildBanglaTypography(context) }
    val spacing = remember { Spacing() }
    val colorScheme = schemeFor(theme = colorTheme, dark = darkTheme)

    CompositionLocalProvider(LocalSpacing provides spacing) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
