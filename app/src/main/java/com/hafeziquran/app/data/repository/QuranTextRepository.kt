package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.core.util.AppJson
import com.hafeziquran.app.data.model.QuranTextFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the Uthmani ayah text from `assets/quran_uthmani.json` once per process.
 *
 * The file is ~1.3 MB, so caching the decoded `number -> ayahs` map is cheaper
 * than re-decoding on every surah/ayah change in the Sobok screen.
 */
@Singleton
class QuranTextRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    @Volatile
    private var cache: Map<Int, List<String>>? = null

    suspend fun ayahs(surahNumber: Int): Result<List<String>> {
        val map = loadMap().getOrElse { return Result.failure(it) }
        val ayahs = map[surahNumber]
            ?: return Result.failure(IllegalArgumentException("Unknown surah $surahNumber"))
        return Result.success(ayahs)
    }

    suspend fun ayah(surahNumber: Int, ayahNumber: Int): Result<String> =
        ayahs(surahNumber).mapCatching { list ->
            require(ayahNumber in 1..list.size) { "Ayah $ayahNumber out of range" }
            list[ayahNumber - 1]
        }

    private suspend fun loadMap(): Result<Map<Int, List<String>>> {
        cache?.let { return Result.success(it) }
        return mutex.withLock {
            cache?.let { return@withLock Result.success(it) }
            load().onSuccess { cache = it }
        }
    }

    private suspend fun load(): Result<Map<Int, List<String>>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.assets.open(AppConstants.QURAN_TEXT_ASSET)
                .bufferedReader()
                .use { it.readText() }
            AppJson.decodeFromString<QuranTextFile>(raw)
                .surahs
                .associate { it.number to it.ayahs }
        }
    }
}
