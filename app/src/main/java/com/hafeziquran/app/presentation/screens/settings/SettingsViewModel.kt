package com.hafeziquran.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.BuildConfig
import com.hafeziquran.app.data.preferences.ThemeMode
import com.hafeziquran.app.data.preferences.UserPreferences
import com.hafeziquran.app.data.repository.SobokModelRepository
import com.hafeziquran.app.presentation.theme.AppColorTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Downloaded state of the optional Sobok Whisper model. */
data class SobokModelState(
    val downloaded: Boolean = false,
    val sizeBytes: Long = 0L,
)

/** Backs the Settings screen: theme + palette and the optional Sobok model. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val sobokModelRepository: SobokModelRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val colorTheme: StateFlow<AppColorTheme> = preferences.colorThemeName
        .map { AppColorTheme.fromName(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppColorTheme.Default)

    private val _sobokModel = MutableStateFlow(SobokModelState())
    val sobokModel: StateFlow<SobokModelState> = _sobokModel.asStateFlow()

    val versionName: String = BuildConfig.VERSION_NAME

    init {
        refreshSobokModel()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setColorTheme(theme: AppColorTheme) {
        viewModelScope.launch { preferences.setColorThemeName(theme.name) }
    }

    fun deleteSobokModel() {
        viewModelScope.launch {
            sobokModelRepository.delete()
            refreshSobokModel()
        }
    }

    private fun refreshSobokModel() {
        _sobokModel.value = SobokModelState(
            downloaded = sobokModelRepository.isDownloaded(),
            sizeBytes = sobokModelRepository.sizeBytes(),
        )
    }
}
