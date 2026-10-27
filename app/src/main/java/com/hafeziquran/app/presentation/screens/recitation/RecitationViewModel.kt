package com.hafeziquran.app.presentation.screens.recitation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.recitation.AyahCheckResult
import com.hafeziquran.app.data.recitation.AyahProcessingQueue
import com.hafeziquran.app.data.recitation.AyahSegment
import com.hafeziquran.app.data.recitation.AyahSegmentRecorder
import com.hafeziquran.app.data.recitation.AyahTranscription
import com.hafeziquran.app.data.recitation.PageAyah
import com.hafeziquran.app.data.recitation.QuranRecitationChecker
import com.hafeziquran.app.data.recitation.RecitationMetrics
import com.hafeziquran.app.data.recitation.WhisperWorker
import com.hafeziquran.app.data.recitation.WordMatchStatus
import com.hafeziquran.app.data.repository.QuranIndexRepository
import com.hafeziquran.app.data.repository.QuranPageRepository
import com.hafeziquran.app.data.repository.ReadingPositionRepository
import com.hafeziquran.app.data.repository.SobokModelRepository
import com.hafeziquran.app.data.whisper.WhisperEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

enum class AyahDisplayState { IDLE, LISTENING, QUEUED, PROCESSING, RESULT_READY, ERROR }

data class AyahUiState(
    val state: AyahDisplayState = AyahDisplayState.IDLE,
    val segmentId: Long? = null,
    val result: AyahCheckResult? = null,
)

data class RecitationUiState(
    val page: Int = 1,
    val pageCount: Int = AppConstants.PHYSICAL_PAGE_COUNT,
    val ayahs: List<PageAyah> = emptyList(),
    val ayahStates: Map<Int, AyahUiState> = emptyMap(),
    val isListening: Boolean = false,
    val currentAyahIndex: Int = 0,
    val processingSegmentIds: Set<Long> = emptySet(),
    val queuedSegmentIds: Set<Long> = emptySet(),
    val completedResults: Map<String, AyahCheckResult> = emptyMap(),
    val queueBacklogCount: Int = 0,
    val errorMessage: String? = null,
    val contentLoading: Boolean = true,
    val modelDownloaded: Boolean = false,
    val modelSizeBytes: Long = 0L,
    val modelReady: Boolean = false,
    val download: DownloadStatus = DownloadStatus.Idle,
    val pageFinished: Boolean = false,
    val metrics: RecitationMetrics = RecitationMetrics(),
)

/**
 * Drives the live, full-page recitation checker.
 *
 * Recording and inference are decoupled by two channels: the recorder writes to
 * an unbounded channel (it must never block the microphone) and a router coroutine
 * moves completed segments into the bounded [AyahProcessingQueue]. [WhisperWorker]
 * is the only consumer of that queue, so inference is strictly serial.
 */
