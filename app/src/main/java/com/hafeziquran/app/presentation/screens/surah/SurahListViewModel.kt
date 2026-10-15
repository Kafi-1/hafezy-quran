package com.hafeziquran.app.presentation.screens.surah

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.core.util.toIntOrNullBangla
import com.hafeziquran.app.data.model.Surah
import com.hafeziquran.app.data.repository.QuranIndexRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A surah plus the page it starts on, with the index offset already applied. */
data class SurahRow(val surah: Surah, val page: Int)

data class SurahListUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val results: List<SurahRow> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class SurahListViewModel @Inject constructor(
    private val indexRepository: QuranIndexRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<SurahListUiState> =
        combine(indexRepository.index, query) { resource, q ->
            val surahs = (resource as? Resource.Success)?.data?.surahs.orEmpty()
            SurahListUiState(
                isLoading = resource is Resource.Loading,
                query = q,
                results = surahs.filterBy(q).map { SurahRow(it, indexRepository.resolvePage(it.page)) },
                errorMessage = (resource as? Resource.Error)?.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SurahListUiState(),
        )

    init {
        viewModelScope.launch { indexRepository.load() }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /**
     * Matches on the Bangla name, the meaning, or an exact surah number — the
     * three things a user actually types. The number accepts Bengali digits too,
     * so `২` and `2` both find সূরা আল-বাকারা. Arabic is excluded: the bundled font
     * has no Arabic coverage, and nobody searches a Bangla UI by Arabic script.
     */
    private fun List<Surah>.filterBy(query: String): List<Surah> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return this
        val number = trimmed.toIntOrNullBangla()
        return filter { surah ->
            surah.bangla.contains(trimmed, ignoreCase = true) ||
                surah.meaning.contains(trimmed, ignoreCase = true) ||
                (number != null && surah.number == number)
        }
    }
}
