// PlatformTextStyle / LineHeightStyle may still be flagged experimental
// depending on the Compose UI patch version; opt in defensively.
@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.hafeziquran.app.presentation.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Bangla type scale.
 *
 * The app ships `bangla.ttf` (SolaimanLipi Bold) inside `assets/`. We load it
 * through a [Typeface] rather than `res/font` so the exact asset named in the
 * spec is the one rendered — and so no experimental font APIs are required.
 *
 * SolaimanLipi is a *bold* face, so every weight maps to it; requesting lighter
 * weights would trigger a synthetic-thin fallback that looks worse.
 */
private const val BANGLA_ASSET = "bangla.ttf"

// Disabling legacy font padding makes Bangla matras sit tight against the line
// box — without it, conjuncts get clipped on some OEM ROMs.
private val tightPlatformStyle = PlatformTextStyle(includeFontPadding = false)
private val tightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun base(
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    platformStyle = tightPlatformStyle,
    lineHeightStyle = tightLineHeight,
)

/**
 * Builds the app [Typography]. Requires a [Context] only to reach the bundled
 * font asset; call it once from the theme root and remember the result.
 */
fun buildBanglaTypography(context: Context): Typography {
    val bangla = FontFamily(Typeface.createFromAsset(context.assets, BANGLA_ASSET))

    return Typography(
        displayLarge = base(52, 62, family = bangla),
        displayMedium = base(44, 54, family = bangla),
        displaySmall = base(36, 46, family = bangla),

        headlineLarge = base(30, 40, family = bangla),
        headlineMedium = base(26, 34, family = bangla),
        headlineSmall = base(22, 30, family = bangla),

        titleLarge = base(20, 28, family = bangla),
        titleMedium = base(17, 25, 0.1, family = bangla),
        titleSmall = base(15, 22, 0.1, family = bangla),

        bodyLarge = base(17, 26, 0.3, family = bangla),
        bodyMedium = base(15, 23, 0.25, family = bangla),
        bodySmall = base(13, 19, 0.3, family = bangla),

        labelLarge = base(15, 21, 0.1, family = bangla),
        labelMedium = base(13, 18, 0.3, family = bangla),
        labelSmall = base(11, 16, 0.4, family = bangla),
    )
}

/** Arabic glyphs for the Tasbeeh screen — system Naskh reads far better than the
 *  Bangla face, which has no Arabic coverage. */
val ArabicFontFamily: FontFamily = FontFamily.Serif
