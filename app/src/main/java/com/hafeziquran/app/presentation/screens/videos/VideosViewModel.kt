package com.hafeziquran.app.presentation.screens.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.data.model.VideoItem
import com.hafeziquran.app.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideosUiState(
    val loading: Boolean = true,
    val videos: List<VideoItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class VideosViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(VideosUiState())
    val ui: StateFlow<VideosUiState> = _ui.asStateFlow()

    private var collectJob: Job? = null

    init {
        load()
    }

    fun load() {
        collectJob?.cancel()
        _ui.value = VideosUiState(loading = true)
        collectJob = viewModelScope.launch {
            videoRepository.videos()
                .catch {
                    _ui.value = VideosUiState(
                        loading = false,
                        error = "ভিডিও লোড করা যায়নি। ইন্টারনেট সংযোগ এবং Firestore rules চেক করুন।",
                    )
                }
                .collect { videos ->
                    _ui.value = VideosUiState(loading = false, videos = videos)
                }
        }
    }
}
