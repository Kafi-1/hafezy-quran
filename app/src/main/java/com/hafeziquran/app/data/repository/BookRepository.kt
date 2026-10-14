package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.core.util.AppJson
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.model.BooksFile
import com.hafeziquran.app.data.model.IslamicBook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the "ইসলামিক বুক" catalogue from `assets/books.json`.
 *
 * Each entry is probed against the APK so a book whose PDF has not been dropped
 * into `assets/` yet is flagged [IslamicBook.isAvailable] = false rather than
 * failing when the user taps it.
 */
@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun loadBooks(): Resource<List<IslamicBook>> = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(AppConstants.BOOKS_JSON_ASSET)
                .bufferedReader()
                .use { it.readText() }
        }.mapCatching { AppJson.decodeFromString<BooksFile>(it) }
            .map { file -> file.books.map { it.copy(isAvailable = assetExists(it.asset)) } }
            .fold(
                onSuccess = { Resource.Success(it) },
                onFailure = { Resource.Error(it, "বইয়ের তালিকা লোড করা যায়নি") },
            )
    }

    /** True if [assetPath] is packaged in the APK. */
    private fun assetExists(assetPath: String): Boolean =
        runCatching { context.assets.open(assetPath).close() }.isSuccess
}
