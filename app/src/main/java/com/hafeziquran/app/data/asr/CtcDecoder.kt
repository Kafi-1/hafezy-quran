package com.hafeziquran.app.data.asr

import java.nio.FloatBuffer

/**
 * Greedy CTC decoder: no external beam-search, so it is fast enough to run on a
 * mid-range phone between two recorded segments.
 *
 * For every frame it takes the arg-max class, collapses runs of the same class,
 * and drops the blank (and `<unk>`) that CTC inserts. The surviving pieces are
 * SentencePiece tokens — a leading `▁` (U+2581) marks a word boundary — so they
 * are concatenated and each `▁` becomes a space to rebuild normal words.
 */
class CtcDecoder(private val vocab: FastConformerVocab) {

    /**
     * Decodes a `[1, frames, vocab]` log-probability tensor laid out row-major.
     * [logProbs] must be positioned at the first frame and hold at least
     * `frames * classes` floats.
     */
    fun decode(logProbs: FloatBuffer, frames: Int, classes: Int): String {
        if (frames <= 0 || classes <= 0) return ""
        val out = StringBuilder()
        var previous = -1
        for (t in 0 until frames) {
            val base = t * classes
            var best = 0
            var bestScore = Float.NEGATIVE_INFINITY
            for (c in 0 until classes) {
                val score = logProbs.get(base + c)
                if (score > bestScore) {
                    bestScore = score
                    best = c
                }
            }
            if (best != previous && best != vocab.blankId && best != vocab.unkId) {
                vocab.piece(best)?.let { out.append(it) }
            }
            previous = best
        }
        return out.toString().replace(WORD_DELIMITER, ' ').trim()
    }

    private companion object {
        /** SentencePiece's word-start marker (U+2581). */
        const val WORD_DELIMITER = '\u2581'
    }
}
