package com.hafeziquran.app.data.recitation

import android.util.Log
import com.hafeziquran.app.BuildConfig
import com.hafeziquran.app.core.util.normalizeArabicWord
import com.hafeziquran.app.core.util.normalizedWordSimilarity
import kotlin.math.abs

/**
 * Maps Whisper transcripts onto the ayahs of the open page.
 *
 * The checker is stateful on purpose: [nextAyahIndex] starts at the top of the
 * page and only ever moves forward, so a segment is aligned against a small
 * window (at most [MAX_WINDOW_AYAHS] ayahs) instead of the whole Quran.
 *
 * Word mapping uses global Needleman–Wunsch alignment. Substitution cost is 0 for
 * words whose normalised character similarity passes [MATCH_SIMILARITY], so
 * diacritic and orthographic differences do not cascade. A segment that spans
 * several ayahs naturally produces one [AyahCheckResult] per ayah, because every
 * expected word remembers which ayah it belongs to.
 *
 * Mid-ayah pauses are supported: when alignment does not reach the end of the
 * last touched ayah, the cursor stays on that ayah with a word offset, and the
 * returned result marks the unheard tail as `covered = false` so the UI leaves it
 * in the normal text colour. The next segment continues from that offset.
 */
class QuranRecitationChecker(pageAyahs: List<PageAyah>) {

    private class ExpectedAyah(val source: PageAyah) {
        val displayWords: List<String> = source.words

        /** Null for decorative waqf marks that have no letters to recite. */
        val normalized: List<String?> = displayWords.map { word ->
            normalizeArabicWord(word).takeIf { it.isNotEmpty() }
        }

        val validRefs: List<Pair<Int, String>> = normalized.mapIndexedNotNull { index, value ->
            value?.let { index to it }
        }
    }

    private data class WindowRef(
        val ayahIndex: Int,
        val refIndex: Int,
        val normalized: String,
    )

    private sealed interface Op {
        data class Paired(val refIndex: Int, val spokenIndex: Int, val similarity: Float) : Op
        data class Missing(val refIndex: Int) : Op
        data class Extra(val spokenIndex: Int) : Op
    }

    private val ayahs: List<ExpectedAyah> = pageAyahs.map(::ExpectedAyah)

    @Volatile
    private var cursor: Int = 0

    /** Valid refs of [cursor] already matched by earlier, mid-ayah segments. */
    private var refOffset: Int = 0

    /** Per-ayah display-indexed verdicts, filled across partial segments. */
    private val merged = HashMap<Int, MutableList<WordStatus?>>()
    private val extrasByAyah = HashMap<Int, MutableList<WordStatus>>()

    val nextAyahIndex: Int
        get() = cursor

    val isPageFinished: Boolean
        get() = cursor >= ayahs.size

