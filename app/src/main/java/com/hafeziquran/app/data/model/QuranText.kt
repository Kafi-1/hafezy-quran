package com.hafeziquran.app.data.model

import kotlinx.serialization.Serializable

/** One surah's ayah texts (1-based order) from `assets/quran_uthmani.json`. */
@Serializable
data class SurahText(
    val number: Int,
    val ayahs: List<String> = emptyList(),
)

/** Root object of `assets/quran_uthmani.json`. */
@Serializable
data class QuranTextFile(val surahs: List<SurahText> = emptyList())
