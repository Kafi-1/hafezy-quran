package com.hafeziquran.app.data.recitation

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Continuous microphone capture with a small energy-based VAD.
 *
 * The recorder never waits for Whisper: completed segments are handed to the
 * caller's channel with a non-blocking `trySend`, so the capture loop keeps
 * running while the queue drains in the background. Speech boundaries follow a
 * hysteresis rule (a higher threshold to start, a lower one to stay in speech),
 * which keeps quiet syllables from splitting an ayah in half.
 *
 * The VAD is intentionally dependency-free. Silero/WebRTC would need ~2 MB of
 * native models; for a single-speaker, close-mic recitation a normalised RMS
 * gate with an adaptive noise floor is both lighter and predictable on low-end
 * phones. Thresholds are constants at the bottom of this file.
 */
class AyahSegmentRecorder @Inject constructor() {

    @Volatile
    private var stopRequested = false

    @Volatile
    private var active = false

    @Volatile
    private var audioRecord: AudioRecord? = null

    private var nextSegmentId = 1L

    val isRecording: Boolean
        get() = active

    /**
     * Captures until [stop] is called. Every 700–900 ms of silence closes the
     * current segment; segments shorter than [MIN_SEGMENT_MS] are discarded as
     * false triggers.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(
        events: SendChannel<AyahSegment>,
        onDiscarded: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        stopRequested = false

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        require(minBuffer > 0) { "Microphone is not available" }

        val recorder = createAudioRecord(maxOf(minBuffer, FRAME_SAMPLES * 8 * 2))
        audioRecord = recorder

        val frame = ShortArray(FRAME_SAMPLES)
        val preRoll = PreRollRing(PRE_ROLL_FRAMES)
        val segment = PcmBuffer(initialCapacity = SAMPLE_RATE * 2)
        var noiseFloor = NOISE_FLOOR_INITIAL
        var inSpeech = false
        var speechRun = 0
        var silenceFrames = 0

        fun finishSegment(dropTrailingSilence: Boolean) {
            if (dropTrailingSilence && silenceFrames > POST_ROLL_FRAMES) {
                segment.trimFrames(silenceFrames - POST_ROLL_FRAMES)
            }
            val samples = segment.toShortArray()
            val durationMs = samples.size * 1000L / SAMPLE_RATE
            if (durationMs >= MIN_SEGMENT_MS) {
                val captured = AyahSegment(
                    id = nextSegmentId++,
                    createdAtMs = SystemClock.elapsedRealtime(),
                    durationMs = durationMs,
                    pcm = samples,
                )
                if (!events.trySend(captured).isSuccess) {
                    // The producer channel is unbounded, so this should not happen;
                    // log rather than silently lose a captured segment.
                    Log.w(TAG, "Segment ${captured.id} dropped: event channel closed")
                }
            } else {
                Log.d(TAG, "discarded ${durationMs}ms segment below ${MIN_SEGMENT_MS}ms floor")
                onDiscarded()
            }
            segment.clear()
            inSpeech = false
            speechRun = 0
            silenceFrames = 0
        }

        try {
            recorder.startRecording()
            active = true

            capture@ while (!stopRequested && coroutineContext.isActive) {
                var filled = 0
                while (filled < FRAME_SAMPLES) {
                    val read = recorder.read(frame, filled, FRAME_SAMPLES - filled)
                    when {
                        read > 0 -> filled += read
                        read == 0 -> Unit
                        else -> break@capture
                    }
                }
                if (filled < FRAME_SAMPLES) break@capture

                val rms = rms(frame)
                preRoll.push(frame)

                val speechThreshold = maxOf(ABS_SPEECH_RMS, noiseFloor * SPEECH_NOISE_FACTOR)
                val silenceThreshold = maxOf(ABS_SILENCE_RMS, noiseFloor * SILENCE_NOISE_FACTOR)
                var startedThisFrame = false

                if (!inSpeech) {
                    if (rms < speechThreshold * 2f) {
                        noiseFloor = (noiseFloor * 0.97f + rms * 0.03f)
                            .coerceIn(NOISE_FLOOR_MIN, NOISE_FLOOR_MAX)
                    }
                    if (rms >= speechThreshold) {
                        speechRun++
                        if (speechRun >= START_SPEECH_FRAMES) {
                            inSpeech = true
                            silenceFrames = 0
                            segment.clear()
                            preRoll.appendTo(segment)
                            startedThisFrame = true
                        }
                    } else {
                        speechRun = 0
                    }
                }

                if (inSpeech) {
                    if (rms >= silenceThreshold) {
                        silenceFrames = 0
                    } else {
                        silenceFrames++
                    }
                    // The trigger frame is already in the pre-roll copy.
                    if (!startedThisFrame) segment.appendFrame(frame)

                    when {
                        silenceFrames >= END_SILENCE_FRAMES -> finishSegment(dropTrailingSilence = true)
                        segment.sampleCount >= MAX_SEGMENT_SAMPLES -> {
                            finishSegment(dropTrailingSilence = false)
                            // A very fast reciter can run past the hard cap; start
                            // the next segment from the pre-roll instead of losing
                            // the still-active speech.
                            if (rms >= silenceThreshold && !stopRequested) {
                                inSpeech = true
                                preRoll.appendTo(segment)
                            }
                        }
                    }
                }
            }

            if (inSpeech) finishSegment(dropTrailingSilence = true)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "Microphone capture failed", throwable)
            onError(throwable)
        } finally {
            active = false
            runCatching { recorder.stop() }
            recorder.release()
            audioRecord = null
        }
    }

    fun stop() {
        stopRequested = true
        runCatching { audioRecord?.stop() }
    }

    private fun rms(frame: ShortArray): Float {
        var sum = 0.0
        for (sample in frame) {
            val value = sample.toDouble()
            sum += value * value
        }
        return (sqrt(sum / frame.size) / 32768.0).toFloat()
    }

    @SuppressLint("MissingPermission")
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
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000

        /** ~700–900 ms per the product spec; 40 frames = 800 ms. */
        const val END_SILENCE_MS = 800
        const val MIN_SEGMENT_MS = 1_500L

