package com.hafeziquran.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide appearance setting. Persisted as its enum name for stability. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Mushaf page transition style. */
enum class ReadingMode { HORIZONTAL, VERTICAL }

/** Single DataStore instance for the whole process. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings",
)

/**
 * Thin, typed wrapper over DataStore.
 *
 * Only *preferences* live here. Reading position and bookmarks are relational
 * data and belong in Room (see [com.hafeziquran.app.data.repository]); keeping
 * that split stops DataStore from becoming a catch-all key-value drawer.
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val READER_NIGHT_MODE = booleanPreferencesKey("reader_night_mode")
        val TASBEEH_VIBRATION = booleanPreferencesKey("tasbeeh_vibration")
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { name ->
            runCatching { ThemeMode.valueOf(name) }.getOrNull()
        } ?: ThemeMode.SYSTEM
    }

    /**
     * Selected palette, stored by enum name.
     *
     * Deliberately a raw [String] rather than the enum type: the palette enum
     * holds Compose `Color`s and lives in the presentation layer, and the data
     * layer must not depend on that. The UI maps it via `AppColorTheme.fromName`.
     */
    val colorThemeName: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.COLOR_THEME]
    }

    val readingMode: Flow<ReadingMode> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.READING_MODE]?.let { name ->
            runCatching { ReadingMode.valueOf(name) }.getOrNull()
        } ?: ReadingMode.HORIZONTAL
    }

    val keepScreenOn: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.KEEP_SCREEN_ON] ?: true
    }

    /** Reader-specific dark veil, independent from the app theme. */
    val readerNightMode: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.READER_NIGHT_MODE] ?: false
    }

    val tasbeehVibration: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.TASBEEH_VIBRATION] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit(Keys.THEME_MODE, mode.name)

    suspend fun setColorThemeName(name: String) = edit(Keys.COLOR_THEME, name)

    suspend fun setReadingMode(mode: ReadingMode) = edit(Keys.READING_MODE, mode.name)

    suspend fun setKeepScreenOn(enabled: Boolean) = edit(Keys.KEEP_SCREEN_ON, enabled)

    suspend fun setReaderNightMode(enabled: Boolean) = edit(Keys.READER_NIGHT_MODE, enabled)

    suspend fun setTasbeehVibration(enabled: Boolean) = edit(Keys.TASBEEH_VIBRATION, enabled)

    private suspend fun <T> edit(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }
}
