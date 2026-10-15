package com.hafeziquran.app.data.model

import kotlinx.serialization.Serializable

/**
 * One chapter's metadata for the audio player, from `assets/surahs.json`.
 *
 * Deliberately separate from [Surah] (the mushaf page index): that model carries
 * PDF page numbers, which the audio player neither has nor needs. Keeping the two
 * apart means editing one catalogue can never silently shift the other.
 */
@Serializable
data class SurahInfo(
    val number: Int,
    val arabic: String,
    val bangla: String,
    val english: String,
    val ayahs: Int,
)

/** Root object of `assets/surahs.json`. */
@Serializable
data class SurahsFile(val surahs: List<SurahInfo> = emptyList())
