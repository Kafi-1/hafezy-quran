package com.hafeziquran.app.data.recitation

/**
 * One VAD-delimited piece of recitation captured straight from the microphone.
 *
 * [pcm] is mono 16 kHz PCM-16 — the format whisper.cpp consumes. Segments are
 * deliberately plain objects with no behaviour: the recorder owns capture, the
 * queue owns buffering, and the worker owns inference.
 */
class AyahSegment(
    val id: Long,
    val createdAtMs: Long,
    val durationMs: Long,
    val pcm: ShortArray,
)

/** Verdict for one expected Quran word after alignment with the transcript. */
enum class WordMatchStatus { CORRECT, WRONG, MISSING, EXTRA }

/**
 * A single word shown to the user.
 *
 * [expectedWord] is the Uthmani token from the mushaf (empty for [WordMatchStatus.EXTRA]);
 * [spokenWord] is what Whisper heard (empty for [WordMatchStatus.MISSING]);
 * [similarityScore] is 0..1 character similarity after Arabic normalisation.
 */
data class WordStatus(
    val spokenWord: String,
    val expectedWord: String,
    val status: WordMatchStatus,
    val similarityScore: Float,
    /** False for words after a mid-ayah pause that have not been heard yet. */
    val covered: Boolean = true,
)

/** Result for one ayah; a single audio segment can produce several of these. */
data class AyahCheckResult(
    val segmentId: Long,
    val surahNumber: Int,
    val ayahNumber: Int,
    val words: List<WordStatus>,
    val recognizedText: String,
    /** False when the segment ended mid-ayah; the rest is still unread. */
    val complete: Boolean = true,
) {
    val correctCount: Int
        get() = words.count { it.status == WordMatchStatus.CORRECT }

    val accuracy: Float
        get() {
            val scored = words.count { it.status != WordMatchStatus.EXTRA }
            return if (scored == 0) 0f else correctCount.toFloat() / scored
        }
}

/** Output of the single Whisper worker. */
data class AyahTranscription(
    val segmentId: Long,
    val arabicText: String,
    val inferenceMs: Long,
    /** Time the segment waited in the bounded queue before inference started. */
    val queueWaitMs: Long = 0L,
)

/**
 * Live counters for the Sobok debug panel — cheap to update, never persisted.
 *
 * Timings are the most recent segment's, not averages: the operator wants to see
 * the current steady-state latency, and a rolling average would hide spikes.
 */
data class RecitationMetrics(
    val segmentCount: Int = 0,
    val failedCount: Int = 0,
    val discardedCount: Int = 0,
    val droppedCount: Int = 0,
    val modelLoadMs: Long = 0L,
    val lastInferenceMs: Long = 0L,
    val lastQueueWaitMs: Long = 0L,
    val lastTotalMs: Long = 0L,
    val lastAccuracy: Float = 0f,
)

/** One ayah belonging to the currently open mushaf page. */
data class PageAyah(
    val surahNumber: Int,
    val ayahNumber: Int,
    val text: String,
) {
    val words: List<String>
        get() = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
