package com.hafeziquran.app.core.util

/**
 * Extracts the 11-character YouTube id from whatever an editor typed into
 * Firestore — a bare id, a `youtu.be` short link, a watch URL, an embed URL or a
 * Shorts URL all normalise to the same id.
 *
 * Without this, a catalogue entry that stored a full URL produced an embed src of
 * `.../embed/https://youtu.be/xxxx`, which YouTube answers with an empty player —
 * a black rectangle.
 */
fun normalizeYouTubeId(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return value
    ID_PATTERNS.forEach { pattern ->
        pattern.find(value)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
    }
    return value
}

private val ID_PATTERNS = listOf(
    Regex("youtu\\.be/([A-Za-z0-9_-]{6,})"),
    Regex("[?&]v=([A-Za-z0-9_-]{6,})"),
    Regex("/embed/([A-Za-z0-9_-]{6,})"),
    Regex("/shorts/([A-Za-z0-9_-]{6,})"),
    Regex("/v/([A-Za-z0-9_-]{6,})"),
    Regex("/live/([A-Za-z0-9_-]{6,})"),
)