        private const val END_SILENCE_FRAMES = END_SILENCE_MS / FRAME_MS
        private const val START_SPEECH_FRAMES = 2
        private const val PRE_ROLL_FRAMES = 12 // 240 ms before trigger
        private const val POST_ROLL_FRAMES = 12 // keep 240 ms of tail silence
        private const val MAX_SEGMENT_SAMPLES = SAMPLE_RATE * 20 // hard 20 s cap

        private const val NOISE_FLOOR_INITIAL = 0.004f
        private const val NOISE_FLOOR_MIN = 0.0005f
        private const val NOISE_FLOOR_MAX = 0.02f

        /** Absolute RMS floors, used when the room is near-silent. */
        private const val ABS_SPEECH_RMS = 0.011f
        private const val ABS_SILENCE_RMS = 0.0055f

        /** Adaptive multipliers over the measured noise floor. */
        private const val SPEECH_NOISE_FACTOR = 2.8f
        private const val SILENCE_NOISE_FACTOR = 2.0f

        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val TAG = "LIVE_SOBOK_VAD"
    }
}

/** Growable PCM-16 buffer; reallocates only when a segment outgrows it. */
private class PcmBuffer(initialCapacity: Int) {
    private var data = ShortArray(initialCapacity)

    var sampleCount: Int = 0
        private set

    fun appendFrame(frame: ShortArray) {
        ensureCapacity(sampleCount + frame.size)
        frame.copyInto(data, sampleCount)
        sampleCount += frame.size
    }

    fun trimFrames(frames: Int) {
        sampleCount = maxOf(0, sampleCount - frames * AyahSegmentRecorder.FRAME_SAMPLES)
    }

    fun clear() {
        sampleCount = 0
    }

    fun toShortArray(): ShortArray = data.copyOf(sampleCount)

    private fun ensureCapacity(required: Int) {
        if (required <= data.size) return
        var next = data.size * 2
        while (next < required) next *= 2
        data = data.copyOf(next)
    }
}

/** Fixed ring of recent frames used as pre-roll before speech is confirmed. */
private class PreRollRing(private val slots: Int) {
    private val frames = Array(slots) { ShortArray(AyahSegmentRecorder.FRAME_SAMPLES) }
    private var next = 0
    private var count = 0

    fun push(frame: ShortArray) {
        frame.copyInto(frames[next])
        next = (next + 1) % slots
        if (count < slots) count++
    }

    fun appendTo(buffer: PcmBuffer) {
        val start = (next - count + slots) % slots
        for (offset in 0 until count) {
            buffer.appendFrame(frames[(start + offset) % slots])
        }
    }
}
