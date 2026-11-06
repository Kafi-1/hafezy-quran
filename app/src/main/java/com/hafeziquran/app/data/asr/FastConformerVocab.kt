package com.hafeziquran.app.data.asr

import kotlinx.serialization.json.Json
import java.io.File

/**
 * SentencePiece vocabulary of the FastConformer CTC model.
 *
 * `vocab.json` is a flat JSON object of `"<id>": "<piece>"`; the ids run
 * `0..N-1` and the last one is the CTC blank. [blankId] therefore doubles as the
 * per-frame class count the decoder must arg-max over.
 */
class FastConformerVocab private constructor(private val pieces: Map<Int, String>) {

    /** Number of CTC output classes (one more than the highest id). */
    val size: Int = (pieces.keys.maxOrNull() ?: -1) + 1

    /** The CTC blank token id — never emitted into the transcript. */
    val blankId: Int = pieces.entries.firstOrNull { it.value == BLANK }?.key ?: (size - 1)

    /** The unknown token id, if the vocabulary defines one. */
    val unkId: Int = pieces.entries.firstOrNull { it.value == UNK }?.key ?: -1

    /** The SentencePiece piece for [id], or null when the id is out of range. */
    fun piece(id: Int): String? = pieces[id]

    companion object {
        private const val BLANK = "<blank>"
        private const val UNK = "<unk>"

        private val json = Json { ignoreUnknownKeys = true }

        fun load(file: File): FastConformerVocab = parse(file.readText())

        fun parse(text: String): FastConformerVocab {
            val raw: Map<String, String> = json.decodeFromString(text)
            val pieces = HashMap<Int, String>(raw.size)
            raw.forEach { (key, value) ->
                key.toIntOrNull()?.let { pieces[it] = value }
            }
            require(pieces.isNotEmpty()) { "Vocab is empty" }
            return FastConformerVocab(pieces)
        }
    }
}
