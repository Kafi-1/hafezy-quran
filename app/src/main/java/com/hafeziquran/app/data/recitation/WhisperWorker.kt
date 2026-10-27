package com.hafeziquran.app.data.recitation

import android.os.SystemClock
import android.util.Log
import com.hafeziquran.app.BuildConfig
import com.hafeziquran.app.data.audio.toWhisperSamples
import com.hafeziquran.app.data.whisper.WhisperEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The one and only consumer of [AyahProcessingQueue].
 *
 * A single coroutine on [Dispatchers.Default] drains the channel in order, so the
 * whisper.cpp context is never entered concurrently. [WhisperEngine] also holds a
 * mutex; that is belt-and-braces for other callers (the per-ayah Sobok screen),
 * not a licence to run several workers.
 */
class WhisperWorker @Inject constructor(
    private val whisperEngine: WhisperEngine,
) {

    fun start(
        scope: CoroutineScope,
        queue: AyahProcessingQueue,
        modelPath: () -> String?,
        onDequeued: suspend (AyahSegment) -> Unit,
        onTranscribed: suspend (AyahSegment, AyahTranscription) -> Unit,
        onFailed: suspend (AyahSegment, Throwable) -> Unit,
    ): Job = scope.launch(Dispatchers.Default) {
        for (segment in queue.channel) {
            queue.onDequeued()
            val queueWaitMs = SystemClock.elapsedRealtime() - segment.createdAtMs

            try {
                onDequeued(segment)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.w(TAG, "Dequeue listener failed", throwable)
            }

            val path = modelPath()
            if (path == null) {
                deliverFailure(segment, IllegalStateException("Sobok model is not loaded"), onFailed)
                continue
            }

            var delivered = false
            var lastError: Throwable? = null

            for (attempt in 1..MAX_ATTEMPTS) {
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    val text = whisperEngine.transcribe(path, segment.pcm.toWhisperSamples())
                        .getOrThrow()
                    check(text.isNotBlank()) { "Whisper returned an empty transcript" }

                    val inferenceMs = SystemClock.elapsedRealtime() - startedAt
                    val totalMs = SystemClock.elapsedRealtime() - segment.createdAtMs
                    if (BuildConfig.DEBUG) {
                        Log.i(
                            TAG,
                            "segment=${segment.id} queueWait=${queueWaitMs}ms " +
                                "audio=${segment.durationMs}ms inference=${inferenceMs}ms " +
                                "total=${totalMs}ms attempt=$attempt",
                        )
                    }
                    onTranscribed(
                        segment,
                        AyahTranscription(
                            segmentId = segment.id,
                            arabicText = text,
                            inferenceMs = inferenceMs,
                            queueWaitMs = queueWaitMs,
                        ),
                    )
                    delivered = true
                    break
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    lastError = throwable
                    Log.w(
                        TAG,
                        "segment=${segment.id} attempt=$attempt failed: ${throwable.message}",
                    )
                }
            }

            if (!delivered) {
                deliverFailure(
                    segment,
                    lastError ?: IllegalStateException("Transcription failed"),
                    onFailed,
                )
            }
        }
    }

    private suspend fun deliverFailure(
        segment: AyahSegment,
        error: Throwable,
        onFailed: suspend (AyahSegment, Throwable) -> Unit,
    ) {
        try {
            onFailed(segment, error)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failure listener threw", throwable)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val TAG = "LIVE_SOBOK_WHISPER"
    }
}
