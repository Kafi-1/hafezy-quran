package com.hafeziquran.app.presentation.screens.banglaquran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.repository.BanglaQuranRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class BanglaQuranUiState(
    val isDownloaded: Boolean = false,
    val sizeBytes: Long = 0L,
    val download: DownloadStatus = DownloadStatus.Idle,
)

/**
 * Drives the Bangla-Quran download screen. All the real work lives in
 * [BanglaQuranRepository]; this only reshapes its state for the UI and forwards
 * user intents.
 */
@HiltViewModel
class BanglaQuranViewModel @Inject constructor(
    private val repository: BanglaQuranRepository,
) : ViewModel() {

    val uiState: StateFlow<BanglaQuranUiState> = repository.downloadState
        .map { status ->
            // Re-read the file each emission: a completed download (or a delete,
            // which resets the status to Idle) is exactly what changes this.
            BanglaQuranUiState(
                isDownloaded = repository.isDownloaded(),
                sizeBytes = repository.sizeBytes(),
                download = status,
            )
        }
        // File stat is I/O; keep it off the main thread.
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = BanglaQuranUiState(
                isDownloaded = repository.isDownloaded(),
                sizeBytes = repository.sizeBytes(),
            ),
        )

    fun download() = repository.startDownload()

    fun cancel() = repository.cancelDownload()

    fun delete() = repository.delete()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
