package com.hafeziquran.app.core.util

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

private val SizeUnits = arrayOf("B", "KB", "MB", "GB")

/**
 * Formats a byte count as a short human-readable size, e.g. `345 KB`, `24.2 MB`.
 *
 * Uses binary (1024-based) units, matching what file managers and the download's
 * own `Content-Length` arithmetic produce, so the progress label and the total
 * never disagree with each other.
 */
fun Long.toReadableSize(): String {
    if (this <= 0L) return "0 B"
    if (this < 1024L) return "$this B"

    val exponent = (log10(this.toDouble()) / log10(1024.0))
        .toInt()
        .coerceIn(1, SizeUnits.lastIndex)
    val value = this / 1024.0.pow(exponent.toDouble())
    return String.format(Locale.US, "%.1f %s", value, SizeUnits[exponent])
}
