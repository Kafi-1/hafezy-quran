package com.hafeziquran.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.data.preferences.ThemeMode
import com.hafeziquran.app.data.preferences.UserPreferences
import com.hafeziquran.app.presentation.theme.AppColorTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Process-level state the Activity needs before any screen exists — the theme
 * and palette, so the very first frame is already the right colour.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    preferences: UserPreferences,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.SYSTEM,
        )

    val colorTheme: StateFlow<AppColorTheme> = preferences.colorThemeName
        .map { AppColorTheme.fromName(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppColorTheme.Default,
        )
}
