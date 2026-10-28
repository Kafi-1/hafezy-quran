package com.hafeziquran.app.presentation.screens.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.model.BookType
import com.hafeziquran.app.data.model.IslamicBook
import com.hafeziquran.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One catalogue row: the book plus its derived open/download state. */
data class BookRow(
    val book: IslamicBook,
    /** True when the book's PDF is on disk and can be opened right now. */
    val isOpenable: Boolean,
    val download: DownloadStatus,
)

data class BooksUiState(
    val isLoading: Boolean = true,
    val rows: List<BookRow> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Backs the "ইসলামিক বুক" tab. Loads the catalogue once, then re-derives each
 * row's state from the download manager so a running download's progress shows
 * up live on the card.
 */
@HiltViewModel
class BooksViewModel @Inject constructor(
    private val repository: BookRepository,
) : ViewModel() {

    private val _books = MutableStateFlow<List<IslamicBook>>(emptyList())
    private val _error = MutableStateFlow<String?>(null)
    private val _loading = MutableStateFlow(true)

    val uiState: StateFlow<BooksUiState> =
        combine(
            _books,
            _loading,
            _error,
            repository.downloadStates,
        ) { books, loading, error, downloads ->
            BooksUiState(
                isLoading = loading,
                errorMessage = error,
                rows = books.map { book ->
                    val download = downloads[book.id] ?: DownloadStatus.Idle
                    val openable = when (book.type) {
                        BookType.LOCAL -> repository.assetExists(book.asset)
                        BookType.DOWNLOAD -> repository.isDownloaded(book)
                    }
                    BookRow(book, openable, download)
                },
            )
        }
            // File stats are I/O; keep them off the main thread.
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = BooksUiState(isLoading = true),
            )

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = repository.loadBooks()) {
                is Resource.Success -> _books.value = result.data
                is Resource.Error -> _error.value = result.message
                Resource.Loading -> Unit
            }
            _loading.value = false
        }
    }

    fun download(book: IslamicBook) = repository.startDownload(book)

    fun cancelDownload(book: IslamicBook) = repository.cancelDownload(book)

    fun deleteDownload(book: IslamicBook) = repository.deleteDownload(book)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
