package com.hafeziquran.app.presentation.screens.books

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.pdf.PdfFileRenderer
import com.hafeziquran.app.data.preferences.UserPreferences
import com.hafeziquran.app.data.repository.BookRepository
import com.hafeziquran.app.presentation.navigation.Screen
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
 * Viewer for a downloaded "ইসলামিক বুক" entry. Resolves the book from the
 * `bookId` nav argument, opens its private-storage PDF through a
 * [PdfFileRenderer], and keeps per-book bookmarks/last-page in DataStore so they
 * stay separate from every other document.
 */
@HiltViewModel
class BookViewerViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val pdfiumCore: PdfiumCore,
    private val preferences: UserPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), PdfFileViewer {

    private val bookId: String =
        savedStateHandle.get<String>(Screen.BookViewer.ARG_BOOK_ID).orEmpty()

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
                .flatMapLatest { page -> preferences.bookBookmarks(bookId).map { page in it } }
                .collect { bookmarked -> _uiState.update { it.copy(isBookmarked = bookmarked) } }
        }
    }

    private fun openDocument() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val book = bookRepository.findBook(bookId)
                if (book == null) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "বই পাওয়া যায়নি")
                    }
                    return@launch
                }
                val file = bookRepository.fileFor(book)
                if (file == null || !file.exists()) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "বই ফাইল পাওয়া যায়নি")
                    }
                    return@launch
                }

                val newRenderer = PdfFileRenderer(file, pdfiumCore)
                val count = newRenderer.open()
                val toc = newRenderer.tableOfContents()
                renderer = newRenderer

                if (count <= 0) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "ফাইলটি খালি মনে হচ্ছে")
                    }
                    return@launch
                }

                val startPage = preferences.bookLastPage(bookId).first().coerceIn(1, count)
                currentPage.value = startPage
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pageCount = count,
                        currentPage = startPage,
                        toc = toc,
                        pageAspectRatio = newRenderer.pageAspectRatio,
                        documentTitle = book.title,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Book open failed for id=$bookId", throwable)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "বই খোলা যায়নি")
                }
            }
        }
    }

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
        viewModelScope.launch { preferences.setBookLastPage(bookId, page) }
    }

    override fun toggleBookmark() {
        val page = currentPage.value
        viewModelScope.launch { preferences.toggleBookBookmark(bookId, page) }
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
        const val TAG = "BookViewerVM"
    }
}
