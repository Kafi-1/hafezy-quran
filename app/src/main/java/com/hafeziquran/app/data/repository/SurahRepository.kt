package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.core.util.AppJson
import com.hafeziquran.app.data.model.SurahInfo
import com.hafeziquran.app.data.model.SurahsFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the 114-surah audio catalogue from `assets/surahs.json`, once per process.
 *
 * Bundled rather than fetched so the player's list, its search index, and the
 * downloader's targets all exist offline and are identical on every device.
 */
@Singleton
class SurahRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    @Volatile
    private var cache: List<SurahInfo>? = null

    suspend fun surahs(): Result<List<SurahInfo>> {
        cache?.let { return Result.success(it) }

        return mutex.withLock {
            cache?.let { return@withLock Result.success(it) }

            withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.assets.open(AppConstants.SURAHS_JSON_ASSET)
                        .bufferedReader()
                        .use { it.readText() }
                    AppJson.decodeFromString<SurahsFile>(raw).surahs.sortedBy { it.number }
                }
            }.onSuccess { cache = it }
        }
    }
}
