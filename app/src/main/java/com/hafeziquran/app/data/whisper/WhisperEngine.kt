package com.hafeziquran.app.data.whisper

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single whisper.cpp context for the process.
 *
 * Loading the 78 MB model and running inference are both expensive and must
 * happen off the main thread. A [Mutex] serialises load/transcribe/free so the
 * context can never be freed while inference is running (Settings deletion).
 */
@Singleton
class WhisperEngine @Inject constructor() {

    private val mutex = Mutex()

    @Volatile
    private var contextPtr: Long = 0L

    @Volatile
    private var loadedPath: String? = null

    /** Wall-clock cost of the most recent model load; 0 until a load happens. */
    @Volatile
    var lastLoadTimeMs: Long = 0L
        private set

    suspend fun transcribe(modelPath: String, samples: FloatArray): Result<String> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                try {
                    check(WhisperBridge.isAvailable) { "Whisper native library unavailable" }
                    require(samples.isNotEmpty()) { "Empty audio" }
                    ensureLoaded(modelPath)
                    Result.success(
                        WhisperBridge.transcribe(contextPtr, samples, LANGUAGE)
                            ?: error("Whisper returned no transcription"),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Result.failure(throwable)
                }
            }
        }

    /** True once a model context is resident; used by the recitation screen UI. */
    val isReady: Boolean
        get() = contextPtr != 0L

    /**
     * Loads the model ahead of the first segment so recording never waits for the
     * ~1 s file parse on a cold start. Idempotent for the same path.
     */
    suspend fun preload(modelPath: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                try {
                    check(WhisperBridge.isAvailable) { "Whisper native library unavailable" }
                    ensureLoaded(modelPath)
                    Result.success(Unit)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Result.failure(throwable)
                }
            }
        }

    suspend fun release() {
        mutex.withLock { releaseLocked() }
    }

    private fun ensureLoaded(path: String) {
        if (contextPtr != 0L && loadedPath == path) return
        releaseLocked()
        val startedAt = SystemClock.elapsedRealtime()
        val created = WhisperBridge.initContext(path)
        check(created != 0L) { "Could not load Whisper model" }
        contextPtr = created
        loadedPath = path
        lastLoadTimeMs = SystemClock.elapsedRealtime() - startedAt
        Log.i(TAG, "model loaded in ${lastLoadTimeMs}ms: $path")
    }

    private fun releaseLocked() {
        if (contextPtr != 0L) {
            WhisperBridge.freeContext(contextPtr)
            contextPtr = 0L
        }
        loadedPath = null
    }

    private companion object {
        const val LANGUAGE = "ar"
        const val TAG = "LIVE_SOBOK_MODEL"
    }
}
