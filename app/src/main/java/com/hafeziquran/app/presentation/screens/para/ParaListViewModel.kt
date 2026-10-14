package com.hafeziquran.app.presentation.screens.para

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.model.Para
import com.hafeziquran.app.data.repository.QuranIndexRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A para plus its resolved starting page. */
data class ParaRow(val para: Para, val page: Int)

data class ParaListUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val results: List<ParaRow> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ParaListViewModel @Inject constructor(
    private val indexRepository: QuranIndexRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<ParaListUiState> =
        combine(indexRepository.index, query) { resource, q ->
            val paras = (resource as? Resource.Success)?.data?.paras.orEmpty()
            ParaListUiState(
                isLoading = resource is Resource.Loading,
                query = q,
                results = paras.filterBy(q).map { ParaRow(it, indexRepository.resolvePage(it.page)) },
                errorMessage = (resource as? Resource.Error)?.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ParaListUiState(),
        )

    init {
        viewModelScope.launch { indexRepository.load() }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    private fun List<Para>.filterBy(query: String): List<Para> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return this
        return filter { para ->
            para.bangla.contains(trimmed, ignoreCase = true) ||
                para.number == trimmed.toIntOrNull()
        }
    }
}
