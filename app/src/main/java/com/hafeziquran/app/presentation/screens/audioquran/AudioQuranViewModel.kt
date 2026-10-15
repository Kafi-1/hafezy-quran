package com.hafeziquran.app.presentation.screens.audioquran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.util.toIntOrNullBangla
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.model.Reciter
import com.hafeziquran.app.data.model.SurahInfo
import com.hafeziquran.app.data.repository.AudioRepository
import com.hafeziquran.app.data.repository.BatchProgress
import com.hafeziquran.app.data.repository.SurahRepository
import com.hafeziquran.app.playback.AudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AudioQuranUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val reciter: Reciter = Reciter.ALAFASY,
    val query: String = "",
    /** The surah list after applying [query]; what the list renders. */
    val surahs: List<SurahInfo> = emptyList(),
    /** Per-surah download state for the selected reciter, keyed by surah number. */
    val downloads: Map<Int, DownloadStatus> = emptyMap(),
    val downloaded: Set<Int> = emptySet(),
    val batch: BatchProgress = BatchProgress(),
    val nowPlaying: SurahInfo? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
)

/**
 * Drives the Audio Quran screen: the catalogue, the search filter, per-surah and
 * whole-Quran downloads, and playback commands forwarded to [AudioPlayer].
 *
 * State is assembled by separate collectors updating one [MutableStateFlow] rather
 * than a large `combine`: the sources (catalogue, downloads, player, filter) have
 * no cross-dependencies, and this keeps each update explicit.
 */
@HiltViewModel
class AudioQuranViewModel @Inject constructor(
    private val surahRepository: SurahRepository,
    private val audioRepository: AudioRepository,
    private val player: AudioPlayer,
) : ViewModel() {

    private val _ui = MutableStateFlow(AudioQuranUiState())
    val uiState: StateFlow<AudioQuranUiState> = _ui.asStateFlow()

    /** The unfiltered catalogue; the queue and "download all" always use all 114. */
    private var catalogue: List<SurahInfo> = emptyList()

    init {
        viewModelScope.launch {
            surahRepository.surahs()
                .onSuccess { list ->
                    catalogue = list
                    // The player may already be running (audio outlives this
                    // screen); resolve its surah now that the catalogue exists.
                    val current = player.state.value.currentSurah
                    _ui.update {
                        it.copy(
                            loading = false,
                            surahs = filterSurahs(list, it.query),
                            nowPlaying = current?.let { n -> list.firstOrNull { s -> s.number == n } },
                        )
                    }
                }
                .onFailure {
                    _ui.update { it.copy(loading = false, error = "সূরার তালিকা লোড করা যায়নি") }
                }
            withContext(Dispatchers.IO) { audioRepository.seedDownloaded(_ui.value.reciter) }
        }

        viewModelScope.launch {
            audioRepository.downloads.collect { map ->
                val prefix = "${_ui.value.reciter.slug}_"
                val perSurah = buildMap {
                    map.forEach { (key, status) ->
                        if (key.startsWith(prefix)) {
                            audioRepository.surahNumberFromKey(key)?.let { put(it, status) }
                        }
                    }
                }
                val downloaded = perSurah.filterValues { it is DownloadStatus.Completed }.keys
                _ui.update { it.copy(downloads = perSurah, downloaded = downloaded) }
            }
        }

        viewModelScope.launch {
            audioRepository.batch.collect { progress -> _ui.update { it.copy(batch = progress) } }
        }

        viewModelScope.launch {
            player.state.collect { playback ->
                _ui.update {
                    it.copy(
                        nowPlaying = playback.currentSurah?.let { n -> catalogue.firstOrNull { s -> s.number == n } },
                        isPlaying = playback.playing,
                        buffering = playback.buffering,
                    )
                }
            }
        }
    }

    fun setReciter(reciter: Reciter) {
        if (reciter == _ui.value.reciter) return
        _ui.update { it.copy(reciter = reciter, downloads = emptyMap(), downloaded = emptySet()) }
        viewModelScope.launch(Dispatchers.IO) { audioRepository.seedDownloaded(reciter) }
    }

    fun setQuery(query: String) {
        _ui.update { it.copy(query = query, surahs = filterSurahs(catalogue, query)) }
    }

    fun play(surahNumber: Int) {
        val list = catalogue
        if (list.isEmpty()) return
        val reciter = _ui.value.reciter
        viewModelScope.launch {
            val index = list.indexOfFirst { it.number == surahNumber }.coerceAtLeast(0)
            val items = withContext(Dispatchers.IO) {
                list.map { audioRepository.mediaItem(reciter, it) }
            }
            player.playQueue(items, index)
        }
    }

    fun togglePlayPause() = player.togglePlayPause()

    fun next() = player.next()

    fun previous() = player.previous()

    fun download(surahNumber: Int) = audioRepository.download(_ui.value.reciter, surahNumber)

    fun cancelDownload(surahNumber: Int) = audioRepository.cancelDownload(_ui.value.reciter, surahNumber)

    fun delete(surahNumber: Int) = audioRepository.delete(_ui.value.reciter, surahNumber)

    fun downloadAll() = audioRepository.downloadAll(_ui.value.reciter, catalogue.map { it.number })

    fun cancelDownloadAll() = audioRepository.cancelDownloadAll()

    fun deleteAll(): Int = audioRepository.deleteAll(_ui.value.reciter)

    /** Matches Bangla name, Arabic name, English transliteration, or surah number. */
    private fun filterSurahs(list: List<SurahInfo>, query: String): List<SurahInfo> {
        val q = query.trim()
        if (q.isEmpty()) return list
        val lower = q.lowercase()
        val number = q.toIntOrNull() ?: q.toIntOrNullBangla()
        return list.filter { surah ->
            surah.bangla.contains(q) ||
                surah.arabic.contains(q) ||
                surah.english.lowercase().contains(lower) ||
                (number != null && surah.number == number)
        }
    }
}
