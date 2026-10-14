package com.hafeziquran.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.BuildConfig
import com.hafeziquran.app.data.preferences.ThemeMode
import com.hafeziquran.app.data.preferences.UserPreferences
import com.hafeziquran.app.presentation.theme.AppColorTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Settings screen: theme + palette are the only editable state. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val colorTheme: StateFlow<AppColorTheme> = preferences.colorThemeName
        .map { AppColorTheme.fromName(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppColorTheme.Default)

    val versionName: String = BuildConfig.VERSION_NAME

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setColorTheme(theme: AppColorTheme) {
        viewModelScope.launch { preferences.setColorThemeName(theme.name) }
    }
}
