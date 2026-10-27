package com.hafeziquran.app.presentation.screens.sobok

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.util.AlignmentResult
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.core.util.alignAyah
import com.hafeziquran.app.data.audio.QuranRecorder
import com.hafeziquran.app.data.audio.toWhisperSamples
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.model.Surah
import com.hafeziquran.app.data.repository.QuranIndexRepository
import com.hafeziquran.app.data.repository.QuranTextRepository
import com.hafeziquran.app.data.repository.SobokModelRepository
import com.hafeziquran.app.data.whisper.WhisperEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SobokPhase { IDLE, RECORDING, PROCESSING, RESULT, ERROR }

data class SobokUiState(
    val modelDownloaded: Boolean = false,
    val modelSizeBytes: Long = 0L,
    val download: DownloadStatus = DownloadStatus.Idle,
    val surahs: List<Surah> = emptyList(),
    val selectedSurah: Surah? = null,
    val selectedAyah: Int = 1,
    val ayahText: String = "",
    val contentLoading: Boolean = true,
    val phase: SobokPhase = SobokPhase.IDLE,
    val elapsedSeconds: Int = 0,
    val result: AlignmentResult? = null,
    val errorMessage: String? = null,
) {
    val totalAyahs: Int
        get() = selectedSurah?.ayahs ?: 0

    val canGoNext: Boolean
        get() = selectedAyah < totalAyahs
}

/**
 * Drives the Sobok flow: optional model download gate, surah/ayah selection,
 * microphone recording, offline transcription and word-level result.
 */
