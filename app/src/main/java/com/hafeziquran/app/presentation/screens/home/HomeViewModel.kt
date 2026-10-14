package com.hafeziquran.app.presentation.screens.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.model.Para
import com.hafeziquran.app.data.model.Surah
import com.hafeziquran.app.data.pdf.PdfPageRenderer
import com.hafeziquran.app.data.repository.QuranIndexRepository
import com.hafeziquran.app.data.repository.ReadingPositionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A shortcut shown in the horizontal strip under the continue-reading card. */
data class FeaturedLink(
    val label: String,
    /** Resolved page, ready to hand to the reader. */
    val page: Int,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val lastReadPage: Int? = null,
    val lastReadSurah: Surah? = null,
    val lastReadPara: Para? = null,
    val featured: List<FeaturedLink> = emptyList(),
    val totalPages: Int = 0,
    val readProgress: Float = 0f,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val indexRepository: QuranIndexRepository,
    private val readingPositionRepository: ReadingPositionRepository,
    private val pdfRenderer: PdfPageRenderer,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        observeReadingState()
        loadIndex()
        loadPageCount()
    }

    /**
     * Recomputes the "current position" whenever the saved page or the index
     * changes. The surah/para are *derived*, never stored, so they can't go stale.
     */
    private fun observeReadingState() {
        viewModelScope.launch {
            combine(
                readingPositionRepository.observeLastPage(),
                indexRepository.index,
            ) { page, indexResource -> page to indexResource }
                .collect { (page, indexResource) ->
                    val index = (indexResource as? Resource.Success)?.data

                    val surahs = index?.surahs.orEmpty()
                    val paras = index?.paras.orEmpty()

                    // Last surah/para whose start page is at or before the reader.
                    val currentSurah = page?.let { p ->
                        surahs.lastOrNull { indexRepository.resolvePage(it.page) <= p } ?: surahs.firstOrNull()
                    }
                    val currentPara = page?.let { p ->
                        paras.lastOrNull { indexRepository.resolvePage(it.page) <= p } ?: paras.firstOrNull()
                    }

                    _state.update { state ->
                        state.copy(
                            lastReadPage = page,
                            lastReadSurah = currentSurah,
                            lastReadPara = currentPara,
                            featured = buildFeatured(index?.surahs.orEmpty()),
                            // Index loaded ⇒ we have enough to render the screen.
                            isLoading = state.totalPages == 0 && index == null,
                            readProgress = progressOf(page),
                        )
                    }
                }
        }
    }

    private fun loadIndex() {
        viewModelScope.launch {
            val result = indexRepository.load()
            if (result is Resource.Error) {
                _state.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    /**
     * Opens the document just far enough to learn the true page count.
     *
     * Worth the one-off asset extraction: it makes the progress bar accurate and
     * means the reader opens without its own spinner on first use.
     */
    private fun loadPageCount() {
        viewModelScope.launch {
            val count = runCatching { pdfRenderer.open() }.getOrDefault(0)
            _state.update { state ->
                state.copy(
                    totalPages = count,
                    isLoading = false,
                    readProgress = progressOf(state.lastReadPage),
                )
            }
        }
    }

    private fun progressOf(page: Int?): Float {
        val total = _state.value.totalPages.takeIf { it > 0 }
            ?: FALLBACK_TOTAL_PAGES
        return ((page ?: 0).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /** Turns the curated list of shortcuts into resolved, ready-to-open pages. */
    private fun buildFeatured(surahs: List<Surah>): List<FeaturedLink> =
        FEATURED_SURAHS.mapNotNull { (label, surahNumber, pageOverride) ->
            val surah = surahs.firstOrNull { it.number == surahNumber }
            val rawPage = pageOverride ?: surah?.page
            rawPage?.let { FeaturedLink(label, indexRepository.resolvePage(it)) }
        }

    /**
     * Renders a small thumbnail of [page] for the "continue reading" card.
     */
    suspend fun renderPreview(page: Int, widthPx: Int): Resource<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                val index = (page - 1).coerceAtLeast(0)
                if (pdfRenderer.pageCount == 0) pdfRenderer.open()
                pdfRenderer.renderPage(index, widthPx)
            }.fold(
                onSuccess = { Resource.Success(it) },
                onFailure = { Resource.Error(it, "প্রিভিউ লোড করা যায়নি") },
            )
        }

    private companion object {
        /** Used for the progress bar only until Pdfium reports the real count. */
        const val FALLBACK_TOTAL_PAGES = 604

        /**
         * The shortcut strip. `pageOverride` exists for আয়াতুল কুরসি, which is a
         * single ayah inside আল-বাকারা rather than a surah of its own.
         */
        val FEATURED_SURAHS: List<Triple<String, Int, Int?>> = listOf(
            Triple("সূরা ইয়াসীন", 36, null),
            Triple("আয়াতুল কুরসি", 2, 42),
            Triple("সূরা কাহাফ", 18, null),
            Triple("সূরা আর-রহমান", 55, null),
            Triple("সূরা ওয়াকিয়া", 56, null),
            Triple("সূরা মূলক", 67, null),
            Triple("সূরা নাবা", 78, null),
        )
    }
}
