package com.hafeziquran.app.presentation.screens.banglaquran

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.pdf.PdfFileRenderer
import com.hafeziquran.app.data.preferences.UserPreferences
import com.hafeziquran.app.data.repository.BanglaQuranRepository
import com.hafeziquran.app.presentation.screens.pdfviewer.PdfFileViewer
import com.hafeziquran.app.presentation.screens.pdfviewer.PdfFileViewerState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Viewer for the downloaded Bangla translation. Mirrors
 * [com.hafeziquran.app.presentation.screens.reader.ReaderViewModel] but drives a
 * [PdfFileRenderer] over the user's own file instead of the bundled asset, and
 * keeps its bookmarks/last-page in DataStore so they never collide with the
 * mushaf reader's page numbers.
 */
@HiltViewModel
class BanglaQuranViewerViewModel @Inject constructor(
    private val repository: BanglaQuranRepository,
    private val pdfiumCore: PdfiumCore,
    private val preferences: UserPreferences,
) : ViewModel(), PdfFileViewer {

    private val _uiState = MutableStateFlow(PdfFileViewerState())
    override val uiState: StateFlow<PdfFileViewerState> = _uiState.asStateFlow()

    private val currentPage = MutableStateFlow(1)

    @Volatile
    private var renderer: PdfFileRenderer? = null

    init {
        observeNightMode()
        observeBookmarkState()
        openDocument()
    }

    private fun observeNightMode() {
        viewModelScope.launch {
            preferences.readerNightMode.collect { enabled ->
                _uiState.update { it.copy(nightMode = enabled) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeBookmarkState() {
        viewModelScope.launch {
            currentPage
                .flatMapLatest { page -> preferences.banglaBookmarks.map { page in it } }
                .collect { bookmarked -> _uiState.update { it.copy(isBookmarked = bookmarked) } }
        }
    }

    private fun openDocument() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = runCatching {
                val newRenderer = PdfFileRenderer(repository.file, pdfiumCore)
                val count = newRenderer.open()
                val toc = newRenderer.tableOfContents()
                renderer = newRenderer
                Triple(count, toc, newRenderer.pageAspectRatio)
            }

            result.fold(
                onSuccess = { (count, toc, ratio) ->
                    if (count <= 0) {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "ফাইলটি খালি মনে হচ্ছে")
                        }
                        return@fold
                    }
                    val startPage = preferences.banglaLastPage.first().coerceIn(1, count)
                    currentPage.value = startPage
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pageCount = count,
                            currentPage = startPage,
                            toc = toc,
                            pageAspectRatio = ratio,
                        )
                    }
                },
                onFailure = { throwable ->
                    Log.e(TAG, "Bangla Quran open failed", throwable)
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "বাংলা কোরআন খোলা যায়নি")
                    }
                },
            )
        }
    }

    /** Never throws: a bad index or a dead Pdfium handle becomes a Bangla error. */
    override suspend fun renderPage(pageIndex: Int, widthPx: Int): Resource<Bitmap> = try {
        val current = renderer ?: error("Renderer not ready")
        Resource.Success(withContext(Dispatchers.IO) { current.renderPage(pageIndex, widthPx) })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Log.e(TAG, "renderPage(index=$pageIndex) failed", throwable)
        Resource.Error(throwable, "পৃষ্ঠা লোড করা যায়নি")
    }

    override fun prefetchNeighbours(page: Int, widthPx: Int) {
        val current = renderer ?: return
        val index = page - 1
        viewModelScope.launch {
            current.prefetch(index - 1, widthPx)
            current.prefetch(index + 1, widthPx)
        }
    }

    override fun onPageSettled(page: Int) {
        if (currentPage.value == page) return
        currentPage.value = page
        _uiState.update { it.copy(currentPage = page) }
        viewModelScope.launch { preferences.setBanglaLastPage(page) }
    }

    override fun toggleBookmark() {
        val page = currentPage.value
        viewModelScope.launch { preferences.toggleBanglaBookmark(page) }
    }

    override fun setNightMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setReaderNightMode(enabled) }
    }

    override fun requestJump(page: Int) {
        _uiState.update { state ->
            if (state.pageCount <= 0) {
                state
            } else {
                state.copy(jumpRequest = page.coerceIn(1, state.pageCount))
            }
        }
    }

    override fun consumeJumpRequest() {
        _uiState.update { it.copy(jumpRequest = null) }
    }

    override fun retry() = openDocument()

    override fun onCleared() {
        renderer?.close()
        renderer = null
        super.onCleared()
    }

    private companion object {
        const val TAG = "BanglaQuranViewerVM"
    }
}
