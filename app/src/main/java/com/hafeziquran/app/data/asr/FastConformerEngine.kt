package com.hafeziquran.app.data.asr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.hafeziquran.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ONNX Runtime host for the FastConformer Quranic-Arabic model.
 *
 * One [OrtSession] is kept for the whole process and guarded by a [Mutex], so a
 * Settings-driven delete can never free the session mid-transcription.
 *
 * Native ONNX failures cannot be caught in Kotlin, so the engine refuses to enter
 * the native loader when the file looks corrupt or the device is short on memory
 * ([AsrModelValidator], [requireMemoryFor]). Everything after that is wrapped in
 * `Result` so callers always get a Bangla-able failure instead of a process kill.
 */
@Singleton
class FastConformerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val mutex = Mutex()

    private var environment: OrtEnvironment? = null

    // Read by isReady (main thread); written under the mutex.
    @Volatile
    private var session: OrtSession? = null

    private var decoder: CtcDecoder? = null

    private var loadedKey: String? = null
    private var audioInput = DEFAULT_AUDIO_INPUT
    private var lengthInput = DEFAULT_LENGTH_INPUT

    /** Wall-clock cost of the most recent model load; 0 until a load happens. */
    @Volatile
    var lastLoadTimeMs: Long = 0L
        private set

    /** True once a session is resident; used by the recitation screen UI. */
    val isReady: Boolean
        get() = session != null

    /**
     * Loads the graph and vocabulary ahead of the first segment so recording
     * never waits for the parse on a cold start. Idempotent for the same files.
     */
    suspend fun preload(modelPath: String, vocabPath: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                mutex.withLock { ensureLoaded(modelPath, vocabPath) }
                Result.success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "model preload failed", throwable)
                Result.failure(throwable)
            }
        }

    /**
     * Transcribes one segment. Audio longer than [MAX_AUDIO_SECONDS] is truncated
     * before inference: the graph has no length limit of its own, and a runaway
     * segment would multiply the attention buffers until the process is killed.
     */
    suspend fun transcribe(
        modelPath: String,
        vocabPath: String,
        samples: FloatArray,
    ): Result<String> = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                if (samples.isEmpty()) throw IllegalArgumentException("Empty audio")
                val usable = limitAudio(samples)
                ensureLoaded(modelPath, vocabPath)
                Result.success(runInference(usable))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "transcription failed", throwable)
                Result.failure(throwable)
            }
        }
    }

    suspend fun release() {
        mutex.withLock { releaseLocked() }
    }

    private fun ensureLoaded(modelPath: String, vocabPath: String) {
        val key = "$modelPath|$vocabPath"
        if (session != null && loadedKey == key) return

        // Free the previous graph before measuring headroom for the next one.
        releaseLocked()

        val modelFile = File(modelPath)
        val vocabFile = File(vocabPath)
        check(AsrModelValidator.isValidModel(modelFile, 1L)) {
            "Model file is missing or corrupt"
        }
        check(AsrModelValidator.isValidVocab(vocabFile, 1L)) {
            "Vocabulary file is missing or corrupt"
        }
        requireMemoryFor(modelFile)

        val startedAt = SystemClock.elapsedRealtime()
        val env = environment ?: OrtEnvironment.getEnvironment().also { environment = it }
        val vocab = FastConformerVocab.load(vocabFile)
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(
                Runtime.getRuntime().availableProcessors().coerceIn(MIN_THREADS, MAX_THREADS),
            )
            setInterOpNumThreads(1)
        }
        val ortSession = try {
            try {
                env.createSession(modelPath, options)
            } catch (oom: OutOfMemoryError) {
                throw IllegalStateException("Not enough memory to load the model", oom)
            } catch (throwable: Throwable) {
                throw IllegalStateException("ONNX Runtime could not load the model", throwable)
            }
        } finally {
            options.close()
        }

        val inputs = ortSession.inputNames.toList()
        audioInput = inputs.firstOrNull { it.equals("audio_signal", true) }
            ?: inputs.firstOrNull { !it.contains("len", true) }
            ?: DEFAULT_AUDIO_INPUT
        lengthInput = inputs.firstOrNull { it != audioInput } ?: DEFAULT_LENGTH_INPUT

        session = ortSession
        decoder = CtcDecoder(vocab)
        loadedKey = key
        lastLoadTimeMs = SystemClock.elapsedRealtime() - startedAt
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "model loaded in ${lastLoadTimeMs}ms (inputs=$inputs): $modelPath")
        }
    }

    private fun runInference(samples: FloatArray): String {
        val env = environment ?: error("ONNX environment unavailable")
        val ortSession = session ?: error("Model is not loaded")
        val activeDecoder = decoder ?: error("Model is not loaded")

        val count = samples.size.toLong()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), longArrayOf(1, count)).use { audio ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(count)), longArrayOf(1)).use { length ->
                val inputs = HashMap<String, OnnxTensor>(2)
                inputs[audioInput] = audio
                inputs[lengthInput] = length

                ortSession.run(inputs).use { result ->
                    val output = result.get(0) as OnnxTensor
                    val shape = (output.info as TensorInfo).shape
                    check(shape.size >= 2) { "Unexpected model output rank ${shape.size}" }
                    val frames = shape[shape.size - 2].toInt()
                    val classes = shape[shape.size - 1].toInt()
                    check(frames > 0 && classes > 0) { "Model returned an empty output" }
                    val logProbs = output.floatBuffer
                        ?: error("Unexpected non-float model output")
                    return activeDecoder.decode(logProbs, frames, classes)
                }
            }
        }
    }

    private fun limitAudio(samples: FloatArray): FloatArray {
        if (samples.size < MIN_AUDIO_SAMPLES) {
            throw IllegalArgumentException("Audio is too short: ${samples.size} samples")
        }
        if (samples.size <= MAX_AUDIO_SAMPLES) return samples
        if (BuildConfig.DEBUG) {
            Log.w(
                TAG,
                "audio truncated from ${samples.size} to $MAX_AUDIO_SAMPLES samples " +
                    "($MAX_AUDIO_SECONDS s limit)",
            )
        }
        return samples.copyOf(MAX_AUDIO_SAMPLES)
    }

    /**
     * `availMem` includes reclaimable page cache, so it is the closest practical
     * estimate of what the native allocator can get. The graph itself plus the
     * inference arena needs roughly the file size again, plus headroom for the
     * preprocessor and decoder buffers.
     */
    private fun requireMemoryFor(modelFile: File) {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val info = ActivityManager.MemoryInfo()
        if (!runCatching { manager.getMemoryInfo(info) }.isSuccess) return

        val required = modelFile.length() + MEMORY_HEADROOM_BYTES
        if (info.lowMemory || info.availMem < required) {
            throw IllegalStateException(
                "Insufficient memory to load the model: need ~${required / MIB}MB, " +
                    "available ${info.availMem / MIB}MB",
            )
        }
    }

    private fun releaseLocked() {
        session?.runCatching { close() }
        session = null
        decoder = null
        loadedKey = null
    }

    private companion object {
        const val TAG = "SOBOK_ASR"
        const val MIN_THREADS = 2
        const val MAX_THREADS = 4
        const val DEFAULT_AUDIO_INPUT = "audio_signal"
        const val DEFAULT_LENGTH_INPUT = "length"

        const val SAMPLE_RATE = 16_000
        const val MAX_AUDIO_SECONDS = 15
        const val MAX_AUDIO_SAMPLES = SAMPLE_RATE * MAX_AUDIO_SECONDS

        /** ~250 ms; shorter than this is an accidental tap, not a recitation. */
        const val MIN_AUDIO_SAMPLES = SAMPLE_RATE / 4

        const val MEMORY_HEADROOM_BYTES = 192L * 1024L * 1024L
        const val MIB = 1024L * 1024L
    }
}