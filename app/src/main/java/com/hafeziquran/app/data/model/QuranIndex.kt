package com.hafeziquran.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One of the 114 chapters.
 *
 * [page] is the *physical* page (1-612) in the bundled `quran.pdf`, taken from
 * the scan's own outline. Callers still push it through
 * [com.hafeziquran.app.data.repository.QuranIndexRepository.resolvePage] so the
 * global `pageOffset` nudge is applied.
 *
 * [madaniPage] keeps the standard 604-page Madani number, used only by
 * [com.hafeziquran.app.data.repository.QuranIndexRepository.toMadaniPage] to map
 * a physical page back for the recitation ayah lookup.
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
    val madaniPage: Int = 0,
)

/** One of the 30 juz. Same [page] / [madaniPage] semantics as [Surah]. */
@Serializable
data class Para(
    val number: Int,
    val arabic: String,
    val bangla: String,
    val page: Int,
    val madaniPage: Int = 0,
)

/**
 * Root object of `assets/quran_index.json`.
 *
 * Pages are stored directly as *physical* pages: the 612-page scan's pagination
 * drifts away from the standard 604-page Madani index (by +1 up to +7), so no
 * single offset can reconcile the two. [pageOffset] survives only as a global
 * nudge added to every [Surah.page] / [Para.page] and is currently 0.
 */
@Serializable
data class QuranIndex(
    val pageOffset: Int = 0,
    val surahs: List<Surah> = emptyList(),
    val paras: List<Para> = emptyList(),
)

/** Where a catalogue book's PDF comes from. */
@Serializable
enum class BookType {
    /** Bundled in the APK under [IslamicBook.asset]. */
    @SerialName("local")
    LOCAL,

    /** Fetched on demand from [IslamicBook.url] into private storage. */
    @SerialName("download")
    DOWNLOAD,
}

/** A PDF book listed in the "ইসলামিক বুক" tab. */
@Serializable
data class IslamicBook(
    val id: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val type: BookType = BookType.LOCAL,
    /** Asset path for [BookType.LOCAL]; null otherwise. */
    val asset: String? = null,
    /** Remote URL for [BookType.DOWNLOAD]; null otherwise. */
    val url: String? = null,
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