    fun check(segmentId: Long, recognizedText: String): List<AyahCheckResult> {
        val startAyah = cursor
        if (startAyah >= ayahs.size) return emptyList()

        var spokenDisplay = recognizedText.trim().split(WHITESPACE).filter { it.isNotBlank() }
        if (spokenDisplay.isEmpty()) {
            merged.getOrPut(startAyah) { newStatusList(startAyah) }
            return listOf(buildResult(segmentId, startAyah, recognizedText, complete = false))
        }

        if (shouldDropBismillah(startAyah) && startsWithBismillah(spokenDisplay)) {
            spokenDisplay = spokenDisplay.drop(BISMILLAH.size)
            if (spokenDisplay.isEmpty()) {
                merged.getOrPut(startAyah) { newStatusList(startAyah) }
                return listOf(buildResult(segmentId, startAyah, recognizedText, complete = false))
            }
        }

        val spoken = spokenDisplay.map { display -> display to normalizeArabicWord(display) }
            .filter { it.second.isNotEmpty() }
        if (spoken.isEmpty()) {
            merged.getOrPut(startAyah) { newStatusList(startAyah) }
            return listOf(buildResult(segmentId, startAyah, recognizedText, complete = false))
        }

        val window = buildWindow(spoken.size)
        if (window.isEmpty()) return emptyList()

        val ops = align(window, spoken.map { it.second })
        val lastPaired = ops.lastOrNull { it is Op.Paired } as? Op.Paired
            ?: return listOf(buildResult(segmentId, startAyah, recognizedText, complete = false))

        val lastRef = window[lastPaired.refIndex]
        val lastAyah = lastRef.ayahIndex
        val lastAyahRefCount = ayahs[lastAyah].validRefs.size
        val lastAyahComplete = lastRef.refIndex >= lastAyahRefCount - 1 - COMPLETION_TOLERANCE

        // Fill verdicts. Refs beyond the last paired word stay null for a partial
        // ayah (unheard), but become MISSING for ayahs the segment finished.
        //
        // Spoken words with no expected counterpart are attached to whichever ayah
        // the alignment had reached when they were heard, so a surplus word inside
        // ayah 3 stays on ayah 3 instead of being dumped on the last ayah touched.
        val extras = ArrayList<String>()
        var extraAyah = startAyah
        for (op in ops) {
            when (op) {
                is Op.Paired -> {
                    val ref = window[op.refIndex]
                    extraAyah = ref.ayahIndex
                    val displayIndex = ayahs[ref.ayahIndex].validRefs[ref.refIndex].first
                    val spokenWord = spoken[op.spokenIndex].first
                    val expectedWord = ayahs[ref.ayahIndex].displayWords[displayIndex]
                    statusList(ref.ayahIndex)[displayIndex] = WordStatus(
                        spokenWord = spokenWord,
                        expectedWord = expectedWord,
                        status = if (op.similarity >= MATCH_SIMILARITY) {
                            WordMatchStatus.CORRECT
                        } else {
                            WordMatchStatus.WRONG
                        },
                        similarityScore = op.similarity,
                    )
                }

                is Op.Missing -> {
                    val ref = window[op.refIndex]
                    extraAyah = ref.ayahIndex
                    val covered = ref.ayahIndex < lastAyah ||
                        (ref.ayahIndex == lastAyah &&
                            (lastAyahComplete || ref.refIndex <= lastRef.refIndex))
                    if (covered) {
                        val displayIndex = ayahs[ref.ayahIndex].validRefs[ref.refIndex].first
                        statusList(ref.ayahIndex)[displayIndex] = WordStatus(
                            spokenWord = "",
                            expectedWord = ayahs[ref.ayahIndex].displayWords[displayIndex],
                            status = WordMatchStatus.MISSING,
                            similarityScore = 0f,
                        )
                    }
                }

                is Op.Extra -> {
                    val spokenWord = spoken[op.spokenIndex].first
                    extras += spokenWord
                    val bucketIndex = extraAyah.coerceIn(startAyah, lastAyah)
                    val bucket = extrasByAyah.getOrPut(bucketIndex) { mutableListOf() }
                    // A hallucinated transcript can emit far more words than the
                    // ayah holds; cap the extras so one ayah cannot explode.
                    if (bucket.size < MAX_EXTRAS_PER_AYAH) {
                        bucket += WordStatus(
                            spokenWord = spokenWord,
                            expectedWord = "",
                            status = WordMatchStatus.EXTRA,
                            similarityScore = 0f,
                        )
                    }
                }
            }
        }

        if (BuildConfig.DEBUG) {
            Log.i(
                TAG,
                "segment=$segmentId start=$startAyah last=$lastAyah complete=$lastAyahComplete " +
                    "ops=${ops.size} extras=${extras.size} next=$cursor",
            )
        }

        if (lastAyahComplete) {
            cursor = lastAyah + 1
            refOffset = 0
        } else {
            cursor = lastAyah
            refOffset = (lastRef.refIndex + 1).coerceAtMost(lastAyahRefCount)
            if (refOffset >= lastAyahRefCount) {
                cursor = lastAyah + 1
                refOffset = 0
            }
        }

        return (startAyah..lastAyah).map { index ->
            val complete = index < lastAyah || lastAyahComplete
            buildResult(segmentId, index, recognizedText, complete)
        }
    }

    /**
     * Expected words from the current position, expanding whole ayahs until the
     * window can absorb the transcript plus a margin. Bounded in both ayahs and
     * words so a hallucinated long transcript cannot scan the whole page.
     */
    private fun buildWindow(spokenWordCount: Int): List<WindowRef> {
        val refs = ArrayList<WindowRef>()
        val target = minOf(spokenWordCount + WINDOW_WORD_MARGIN, MAX_WINDOW_WORDS)
        var ayahIndex = cursor
        var skip = refOffset
        var ayahsVisited = 0

        while (ayahIndex < ayahs.size && ayahsVisited < MAX_WINDOW_AYAHS) {
            val expected = ayahs[ayahIndex]
            for (refIndex in skip until expected.validRefs.size) {
                refs += WindowRef(
                    ayahIndex = ayahIndex,
                    refIndex = refIndex,
                    normalized = expected.validRefs[refIndex].second,
                )
                if (refs.size >= MAX_WINDOW_WORDS) break
            }
            skip = 0
            ayahsVisited++
            ayahIndex++
            if (refs.size >= target) break
        }
        return refs
    }

