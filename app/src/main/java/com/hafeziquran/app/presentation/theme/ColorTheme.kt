package com.hafeziquran.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Selectable app palettes, mirroring the colour choices in the original app.
 *
 * The gold accent and the neutral paper/night surfaces are shared by every
 * palette; only the identity colour (header, buttons, active icons) changes.
 * That keeps all six options visually consistent while still feeling distinct.
 *
 * Persisted by [name], so entries must never be renamed without a migration.
 */
enum class AppColorTheme(
    val label: String,
    val primary: Color,
    val primaryDark: Color,
    /** Lifted variant used as `primary` in dark mode, where deep hues go muddy. */
    val nightAccent: Color,
) {
    GREEN("সবুজ", Color(0xFF0D7377), Color(0xFF0A5C5F), Color(0xFF5FCFD4)),
    EMERALD("গাঢ় সবুজ", Color(0xFF1B5E20), Color(0xFF104214), Color(0xFF7FD68A)),
    BLUE("নীল", Color(0xFF1565C0), Color(0xFF0D47A1), Color(0xFF7FB4F0)),
    INDIGO("নীলবর্ণ", Color(0xFF283593), Color(0xFF1A237E), Color(0xFF9FA8DA)),
    PURPLE("বেগুনি", Color(0xFF6A1B9A), Color(0xFF4A148C), Color(0xFFCE93D8)),
    BROWN("বাদামী", Color(0xFF5D4037), Color(0xFF3E2723), Color(0xFFBCAAA4));

    companion object {
        val Default = GREEN

        /** Unknown or missing names fall back to [Default] rather than throwing. */
        fun fromName(name: String?): AppColorTheme =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
