package com.hafeziquran.app.data.asr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ONNX Runtime host for the FastConformer Quranic-Arabic model, replacing the
 * old whisper.cpp context. One [OrtSession] is kept for the whole process.
 *
 * The exported graph bundles the NeMo preprocessor (mel filterbank + normalise),
 * so the only input is raw 16 kHz mono PCM in `[-1, 1]`. Inference and load are
 * serialised by a [Mutex] so a Settings-driven [release] can never free the
 * session mid-transcription.
 */
@Singleton
class FastConformerEngine @Inject constructor() {

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
            mutex.withLock {
                runCatching {
                    ensureLoaded(modelPath, vocabPath)
                }
            }
        }

    suspend fun transcribe(
        modelPath: String,
        vocabPath: String,
        samples: FloatArray,
    ): Result<String> = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                require(samples.isNotEmpty()) { "Empty audio" }
                ensureLoaded(modelPath, vocabPath)
                Result.success(runInference(samples))
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

    private fun ensureLoaded(modelPath: String, vocabPath: String) {
        val key = "$modelPath|$vocabPath"
        if (session != null && loadedKey == key) return
        releaseLocked()

        val startedAt = SystemClock.elapsedRealtime()
        val env = environment ?: OrtEnvironment.getEnvironment().also { environment = it }
        val vocab = FastConformerVocab.load(java.io.File(vocabPath))
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(
                Runtime.getRuntime().availableProcessors().coerceIn(MIN_THREADS, MAX_THREADS),
            )
            setInterOpNumThreads(1)
        }
        val ortSession = try {
            env.createSession(modelPath, options)
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
        Log.i(TAG, "model loaded in ${lastLoadTimeMs}ms (inputs=$inputs): $modelPath")
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
                    val frames = shape[shape.size - 2].toInt()
                    val classes = shape[shape.size - 1].toInt()
                    val logProbs = output.floatBuffer
                        ?: error("Unexpected non-float model output")
                    return activeDecoder.decode(logProbs, frames, classes)
                }
            }
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
    }
}