    private fun align(window: List<WindowRef>, spoken: List<String>): List<Op> {
        val n = window.size
        val m = spoken.size
        val costs = Array(n + 1) { FloatArray(m + 1) }

        for (i in 0..n) costs[i][0] = i.toFloat()
        for (j in 0..m) costs[0][j] = j.toFloat()

        for (i in 1..n) {
            val ref = window[i - 1].normalized
            val row = costs[i]
            val previous = costs[i - 1]
            for (j in 1..m) {
                val similarity = normalizedWordSimilarity(ref, spoken[j - 1])
                val substitution = previous[j - 1] + if (similarity >= MATCH_SIMILARITY) 0f else 1f
                row[j] = minOf(
                    substitution,
                    previous[j] + 1f,
                    row[j - 1] + 1f,
                )
            }
        }

        val ops = ArrayList<Op>(n + m)
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                val similarity = normalizedWordSimilarity(window[i - 1].normalized, spoken[j - 1])
                val substitution =
                    costs[i - 1][j - 1] + if (similarity >= MATCH_SIMILARITY) 0f else 1f
                if (abs(costs[i][j] - substitution) < EPSILON) {
                    ops += Op.Paired(i - 1, j - 1, similarity)
                    i--
                    j--
                    continue
                }
            }
            if (i > 0 && abs(costs[i][j] - (costs[i - 1][j] + 1f)) < EPSILON) {
                ops += Op.Missing(i - 1)
                i--
                continue
            }
            if (j > 0) {
                ops += Op.Extra(j - 1)
                j--
            } else {
                break
            }
        }
        ops.reverse()
        return ops
    }

    private fun buildResult(
        segmentId: Long,
        ayahIndex: Int,
        recognizedText: String,
        complete: Boolean,
    ): AyahCheckResult {
        val expected = ayahs[ayahIndex]
        val stored = merged[ayahIndex]
        val words = expected.displayWords.mapIndexed { displayIndex, word ->
            if (expected.normalized[displayIndex] == null) {
                // Waqf/sajda signs: keep them in the text but never colour them.
                WordStatus(
                    spokenWord = "",
                    expectedWord = word,
                    status = WordMatchStatus.CORRECT,
                    similarityScore = 1f,
                )
            } else {
                stored?.get(displayIndex) ?: WordStatus(
                    spokenWord = "",
                    expectedWord = word,
                    status = WordMatchStatus.MISSING,
                    similarityScore = 0f,
                    covered = complete,
                )
            }
        } + extrasByAyah[ayahIndex].orEmpty()

        return AyahCheckResult(
            segmentId = segmentId,
            surahNumber = expected.source.surahNumber,
            ayahNumber = expected.source.ayahNumber,
            words = words,
            recognizedText = recognizedText,
            complete = complete,
        )
    }

    private fun statusList(ayahIndex: Int): MutableList<WordStatus?> =
        merged.getOrPut(ayahIndex) { newStatusList(ayahIndex) }

    private fun newStatusList(ayahIndex: Int): MutableList<WordStatus?> =
        MutableList(ayahs[ayahIndex].displayWords.size) { null }

    private fun shouldDropBismillah(ayahIndex: Int): Boolean {
        if (refOffset != 0) return false
        val source = ayahs[ayahIndex].source
        return source.ayahNumber == 1 && source.surahNumber != 1 && source.surahNumber != 9
    }

    private fun startsWithBismillah(words: List<String>): Boolean {
        if (words.size < BISMILLAH.size) return false
        return words.take(BISMILLAH.size).map(::normalizeArabicWord) == BISMILLAH
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val BISMILLAH = listOf("بسم", "الله", "الرحمن", "الرحيم")

        const val MATCH_SIMILARITY = 0.7f
        const val COMPLETION_TOLERANCE = 1
        const val WINDOW_WORD_MARGIN = 16
        const val MAX_WINDOW_WORDS = 240
        const val MAX_WINDOW_AYAHS = 10
        const val MAX_EXTRAS_PER_AYAH = 12
        const val EPSILON = 1e-4f

        const val TAG = "LIVE_SOBOK_ALIGN"
    }
}
