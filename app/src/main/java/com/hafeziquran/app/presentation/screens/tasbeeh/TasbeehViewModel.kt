package com.hafeziquran.app.presentation.screens.tasbeeh

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.sound.ClickSoundPlayer
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.core.util.vibrateRoundComplete
import com.hafeziquran.app.core.util.vibrateTick
import com.hafeziquran.app.data.model.TasbeehItem
import com.hafeziquran.app.data.preferences.UserPreferences
import com.hafeziquran.app.data.repository.TasbeehRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The selectable knobs of a dhikr session, in the order the original app shows. */
val TasbeehTargets: List<Int> = listOf(33, 99, 100, 500, 1000)

data class TasbeehUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val zikrList: List<TasbeehItem> = emptyList(),
    val selectedIndex: Int = 0,
    /** Taps in the current round, always in `0 until target`. */
    val count: Int = 0,
    val target: Int = 33,
    val rounds: Int = 0,
    /** Every tap since the last reset, across all rounds. */
    val totalCount: Int = 0,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
) {
    val currentZikr: TasbeehItem? get() = zikrList.getOrNull(selectedIndex)
}

@HiltViewModel
class TasbeehViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TasbeehRepository,
    private val preferences: UserPreferences,
    private val clickSound: ClickSoundPlayer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasbeehUiState())
    val uiState: StateFlow<TasbeehUiState> = _uiState.asStateFlow()

    init {
        loadZikr()
        viewModelScope.launch {
            preferences.tasbeehVibration.collect { enabled ->
                _uiState.update { it.copy(vibrationEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferences.tasbeehSound.collect { enabled ->
                _uiState.update { it.copy(soundEnabled = enabled) }
            }
        }
    }

    private fun loadZikr() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.loadZikrList()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, zikrList = result.data)
                }

                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }

                Resource.Loading -> Unit
            }
        }
    }

    /**
     * One tap of the counter button. Rolling the count to 0 and bumping [rounds]
     * in the same update keeps the displayed number from ever flashing [target].
     */
    fun onTap() {
        val before = _uiState.value
        val next = before.count + 1
        val roundComplete = next >= before.target

        _uiState.update {
            it.copy(
                count = if (roundComplete) 0 else next,
                rounds = if (roundComplete) it.rounds + 1 else it.rounds,
                totalCount = it.totalCount + 1,
            )
        }

        if (before.soundEnabled) clickSound.play()
        if (before.vibrationEnabled) {
            if (roundComplete) context.vibrateRoundComplete() else context.vibrateTick()
        }
    }

    fun selectTarget(target: Int) {
        // Restart the round so the progress ring can't exceed the new target.
        _uiState.update { it.copy(target = target, count = 0) }
    }

    fun selectZikr(index: Int) {
        _uiState.update { it.copy(selectedIndex = index, count = 0, rounds = 0, totalCount = 0) }
    }

    fun reset() {
        _uiState.update { it.copy(count = 0, rounds = 0, totalCount = 0) }
    }

    fun setVibration(enabled: Boolean) {
        viewModelScope.launch { preferences.setTasbeehVibration(enabled) }
    }

    fun setSound(enabled: Boolean) {
        viewModelScope.launch { preferences.setTasbeehSound(enabled) }
    }
}
