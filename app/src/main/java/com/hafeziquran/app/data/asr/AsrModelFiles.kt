package com.hafeziquran.app.data.asr

/**
 * Absolute paths of the two files the FastConformer pipeline needs: the ONNX
 * graph and its SentencePiece `vocab.json`. Both live in the same private
 * models directory and are only non-null once the download is complete.
 */
data class AsrModelFiles(
    val modelPath: String,
    val vocabPath: String,
)
