package com.hafeziquran.app.core.util

/**
 * Arabic word comparison used by the offline Sobok checker.
 *
 * Whisper emits modern, mostly undiacritised Arabic while the mushaf text uses
 * Uthmani orthography with tashkeel and waqf marks. Both sides are therefore
 * normalised before a word-level alignment is computed, and the *original*
 * tokens are what the UI colours.
 */
enum class WordStatus { CORRECT, INCORRECT, NEUTRAL }

data class AlignedWord(val text: String, val status: WordStatus)

data class AlignmentResult(
    val words: List<AlignedWord>,
    val extraWords: List<String>,
    val correctWords: Int,
    val scoredWords: Int,
    val recognizedText: String,
) {
    /** 0..1 of the meaningful reference words that were matched. */
    val accuracy: Float
        get() = if (scoredWords == 0) 0f else correctWords.toFloat() / scoredWords
}

private val WHITESPACE = Regex("\\s+")

private val BISMILLAH = listOf("بسم", "الله", "الرحمن", "الرحيم")

/** Display tokens of an ayah, preserving Uthmani diacritics and waqf marks. */
fun splitAyahWords(text: String): List<String> =
    text.trim().split(WHITESPACE).filter { it.isNotEmpty() }

/**
 * Strips diacritics, Quranic annotation signs and orthographic variants so that
 * `ٱلرَّحْمَٰنِ` and `الرحمن` normalise to the same word.
 */
fun normalizeArabicWord(word: String): String {
    val normalized = java.text.Normalizer.normalize(word, java.text.Normalizer.Form.NFC)
        .replace(DIACRITICS, "")
        .replace(TATWEEL, "")
        .replace(ALEF_VARIANTS, "\u0627")
        .replace('\u0649', '\u064A') // alef maqsura -> yeh
        .replace('\u0624', '\u0648') // waw with hamza -> waw
        .replace('\u0626', '\u064A') // yeh with hamza -> yeh
        .replace('\u0629', '\u0647') // ta marbuta -> heh
        .replace("\u0621", "") // standalone hamza
        .replace('\u06CC', '\u064A') // Farsi yeh
        .replace('\u06D2', '\u064A') // yeh barree (Urdu) -> yeh
        .replace('\u06A9', '\u0643') // keheh
        .replace('\u06BE', '\u0647') // heh doachashmee -> heh
    return normalized.filter { it in '\u0621'..'\u064A' }
}

/**
 * Aligns recognised words to reference words with a small Levenshtein DP.
 *
 * A near-identical word (character similarity >= [SIMILARITY_THRESHOLD]) is
 * accepted as correct, so spelling differences that survive normalisation do
 * not paint a correctly recited word red.
 */
fun alignAyah(
    originalText: String,
    recognizedText: String,
    ignoreLeadingBismillah: Boolean = false,
): AlignmentResult {
    val displayTokens = splitAyahWords(originalText)
    val reference = displayTokens.mapIndexedNotNull { index, word ->
        normalizeArabicWord(word).takeIf { it.isNotEmpty() }?.let { index to it }
    }

    var recognizedTokens = splitAyahWords(recognizedText)
    if (ignoreLeadingBismillah && recognizedTokens.size > BISMILLAH.size) {
        val head = recognizedTokens.take(BISMILLAH.size).map(::normalizeArabicWord)
        if (head == BISMILLAH) recognizedTokens = recognizedTokens.drop(BISMILLAH.size)
    }
    val recognized = recognizedTokens.map(::normalizeArabicWord)
        .mapIndexedNotNull { index, word -> word.takeIf { it.isNotEmpty() }?.let { index to it } }

    val n = reference.size
    val m = recognized.size
    val costs = Array(n + 1) { DoubleArray(m + 1) }
    for (i in 0..n) costs[i][0] = i.toDouble()
    for (j in 0..m) costs[0][j] = j.toDouble()

    for (i in 1..n) {
        for (j in 1..m) {
            val ref = reference[i - 1].second
            val rec = recognized[j - 1].second
            val substitution = costs[i - 1][j - 1] + if (wordsMatch(ref, rec)) 0.0 else 1.0
            val deletion = costs[i - 1][j] + 1.0
            val insertion = costs[i][j - 1] + 1.0
            costs[i][j] = minOf(substitution, deletion, insertion)
        }
    }

    val statusByDisplayIndex = HashMap<Int, WordStatus>()
    val extra = mutableListOf<String>()
    var i = n
    var j = m
    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 -> {
                val ref = reference[i - 1].second
                val rec = recognized[j - 1].second
                val diagonal = costs[i - 1][j - 1] + if (wordsMatch(ref, rec)) 0.0 else 1.0
                if (kotlin.math.abs(costs[i][j] - diagonal) < 1e-9) {
                    val displayIndex = reference[i - 1].first
                    statusByDisplayIndex[displayIndex] =
                        if (wordsMatch(ref, rec)) WordStatus.CORRECT else WordStatus.INCORRECT
                    i--
                    j--
                    continue
                }
                val deletion = costs[i - 1][j] + 1.0
                if (kotlin.math.abs(costs[i][j] - deletion) < 1e-9) {
                    statusByDisplayIndex[reference[i - 1].first] = WordStatus.INCORRECT
                    i--
                } else {
                    extra += recognizedTokens[recognized[j - 1].first]
                    j--
                }
            }
            i > 0 -> {
                statusByDisplayIndex[reference[i - 1].first] = WordStatus.INCORRECT
                i--
            }
            else -> {
                extra += recognizedTokens[recognized[j - 1].first]
                j--
            }
        }
    }

    val aligned = displayTokens.mapIndexed { index, word ->
        AlignedWord(
            text = word,
            status = statusByDisplayIndex[index] ?: WordStatus.NEUTRAL,
        )
    }
    val correct = aligned.count { it.status == WordStatus.CORRECT }
    return AlignmentResult(
        words = aligned,
        extraWords = extra.asReversed(),
        correctWords = correct,
        scoredWords = reference.size,
        recognizedText = recognizedText.trim(),
    )
}

private fun wordsMatch(a: String, b: String): Boolean {
    if (a == b) return true
    return normalizedWordSimilarity(a, b) >= SIMILARITY_THRESHOLD
}

/** 0..1 character similarity between two already-normalised Arabic words. */
fun normalizedWordSimilarity(a: String, b: String): Float {
    if (a == b) return 1f
    val maxLength = maxOf(a.length, b.length)
    if (maxLength == 0) return 1f
    return 1f - levenshtein(a, b).toFloat() / maxLength
}

private fun levenshtein(a: String, b: String): Int {
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(
                current[j - 1] + 1,
                previous[j] + 1,
                previous[j - 1] + cost,
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
}

private const val SIMILARITY_THRESHOLD = 0.7

private val DIACRITICS = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
private val TATWEEL = Regex("\\u0640")
private val ALEF_VARIANTS = Regex("[\\u0622\\u0623\\u0625\\u0671\\u0672\\u0673\\u0675]")
