package com.hafeziquran.app.presentation.screens.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.model.IslamicBook
import com.hafeziquran.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BooksUiState(
    val isLoading: Boolean = true,
    val books: List<IslamicBook> = emptyList(),
    val errorMessage: String? = null,
)

/** Backs the "ইসলামিক বুক" tab — a flat catalogue, loaded once. */
@HiltViewModel
class BooksViewModel @Inject constructor(
    private val repository: BookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BooksUiState())
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.loadBooks()) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, books = result.data)
                }

                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }

                Resource.Loading -> Unit
            }
        }
    }
}
