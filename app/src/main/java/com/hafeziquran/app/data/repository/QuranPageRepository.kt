package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.core.util.AppJson
import com.hafeziquran.app.data.recitation.PageAyah
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** `assets/quran_pages.json`: one `["surah:ayah", …]` list per Madani page. */
@Serializable
private data class QuranPagesFile(val pages: List<List<String>> = emptyList())

/**
 * Turns a Madani page number into the ordered ayah texts shown by the live
 * recitation screen.
 *
 * `quran_pages.json` stores only `surah:ayah` references (≈50 KB); the actual
 * Uthmani text still comes from [QuranTextRepository], which already caches the
 * 1.3 MB ayah map for the process.
 */
@Singleton
class QuranPageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textRepository: QuranTextRepository,
) {
    private val mutex = Mutex()

    @Volatile
    private var pageRefs: List<List<Pair<Int, Int>>>? = null

    suspend fun page(pageNumber: Int): Result<List<PageAyah>> = runCatching {
        val pages = pages()
        val refs = pages.getOrNull(pageNumber - 1)
            ?: error("Page $pageNumber is out of range (1-${pages.size})")

        refs.map { (surahNumber, ayahNumber) ->
            PageAyah(
                surahNumber = surahNumber,
                ayahNumber = ayahNumber,
                text = textRepository.ayah(surahNumber, ayahNumber).getOrThrow(),
            )
        }
    }

    private suspend fun pages(): List<List<Pair<Int, Int>>> {
        pageRefs?.let { return it }
        return mutex.withLock {
            pageRefs ?: load().also { pageRefs = it }
        }
    }

    private suspend fun load(): List<List<Pair<Int, Int>>> = withContext(Dispatchers.IO) {
        val raw = context.assets.open(AppConstants.QURAN_PAGES_ASSET)
            .bufferedReader()
            .use { it.readText() }
        AppJson.decodeFromString<QuranPagesFile>(raw).pages.map { refs ->
            refs.map { ref ->
                val separator = ref.indexOf(':')
                require(separator in 1 until ref.lastIndex) { "Bad page ref: $ref" }
                ref.substring(0, separator).toInt() to ref.substring(separator + 1).toInt()
            }
        }
    }
}
