package com.hafeziquran.app.data.whisper

/**
 * Thin JNI surface over the vendored whisper.cpp build.
 *
 * The native library is CPU-only and supports both shipped ABIs. [isAvailable]
 * lets callers degrade to a Bangla error instead of crashing when the .so cannot
 * be loaded (for example on an unsupported ABI).
 */
object WhisperBridge {
    private val loaded: Boolean = runCatching { System.loadLibrary("sobok") }.isSuccess

    val isAvailable: Boolean
        get() = loaded

    external fun initContext(modelPath: String): Long

    external fun freeContext(ctx: Long)

    external fun transcribe(ctx: Long, samples: FloatArray, language: String): String?
}