@HiltViewModel
class RecitationViewModel @Inject constructor(
    private val indexRepository: QuranIndexRepository,
    private val pageRepository: QuranPageRepository,
    private val readingPositionRepository: ReadingPositionRepository,
    private val modelRepository: SobokModelRepository,
    private val whisperEngine: WhisperEngine,
    private val recorder: AyahSegmentRecorder,
    private val whisperWorker: WhisperWorker,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(RecitationUiState())
    val state: StateFlow<RecitationUiState> = _state.asStateFlow()

    @Volatile
    private var pageAyahs: List<PageAyah> = emptyList()

    @Volatile
    private var pageIndexByRef: Map<Pair<Int, Int>, Int> = emptyMap()

    @Volatile
    private var checker: QuranRecitationChecker? = null

    private var queue: AyahProcessingQueue? = null
    private var recorderChannel: Channel<AyahSegment>? = null
    private var recorderJob: Job? = null
    private var routerJob: Job? = null
    private var workerJob: Job? = null
    private var pendingCountJob: Job? = null

    private val indicatorLock = Any()
    private val pendingTargets = LinkedHashMap<Long, Int>()
    private var provisionalCursor = 0

    /**
     * Bumped for every capture session. The worker callbacks close over the session
     * they were started with and no-op once [activeSession] has moved on, so a
     * segment still unwinding from a previous page/session can never publish a
     * result against the freshly replaced [checker].
     */
    private val sessionCounter = AtomicLong(0)

    @Volatile
    private var activeSession = -1L

    init {
        observeModel()
        viewModelScope.launch {
            val requested = savedStateHandle.get<Int>(PAGE_ARG) ?: NO_PAGE
            val initial = if (requested > 0) {
                requested
            } else {
                readingPositionRepository.observeLastPage().first() ?: 1
            }
            loadPage(initial)
        }
    }

    fun setPage(physicalPage: Int) {
        val clamped = physicalPage.coerceIn(1, AppConstants.PHYSICAL_PAGE_COUNT)
        if (clamped == _state.value.page) return
        loadPage(clamped)
    }

    fun nextPage() = setPage(_state.value.page + 1)

    fun previousPage() = setPage(_state.value.page - 1)

    fun startListening() {
        if (_state.value.isListening || pageAyahs.isEmpty()) return
        // modelDownloaded is maintained on the IO dispatcher by observeModel(), so
        // this guard does no file I/O on the main thread.
        if (!_state.value.modelDownloaded) {
            _state.update {
                it.copy(errorMessage = "লাইভ সবকের জন্য মডেলটি আগে ডাউনলোড করুন")
            }
            return
        }

        if (checker == null || checker?.isPageFinished == true) {
            checker = QuranRecitationChecker(pageAyahs)
            provisionalCursor = 0
            _state.update {
                it.copy(
                    ayahStates = emptyMap(),
                    completedResults = emptyMap(),
                    pageFinished = false,
                    errorMessage = null,
                    metrics = RecitationMetrics(),
                )
            }
        }

        preloadModelIfNeeded()

        val sessionQueue = AyahProcessingQueue()
        queue = sessionQueue
        val channel = Channel<AyahSegment>(Channel.UNLIMITED)
        recorderChannel = channel

        synchronized(indicatorLock) {
            pendingTargets.clear()
            provisionalCursor = checker?.nextAyahIndex ?: 0
        }

        pendingCountJob = viewModelScope.launch {
            sessionQueue.pendingCount.collect { count ->
                _state.update { it.copy(queueBacklogCount = count) }
            }
        }

        routerJob = viewModelScope.launch(Dispatchers.Default) {
            for (segment in channel) {
                val target = synchronized(indicatorLock) {
                    if (pageAyahs.isEmpty()) {
                        0
                    } else {
                        val index = provisionalCursor.coerceIn(0, pageAyahs.lastIndex)
                        provisionalCursor = (index + 1).coerceAtMost(pageAyahs.lastIndex)
                        index
                    }
                }
                // Register the badge target before enqueue: the worker may pick
                // the segment up on another thread the instant it is queued.
                synchronized(indicatorLock) { pendingTargets[segment.id] = target }
                if (!sessionQueue.enqueue(segment)) {
                    synchronized(indicatorLock) { pendingTargets.remove(segment.id) }
                    _state.update {
                        it.copy(
                            metrics = it.metrics.copy(
                                droppedCount = it.metrics.droppedCount + 1,
                            ),
                        )
                    }
                    continue
                }

                _state.update { current ->
                    val states = current.ayahStates.toMutableMap()
                    states[target] = AyahUiState(
                        state = AyahDisplayState.QUEUED,
                        segmentId = segment.id,
                        result = states[target]?.result,
                    )
                    val next = (target + 1).coerceAtMost(maxOf(0, pageAyahs.lastIndex))
                    if (current.isListening && next > target) {
                        val existing = states[next]
                        if (existing == null || existing.state == AyahDisplayState.IDLE) {
                            states[next] = AyahUiState(
                                state = AyahDisplayState.LISTENING,
                                result = existing?.result,
                            )
                        }
                    }
                    current.copy(
                        queuedSegmentIds = current.queuedSegmentIds + segment.id,
                        ayahStates = states,
                    )
                }
            }
        }

        val session = sessionCounter.incrementAndGet()
        activeSession = session

        workerJob = whisperWorker.start(
            scope = viewModelScope,
            queue = sessionQueue,
            modelPath = { modelRepository.modelPath() },
            onDequeued = { segment -> if (session == activeSession) onSegmentDequeued(segment) },
            onTranscribed = { segment, transcription ->
                if (session == activeSession) onSegmentTranscribed(segment, transcription)
            },
            onFailed = { segment, error ->
                if (session == activeSession) onSegmentFailed(segment, error)
            },
        )

        recorderJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                recorder.record(
                    events = channel,
                    onDiscarded = {
                        _state.update {
                            it.copy(
                                metrics = it.metrics.copy(
                                    discardedCount = it.metrics.discardedCount + 1,
                                ),
                            )
                        }
                    },
                    onError = { throwable ->
                        viewModelScope.launch {
                            _state.update {
                                it.copy(
                                    isListening = false,
                                    errorMessage = "মাইক্রোফোন চালু করা যায়নি: ${throwable.message}",
                                )
                            }
                        }
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }

        val startIndex = checker?.nextAyahIndex ?: 0
        _state.update { current ->
            val states = current.ayahStates.toMutableMap()
            if (startIndex in pageAyahs.indices) {
                val existing = states[startIndex]
                if (existing == null || existing.state != AyahDisplayState.RESULT_READY) {
                    states[startIndex] = AyahUiState(
                        state = AyahDisplayState.LISTENING,
                        result = existing?.result,
                    )
                }
            }
            current.copy(
                isListening = true,
                currentAyahIndex = startIndex,
                errorMessage = null,
                pageFinished = false,
                ayahStates = states,
            )
        }
    }

    fun stopListening() {
        if (!_state.value.isListening && recorderJob == null) return

        // Invalidate this session first: a worker callback still unwinding must not
        // mutate the state of the session the user has just left.
        activeSession = -1L

        recorder.stop()
        recorderJob?.cancel()
        recorderJob = null

        recorderChannel?.close()
        recorderChannel = null

        routerJob?.cancel()
        routerJob = null

        workerJob?.cancel()
        workerJob = null

        pendingCountJob?.cancel()
        pendingCountJob = null

        queue?.closeAndDrain()
        queue = null

        synchronized(indicatorLock) {
            pendingTargets.clear()
            provisionalCursor = checker?.nextAyahIndex ?: 0
        }

        _state.update { current ->
            val states = current.ayahStates.mapValues { (_, ui) ->
                when (ui.state) {
                    AyahDisplayState.QUEUED,
                    AyahDisplayState.PROCESSING,
                    AyahDisplayState.LISTENING,
                    -> AyahUiState(
                        state = if (ui.result != null) {
                            AyahDisplayState.RESULT_READY
                        } else {
                            AyahDisplayState.IDLE
                        },
                        result = ui.result,
                    )
                    else -> ui
                }
            }
            current.copy(
                isListening = false,
                processingSegmentIds = emptySet(),
                queuedSegmentIds = emptySet(),
                queueBacklogCount = 0,
                ayahStates = states,
            )
        }
    }

    fun toggleListening() {
        if (_state.value.isListening) stopListening() else startListening()
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Re-attempts the current page after a transient load failure. */
    fun retryLoadPage() {
        val current = _state.value.page
        _state.update { it.copy(contentLoading = true) }
        loadPage(if (current in 1..AppConstants.PHYSICAL_PAGE_COUNT) current else 1)
    }

    fun startDownload() {
        _state.update { it.copy(errorMessage = null) }
        modelRepository.startDownload()
    }

    fun cancelDownload() = modelRepository.cancelDownload()

    private fun loadPage(physicalPage: Int) {
        stopListening()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    page = physicalPage.coerceIn(1, AppConstants.PHYSICAL_PAGE_COUNT),
                    contentLoading = true,
                    ayahs = emptyList(),
                    ayahStates = emptyMap(),
                    completedResults = emptyMap(),
                    pageFinished = false,
                    errorMessage = null,
                )
            }
            indexRepository.load()
            val madani = indexRepository.toMadaniPage(physicalPage)
            pageRepository.page(madani).fold(
                onSuccess = { ayahs ->
                    pageAyahs = ayahs
                    pageIndexByRef = ayahs.mapIndexed { index, ayah ->
                        (ayah.surahNumber to ayah.ayahNumber) to index
                    }.toMap()
                    checker = QuranRecitationChecker(ayahs)
                    provisionalCursor = 0
                    _state.update {
                        it.copy(
                            ayahs = ayahs,
                            currentAyahIndex = 0,
                            contentLoading = false,
                            ayahStates = emptyMap(),
                        )
                    }
                    preloadModelIfNeeded()
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            contentLoading = false,
                            errorMessage = throwable.message ?: "পৃষ্ঠা লোড করা যায়নি",
                        )
                    }
                },
            )
        }
    }

    private fun observeModel() {
        viewModelScope.launch {
            modelRepository.downloadState
                .map { status ->
                    ModelSnapshot(
                        status = status,
                        downloaded = modelRepository.isDownloaded(),
                        sizeBytes = modelRepository.sizeBytes(),
                    )
                }
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
                            it.copy(errorMessage = "ডাউনলোড করা মডেলটি সঠিক নয়। আবার ডাউনলোড করুন।")
                        }
                    }
                    if (snapshot.downloaded) {
                        // Reclaim the superseded base model once the tiny model is
                        // present and valid — never before.
                        viewModelScope.launch(Dispatchers.IO) {
                            modelRepository.cleanupLegacyModel()
                        }
                        preloadModelIfNeeded()
                    }
                }
        }
    }

    private fun preloadModelIfNeeded() {
        if (whisperEngine.isReady) return
        viewModelScope.launch {
            // Resolving the model path stats the file; keep it off the main thread.
            val modelPath = withContext(Dispatchers.IO) { modelRepository.modelPath() }
                ?: return@launch
            whisperEngine.preload(modelPath).onSuccess {
                _state.update { it.copy(modelReady = true) }
            }
        }
    }

    private suspend fun onSegmentDequeued(segment: AyahSegment) {
        val target = synchronized(indicatorLock) { pendingTargets.remove(segment.id) }
        val pending = rebasePending((target ?: checker?.nextAyahIndex ?: 0) + 1)

        _state.update { current ->
            val states = current.ayahStates.toMutableMap()
            applyQueued(states, pending)
            if (target != null) {
                states[target] = AyahUiState(
                    state = AyahDisplayState.PROCESSING,
                    segmentId = segment.id,
                    result = states[target]?.result,
                )
            }
            current.copy(
                processingSegmentIds = current.processingSegmentIds + segment.id,
                queuedSegmentIds = current.queuedSegmentIds - segment.id,
                ayahStates = states,
            )
        }
    }

    private suspend fun onSegmentTranscribed(
        segment: AyahSegment,
        transcription: AyahTranscription,
    ) {
        val activeChecker = checker ?: return
        val results = activeChecker.check(segment.id, transcription.arabicText)

        synchronized(indicatorLock) { pendingTargets.remove(segment.id) }

        val actualNext = activeChecker.nextAyahIndex
        val pageFinished = activeChecker.isPageFinished
        val pending = rebasePending(actualNext)

        // The last ayah with any scored word carries the most representative
        // accuracy for this segment; extra-only ayahs are ignored.
        val latestAccuracy = results
            .lastOrNull { result -> result.words.any { it.status != WordMatchStatus.EXTRA } }
            ?.accuracy

        _state.update { current ->
            val states = current.ayahStates.toMutableMap()
            val resultsByKey = current.completedResults.toMutableMap()
            val listening = current.isListening

            // A transcribing segment may land on a different ayah than its
            // provisional badge; clear the old PROCESSING marker first.
            states.entries.removeAll { (_, ui) ->
                ui.segmentId == segment.id && ui.state == AyahDisplayState.PROCESSING
            }

            results.forEach { result ->
                val index = pageIndexByRef[result.surahNumber to result.ayahNumber]
                if (index != null) {
                    states[index] = AyahUiState(
                        state = if (!result.complete && listening) {
                            AyahDisplayState.LISTENING
                        } else {
                            AyahDisplayState.RESULT_READY
                        },
                        segmentId = segment.id,
                        result = result,
                    )
                }
                resultsByKey["${segment.id}:${result.ayahNumber}"] = result
            }

            applyQueued(states, pending)

            if (actualNext in pageAyahs.indices && listening && !pageFinished) {
                val existing = states[actualNext]
                if (existing == null || existing.state != AyahDisplayState.RESULT_READY) {
                    states[actualNext] = AyahUiState(
                        state = AyahDisplayState.LISTENING,
                        result = existing?.result,
                    )
                }
            }

            current.copy(
                ayahStates = states,
                completedResults = resultsByKey,
                processingSegmentIds = current.processingSegmentIds - segment.id,
                queuedSegmentIds = current.queuedSegmentIds - segment.id,
                currentAyahIndex = actualNext,
                pageFinished = pageFinished,
                metrics = current.metrics.copy(
                    segmentCount = current.metrics.segmentCount + 1,
                    modelLoadMs = whisperEngine.lastLoadTimeMs,
                    lastInferenceMs = transcription.inferenceMs,
                    lastQueueWaitMs = transcription.queueWaitMs,
                    lastTotalMs = transcription.queueWaitMs + transcription.inferenceMs,
                    lastAccuracy = latestAccuracy ?: current.metrics.lastAccuracy,
                ),
            )
        }

        if (pageFinished) {
            // Leave the worker callback before tearing the session down.
            viewModelScope.launch(Dispatchers.Main.immediate) { stopListening() }
        }
    }

    private suspend fun onSegmentFailed(segment: AyahSegment, error: Throwable) {
        val activeChecker = checker ?: return

        // Deliberately do NOT advance the checker past the failed ayah: the words
        // it was meant to cover have not been heard yet, so the next segment is
        // aligned against the same position. Only the badge is marked failed, and
        // a later successful recitation of the same ayah clears it.
        val failedAyah = synchronized(indicatorLock) {
            pendingTargets.remove(segment.id) ?: activeChecker.nextAyahIndex
        }

        val actualNext = activeChecker.nextAyahIndex
        val pending = rebasePending(actualNext)

        _state.update { current ->
            val states = current.ayahStates.toMutableMap()
            val listening = current.isListening

            states.entries.removeAll { (_, ui) ->
                ui.segmentId == segment.id && ui.state == AyahDisplayState.PROCESSING
            }

            if (failedAyah in pageAyahs.indices) {
                states[failedAyah] = AyahUiState(
                    state = AyahDisplayState.ERROR,
                    segmentId = segment.id,
                    result = states[failedAyah]?.result,
                )
            }

            applyQueued(states, pending)

            if (actualNext in pageAyahs.indices && listening) {
                val existing = states[actualNext]
                if (existing == null || existing.state != AyahDisplayState.ERROR) {
                    states[actualNext] = AyahUiState(
                        state = AyahDisplayState.LISTENING,
                        result = existing?.result,
                    )
                }
            }

            current.copy(
                ayahStates = states,
                processingSegmentIds = current.processingSegmentIds - segment.id,
                queuedSegmentIds = current.queuedSegmentIds - segment.id,
                currentAyahIndex = actualNext,
                errorMessage = null,
                metrics = current.metrics.copy(
                    failedCount = current.metrics.failedCount + 1,
                ),
            )
        }
    }

    /**
     * Re-numbers the still-pending segments from [from] onwards. Segment targets
     * recorded at enqueue time are provisional (a segment can cover several
     * ayahs); once the checker advances they are corrected here.
     */
    private fun rebasePending(from: Int): Map<Long, Int> = synchronized(indicatorLock) {
        val ids = pendingTargets.keys.toList()
        pendingTargets.clear()
        if (pageAyahs.isEmpty()) return@synchronized emptyMap()

        var next = from.coerceIn(0, pageAyahs.lastIndex)
        for (id in ids) {
            pendingTargets[id] = next
            next = (next + 1).coerceAtMost(pageAyahs.lastIndex)
        }
        provisionalCursor = next
        pendingTargets.toMap()
    }

    /** Repaints QUEUED badges so they follow [pending] without hiding verdicts. */
    private fun applyQueued(
        states: MutableMap<Int, AyahUiState>,
        pending: Map<Long, Int>,
    ) {
        states.entries.removeAll { (index, ui) ->
            ui.state == AyahDisplayState.QUEUED &&
                (ui.segmentId == null || pending[ui.segmentId] != index)
        }
        pending.forEach { (segmentId, index) ->
            val existing = states[index]
            if (existing?.state == AyahDisplayState.PROCESSING ||
                existing?.state == AyahDisplayState.RESULT_READY ||
                existing?.state == AyahDisplayState.ERROR
            ) {
                return@forEach
            }
            states[index] = AyahUiState(
                state = AyahDisplayState.QUEUED,
                segmentId = segmentId,
                result = existing?.result,
            )
        }
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }

    private data class ModelSnapshot(
        val status: DownloadStatus,
        val downloaded: Boolean,
        val sizeBytes: Long,
    )

    companion object {
        const val PAGE_ARG = "page"
        const val NO_PAGE = -1
    }
}
