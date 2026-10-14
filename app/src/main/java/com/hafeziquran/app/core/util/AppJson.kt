package com.hafeziquran.app.core.util

import kotlinx.serialization.json.Json

/**
 * Shared, lenient JSON decoder for the bundled asset catalogues.
 *
 * `ignoreUnknownKeys` matters because the asset files carry a `_comment` field
 * documenting how to edit them — that comment must not break decoding.
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
