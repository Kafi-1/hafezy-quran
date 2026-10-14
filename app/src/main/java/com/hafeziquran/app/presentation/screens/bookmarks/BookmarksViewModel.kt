package com.hafeziquran.app.presentation.screens.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.data.local.entity.BookmarkEntity
import com.hafeziquran.app.data.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: BookmarkRepository,
) : ViewModel() {

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.observeBookmarks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Adds a bookmark. Defaults a blank title to "পৃষ্ঠা N" so a list can never
     * show an untitled row.
     */
    fun add(page: Int, title: String) {
        viewModelScope.launch {
            repository.addBookmark(
                page = page,
                title = title.trim().ifEmpty { "পৃষ্ঠা $page" },
            )
        }
    }

    fun update(bookmark: BookmarkEntity, page: Int, title: String) {
        viewModelScope.launch {
            repository.updateBookmark(
                bookmark.copy(
                    pageNumber = page,
                    title = title.trim().ifEmpty { "পৃষ্ঠা $page" },
                ),
            )
        }
    }

    fun delete(bookmark: BookmarkEntity) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }
}
