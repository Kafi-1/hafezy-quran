package com.hafeziquran.app.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.model.Surah
import com.hafeziquran.app.data.pdf.PdfPageRenderer
import com.hafeziquran.app.data.repository.QuranIndexRepository
import com.hafeziquran.app.presentation.screens.surah.SurahRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val surahResults: List<SurahRow> = emptyList(),
    /** Set when the query is a valid page number, so the UI can offer a jump. */
    val pageJump: Int? = null,
    val errorMessage: String? = null,
)

/**
 * Backs the centre "সার্চ" button. One field does double duty, exactly as in the
 * original app: type a name to filter the 114 surahs, or type a number to jump
 * straight to that mushaf page.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val indexRepository: QuranIndexRepository,
    private val pdfRenderer: PdfPageRenderer,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val pageCount = MutableStateFlow(0)

    val state: StateFlow<SearchUiState> =
        combine(indexRepository.index, query, pageCount) { resource, q, pages ->
            val surahs = (resource as? Resource.Success)?.data?.surahs.orEmpty()
            // Pdfium's count wins once known; until then fall back to the index's
            // own pagination so the "jump to page" prompt works on first frame —
            // the reader re-clamps against the real document anyway.
            val resolvedPages = pages.takeIf { it > 0 } ?: FALLBACK_TOTAL_PAGES
            SearchUiState(
                isLoading = resource is Resource.Loading,
                query = q,
                surahResults = surahs.filterBy(q)
                    .map { SurahRow(it, indexRepository.resolvePage(it.page)) },
                pageJump = q.trim().toIntOrNull()?.takeIf { it in 1..resolvedPages },
                errorMessage = (resource as? Resource.Error)?.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState(),
        )

    init {
        viewModelScope.launch { indexRepository.load() }
        viewModelScope.launch {
            runCatching { pdfRenderer.open() }.onSuccess { pageCount.value = it }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    private fun List<Surah>.filterBy(query: String): List<Surah> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return this
        return filter { surah ->
            surah.bangla.contains(trimmed, ignoreCase = true) ||
                surah.meaning.contains(trimmed, ignoreCase = true) ||
                surah.number == trimmed.toIntOrNull()
        }
    }

    private companion object {
        /** Physical page count of the bundled scan; replaced by Pdfium once open. */
        const val FALLBACK_TOTAL_PAGES = 612
    }
}
