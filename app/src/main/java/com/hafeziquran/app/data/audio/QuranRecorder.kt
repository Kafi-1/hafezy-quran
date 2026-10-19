package com.hafeziquran.app.data.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Records mono 16 kHz PCM-16, the only format whisper.cpp accepts.
 *
 * [record] blocks on the IO dispatcher until [stop] is called or [maxDurationMs]
 * elapses. [stop] is thread-safe so the UI can toggle recording from the main
 * thread while the read loop is running.
 */
class QuranRecorder @Inject constructor() {

    @Volatile
    private var stopRequested = false

    @Volatile
    private var active: Boolean = false

    private var audioRecord: AudioRecord? = null

    val isRecording: Boolean
        get() = active

    suspend fun record(
        maxDurationMs: Long,
        onElapsedMs: (Int) -> Unit,
    ): ShortArray = withContext(Dispatchers.IO) {
        stopRequested = false

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        require(bufferSize > 0) { "Microphone is not available" }

        val recorder = createAudioRecord(maxOf(bufferSize, SAMPLE_RATE / 10 * 2))
        audioRecord = recorder

        val capacity = (SAMPLE_RATE.toLong() * maxDurationMs / 1000L).toInt()
        val output = ShortArray(capacity)
        val chunk = ShortArray(maxOf(bufferSize / 2, 1600))
        var total = 0

        try {
            recorder.startRecording()
            active = true
            val startedAt = SystemClock.elapsedRealtime()

            while (!stopRequested && total < capacity) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) {
                    val count = minOf(read, capacity - total)
                    chunk.copyInto(output, total, 0, count)
                    total += count
                } else if (read < 0) {
                    break
                }
                onElapsedMs((SystemClock.elapsedRealtime() - startedAt).toInt())
            }
        } finally {
            active = false
            runCatching { recorder.stop() }
            recorder.release()
            audioRecord = null
        }

        output.copyOf(total)
    }

    fun stop() {
        stopRequested = true
        runCatching { audioRecord?.stop() }
    }

    private fun createAudioRecord(bufferSize: Int): AudioRecord {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        for (source in sources) {
            val record = AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize)
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            record.release()
        }
        error("Microphone is not available")
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_DURATION_MS = 25_000L

        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}

/** Converts PCM-16 samples into the normalised float range Whisper expects. */
fun ShortArray.toWhisperSamples(): FloatArray =
    FloatArray(size) { this[it] / 32768f }