@HiltViewModel
class SobokViewModel @Inject constructor(
    private val indexRepository: QuranIndexRepository,
    private val textRepository: QuranTextRepository,
    private val modelRepository: SobokModelRepository,
    private val whisperEngine: WhisperEngine,
) : ViewModel() {

    private val recorder = QuranRecorder()

    private val _state = MutableStateFlow(SobokUiState())
    val state: StateFlow<SobokUiState> = _state.asStateFlow()

    private var ayahTexts: List<String> = emptyList()
    private var recordingJob: Job? = null

    init {
        observeDownload()
        loadContent()
    }

    private fun observeDownload() {
        viewModelScope.launch {
            modelRepository.downloadState
                .map { status ->
                    DownloadSnapshot(
                        status = status,
                        downloaded = modelRepository.isDownloaded(),
                        sizeBytes = modelRepository.sizeBytes(),
                    )
                }
                // File stat + magic read are I/O; keep them off the main thread.
                .flowOn(Dispatchers.IO)
                .collect { snapshot ->
                    _state.update {
                        it.copy(
                            modelDownloaded = snapshot.downloaded,
                            modelSizeBytes = snapshot.sizeBytes,
                            download = snapshot.status,
                        )
                    }
                    if (snapshot.status is DownloadStatus.Completed && !snapshot.downloaded) {
                        modelRepository.discardInvalid()
                        _state.update {
                            it.copy(
                                errorMessage = "ডাউনলোড করা মডেলটি সঠিক নয়। আবার ডাউনলোড করুন।",
                            )
                        }
                    }
                    if (snapshot.downloaded) {
                        // Reclaim the superseded base model once tiny is valid.
                        modelRepository.cleanupLegacyModel()
                    }
                }
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            when (val index = indexRepository.load()) {
                is Resource.Success -> {
                    val surahs = index.data.surahs.sortedBy { it.number }
                    val first = surahs.firstOrNull { it.number == 1 } ?: surahs.firstOrNull()
                    _state.update {
                        it.copy(surahs = surahs, contentLoading = false, selectedSurah = first)
                    }
                    if (first != null) {
                        loadSurahAyahs(first, 1)
                    }
                }

                is Resource.Error -> _state.update {
                    it.copy(
                        contentLoading = false,
                        errorMessage = index.message ?: "সূরা তালিকা লোড করা যায়নি",
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun selectSurah(surahNumber: Int) {
        val surah = _state.value.surahs.firstOrNull { it.number == surahNumber } ?: return
        loadSurahAyahs(surah, 1)
    }

    fun selectAyah(ayahNumber: Int) {
        if (ayahTexts.isEmpty()) return
        val clamped = ayahNumber.coerceIn(1, ayahTexts.size)
        _state.update {
            it.copy(
                selectedAyah = clamped,
                ayahText = ayahTexts.getOrElse(clamped - 1) { "" },
                phase = SobokPhase.IDLE,
                elapsedSeconds = 0,
                result = null,
                errorMessage = null,
            )
        }
    }

    fun nextAyah() {
        if (_state.value.canGoNext) selectAyah(_state.value.selectedAyah + 1)
    }

    fun previousAyah() {
        if (_state.value.selectedAyah > 1) selectAyah(_state.value.selectedAyah - 1)
    }

    private fun loadSurahAyahs(surah: Surah, ayahNumber: Int) {
        viewModelScope.launch {
            textRepository.ayahs(surah.number).fold(
                onSuccess = { ayahs ->
                    ayahTexts = ayahs
                    val clamped = ayahNumber.coerceIn(1, maxOf(1, ayahs.size))
                    _state.update {
                        it.copy(
                            selectedSurah = surah,
                            selectedAyah = clamped,
                            ayahText = ayahs.getOrElse(clamped - 1) { "" },
                            phase = SobokPhase.IDLE,
                            elapsedSeconds = 0,
                            result = null,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(errorMessage = "আয়াতের টেক্সট লোড করা যায়নি")
                    }
                },
            )
        }
    }

    fun toggleRecording() {
        when (_state.value.phase) {
            SobokPhase.RECORDING -> recorder.stop()
            SobokPhase.PROCESSING -> Unit
            else -> startRecording()
        }
    }

    private fun startRecording() {
        val current = _state.value
        if (!current.modelDownloaded || current.ayahText.isBlank()) return

        recordingJob?.cancel()
        _state.update {
            it.copy(
                phase = SobokPhase.RECORDING,
                elapsedSeconds = 0,
                result = null,
                errorMessage = null,
            )
        }

        recordingJob = viewModelScope.launch {
            try {
                val samples = recorder.record(QuranRecorder.MAX_DURATION_MS) { elapsedMs ->
                    _state.update { it.copy(elapsedSeconds = elapsedMs / 1000) }
                }

                if (samples.size < MIN_SAMPLES) {
                    _state.update {
                        it.copy(
                            phase = SobokPhase.ERROR,
                            errorMessage = "খুব কম শব্দ রেকর্ড হয়েছে। মাইক্রোফোনের কাছে গিয়ে আবার চেষ্টা করুন।",
                        )
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        phase = SobokPhase.PROCESSING,
                        elapsedSeconds = samples.size / QuranRecorder.SAMPLE_RATE,
                    )
                }

                val modelPath = modelRepository.modelPath()
                if (modelPath == null) {
                    _state.update {
                        it.copy(
                            phase = SobokPhase.ERROR,
                            errorMessage = "সবক মডেল পাওয়া যায়নি। মডেলটি আবার ডাউনলোড করুন।",
                        )
                    }
                    return@launch
                }

                whisperEngine.transcribe(modelPath, samples.toWhisperSamples()).fold(
                    onSuccess = { recognized ->
                        if (recognized.isBlank()) {
                            _state.update {
                                it.copy(
                                    phase = SobokPhase.ERROR,
                                    errorMessage = "কোনো তেলাওয়াত সনাক্ত করা যায়নি। আরও স্পষ্টভাবে আবার চেষ্টা করুন।",
                                )
                            }
                        } else {
                            val surahNumber = current.selectedSurah?.number
                            val ignoreBismillah = current.selectedAyah == 1 &&
                                surahNumber != null && surahNumber != 1 && surahNumber != 9
                            val result = alignAyah(
                                originalText = current.ayahText,
                                recognizedText = recognized,
                                ignoreLeadingBismillah = ignoreBismillah,
                            )
                            _state.update {
                                it.copy(
                                    phase = SobokPhase.RESULT,
                                    result = result,
                                    errorMessage = null,
                                )
                            }
                        }
                    },
                    onFailure = {
                        _state.update {
                            it.copy(
                                phase = SobokPhase.ERROR,
                                errorMessage = "প্রসেস করা যায়নি। মডেলটি আবার ডাউনলোড করে চেষ্টা করুন।",
                            )
                        }
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        phase = SobokPhase.ERROR,
                        errorMessage = "রেকর্ডিং ব্যর্থ হয়েছে। মাইক্রোফোনের অনুমতি ও আবার চেষ্টা করুন।",
                    )
                }
            }
        }
    }

    fun resetResult() {
        _state.update {
            it.copy(
                phase = SobokPhase.IDLE,
                elapsedSeconds = 0,
                result = null,
                errorMessage = null,
            )
        }
    }

    fun startDownload() {
        _state.update { it.copy(errorMessage = null) }
        modelRepository.startDownload()
    }

    fun cancelDownload() = modelRepository.cancelDownload()

    /** Deletes a possibly corrupt model and immediately starts a fresh download. */
    fun redownloadModel() {
        viewModelScope.launch {
            modelRepository.delete()
            _state.update {
                it.copy(
                    modelDownloaded = false,
                    phase = SobokPhase.IDLE,
                    result = null,
                    errorMessage = null,
                )
            }
            modelRepository.startDownload()
        }
    }

    override fun onCleared() {
        recorder.stop()
        recordingJob?.cancel()
        super.onCleared()
    }

    private data class DownloadSnapshot(
        val status: DownloadStatus,
        val downloaded: Boolean,
        val sizeBytes: Long,
    )

    private companion object {
        /** ~0.5 s of audio; anything shorter is treated as an accidental tap. */
        const val MIN_SAMPLES = 8_000
    }
}
