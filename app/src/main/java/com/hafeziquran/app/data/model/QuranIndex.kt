package com.hafeziquran.app.data.model

import kotlinx.serialization.Serializable

/**
 * One of the 114 chapters.
 *
 * [page] is the *raw* index value from `assets/quran_index.json`, in the
 * document's own pagination. Callers must push it through
 * [com.hafeziquran.app.data.repository.QuranIndexRepository.resolvePage] so the
 * configurable `pageOffset` is applied before it reaches the reader.
 */
@Serializable
data class Surah(
    val number: Int,
    val arabic: String,
    val bangla: String,
    val meaning: String,
    val ayahs: Int,
    val revelation: String,
    val page: Int,
)

/** One of the 30 juz. Same [page] semantics as [Surah]. */
@Serializable
data class Para(
    val number: Int,
    val arabic: String,
    val bangla: String,
    val page: Int,
)

/**
 * Root object of `assets/quran_index.json`.
 *
 * [pageOffset] is added to every [Surah.page] / [Para.page]. It exists because
 * the shipped index uses the standard 604-page Madani pagination while the
 * bundled scan has 612 pages of its own; a single shift corrects the whole table
 * without editing 144 entries.
 */
@Serializable
data class QuranIndex(
    val pageOffset: Int = 0,
    val surahs: List<Surah> = emptyList(),
    val paras: List<Para> = emptyList(),
)

/** A PDF book listed in the "ইসলামিক বুক" tab. */
@Serializable
data class IslamicBook(
    val id: String,
    val title: String,
    val author: String,
    val asset: String,
    val description: String = "",
    /** Filled in at load time — false when [asset] is not present in the APK. */
    val isAvailable: Boolean = true,
)

@Serializable
data class BooksFile(val books: List<IslamicBook> = emptyList())

/** One zikr from `assets/tasbeeh.json`. */
@Serializable
data class TasbeehItem(
    val arabic: String,
    val bangla: String,
    val meaning: String,
)
