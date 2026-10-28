package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.core.util.AppJson
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.model.QuranIndex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the surah/para page index from `assets/quran_index.json` exactly once and
 * caches it for the process lifetime.
 *
 * The index is a few KB of JSON, so paying for one decode at startup is cheaper
 * and simpler than any on-disk cache.
 */
@Singleton
class QuranIndexRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val loadMutex = Mutex()

    private val _index = MutableStateFlow<Resource<QuranIndex>>(Resource.Loading)
    val index: StateFlow<Resource<QuranIndex>> = _index.asStateFlow()

    /** Applied to every raw page number; kept separate so it survives decode. */
    @Volatile
    private var pageOffset: Int = 0

    suspend fun load(): Resource<QuranIndex> {
        (_index.value as? Resource.Success)?.let { return it }

        return loadMutex.withLock {
            (_index.value as? Resource.Success)?.let { return@withLock it }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open(AppConstants.QURAN_INDEX_ASSET)
                        .bufferedReader()
                        .use { it.readText() }
                }.mapCatching { AppJson.decodeFromString<QuranIndex>(it) }
            }

            result.fold(
                onSuccess = { parsed ->
                    pageOffset = parsed.pageOffset
                    Resource.Success(parsed).also { _index.value = it }
                },
                onFailure = { error ->
                    Resource.Error(error, "সূরা সূচি লোড করা যায়নি").also { _index.value = it }
                },
            )
        }
    }

    /**
     * Converts a stored index page into a 1-based page number for the bundled PDF.
     * Pages are already physical, so with `pageOffset` at 0 this is the identity;
     * the offset is kept as a global nudge. Clamped to the document's real page
     * range so a bad index entry can never ask the reader for a page that does not
     * exist.
     */
    fun resolvePage(rawPage: Int): Int =
        (rawPage + pageOffset).coerceIn(1, AppConstants.PHYSICAL_PAGE_COUNT)

    /**
     * Maps a physical PDF page back to its standard Madani page for the
     * recitation's ayah lookup.
     *
     * There is no single offset to invert: the scan's pagination drifts away from
     * Madani as the mushaf progresses. We anchor on the surah that contains
     * [physicalPage] and carry its Madani start page forward, which is exact while
     * the scan keeps a surah's page spacing and at worst a page or two off inside
     * the few surahs it reflows. Front matter clamps to 1 so the text always has
     * content.
     */
    fun toMadaniPage(physicalPage: Int): Int {
        val surahs = (_index.value as? Resource.Success)?.data?.surahs.orEmpty()
        val anchor = surahs.lastOrNull { it.page <= physicalPage }
            ?: return physicalPage.coerceIn(1, AppConstants.MADANI_PAGE_COUNT)
        return (anchor.madaniPage + (physicalPage - anchor.page))
            .coerceIn(1, AppConstants.MADANI_PAGE_COUNT)
    }
}
