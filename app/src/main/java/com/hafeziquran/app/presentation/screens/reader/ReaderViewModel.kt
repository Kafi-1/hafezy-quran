package com.hafeziquran.app.presentation.screens.reader

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.pdf.PdfPageRenderer
import com.hafeziquran.app.data.preferences.ReadingMode
import com.hafeziquran.app.data.preferences.UserPreferences
import com.hafeziquran.app.data.repository.BookmarkRepository
import com.hafeziquran.app.data.repository.ReadingPositionRepository
import com.hafeziquran.app.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Immutable snapshot of the reader.
 *
 * [isReady] gates the pager: we never build it before we know the true page
 * count, otherwise the `PagerState` would be created with a wrong page count and
 * silently clamp the resume position to page 1.
 */
data class ReaderUiState(
    val isLoading: Boolean = true,
    val pageCount: Int = 0,
    val currentPage: Int = 1,
    val isBookmarked: Boolean = false,
    val nightMode: Boolean = false,
    val keepScreenOn: Boolean = true,
    val readingMode: ReadingMode = ReadingMode.HORIZONTAL,
    val pageAspectRatio: Float = 1.45f,
    val errorMessage: String? = null,
    /**
     * One-shot request to move the pager to a page. Set by the scrubber or the
     * "go to page" dialog and cleared by the reader once it has scrolled, so a
     * recomposition never re-triggers the jump.
     */
    val jumpRequest: Int? = null,
) {
    val isReady: Boolean get() = !isLoading && errorMessage == null && pageCount > 0
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pdfRenderer: PdfPageRenderer,
    private val bookmarkRepository: BookmarkRepository,
    private val readingPositionRepository: ReadingPositionRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    /** Set when navigated from a surah/para/bookmark; `null` means "resume". */
    private val requestedPage: Int? =
        savedStateHandle.get<Int>(Screen.Reader.ARG_PAGE)?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /** Transient snackbar text (bookmark added/removed). */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val currentPage = MutableStateFlow(1)

    init {
        observePreferences()
        observeBookmarkState()
        loadDocument()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferences.readerNightMode.collect { enabled ->
                _uiState.update { it.copy(nightMode = enabled) }
            }
        }
        viewModelScope.launch {
            preferences.keepScreenOn.collect { enabled ->
                _uiState.update { it.copy(keepScreenOn = enabled) }
            }
        }
        viewModelScope.launch {
            preferences.readingMode.collect { mode ->
                _uiState.update { it.copy(readingMode = mode) }
            }
        }
    }

    /** Keeps [ReaderUiState.isBookmarked] in sync with the visible page. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeBookmarkState() {
        viewModelScope.launch {
            currentPage
                .flatMapLatest { page -> bookmarkRepository.observeIsBookmarked(page) }
                .collect { bookmarked ->
                    _uiState.update { it.copy(isBookmarked = bookmarked) }
                }
        }
    }

    private fun loadDocument() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = runCatching { pdfRenderer.open() }

            result.fold(
                onSuccess = { count ->
                    if (count <= 0) {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "কুরআন ফাইল খালি মনে হচ্ছে")
                        }
                        return@fold
                    }

                    // Priority: explicit request → last read → first page.
                    val startPage = (
                        requestedPage
                            ?: readingPositionRepository.observeLastPage().first()
                            ?: FIRST_PAGE
                        ).coerceIn(FIRST_PAGE, count)

                    currentPage.value = startPage
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pageCount = count,
                            currentPage = startPage,
                            pageAspectRatio = pdfRenderer.pageAspectRatio,
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "কুরআন ফাইল খোলা যায়নি",
                        )
                    }
                },
            )
        }
    }

    /** Renders one page at [widthPx] for display, off the main thread. */
    suspend fun renderPage(pageIndex: Int, widthPx: Int): Resource<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching { pdfRenderer.renderPage(pageIndex, widthPx) }
                .fold(
                    onSuccess = { Resource.Success(it) },
                    onFailure = { Resource.Error(it, "পৃষ্ঠা লোড করা যায়নি") },
                )
        }

    /** Warms the pages either side of [page] so the next turn is instant. */
    fun prefetchNeighbours(page: Int, widthPx: Int) {
        viewModelScope.launch {
            pdfRenderer.prefetch(page - 2, widthPx) // -2/+0 are the two ahead in RTL
            pdfRenderer.prefetch(page, widthPx)
        }
    }

    /** Called once a page settles, whether from a swipe or a programmatic jump. */
    fun onPageSettled(page: Int) {
        if (currentPage.value == page) return
        currentPage.value = page
        _uiState.update { it.copy(currentPage = page) }
        viewModelScope.launch {
            readingPositionRepository.saveLastPage(page)
        }
    }

    fun toggleBookmark() {
        val page = currentPage.value
        viewModelScope.launch {
            val added = bookmarkRepository.toggleBookmark(page, "পৃষ্ঠা $page")
            _messages.send(
                if (added) "বুকমার্ক সংরক্ষিত হয়েছে" else "বুকমার্ক মুছে ফেলা হয়েছে",
            )
        }
    }

    fun setNightMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setReaderNightMode(enabled) }
    }

    /** Asks the reader to scroll to [page] (1-based). Clamped to the document. */
    fun requestJump(page: Int) {
        _uiState.update { state ->
            if (state.pageCount <= 0) {
                state
            } else {
                state.copy(jumpRequest = page.coerceIn(FIRST_PAGE, state.pageCount))
            }
        }
    }

    /** Called by the reader once it has honoured [ReaderUiState.jumpRequest]. */
    fun consumeJumpRequest() {
        _uiState.update { it.copy(jumpRequest = null) }
    }

    fun retry() = loadDocument()

    private companion object {
        const val FIRST_PAGE = 1
    }
}
