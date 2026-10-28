package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.core.util.AppJson
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.download.DownloadManager
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.model.BookType
import com.hafeziquran.app.data.model.BooksFile
import com.hafeziquran.app.data.model.IslamicBook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the "ইসলামিক বুক" catalogue from `assets/books.json` and owns the
 * downloadable books' files.
 *
 * A book is either [BookType.LOCAL] (its PDF is bundled in the APK) or
 * [BookType.DOWNLOAD] (its PDF is fetched on demand and kept in private
 * `filesDir`, next to the Bangla mushaf — no permission, wiped on uninstall).
 * Downloading, resuming and deleting are all delegated to the shared
 * [DownloadManager], keyed by [IslamicBook.id].
 */
@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
) {
    suspend fun loadBooks(): Resource<List<IslamicBook>> = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(AppConstants.BOOKS_JSON_ASSET)
                .bufferedReader()
                .use { it.readText() }
        }.mapCatching { AppJson.decodeFromString<BooksFile>(it) }
            .map { file ->
                // A file left over from a previous session is already "downloaded".
                file.books.forEach { book ->
                    fileFor(book)?.let { downloadManager.markCompletedIfPresent(book.id, it) }
                }
                file.books
            }
            .fold(
                onSuccess = { Resource.Success(it) },
                onFailure = { Resource.Error(it, "বইয়ের তালিকা লোড করা যায়নি") },
            )
    }

    /** Resolves one catalogue entry by its stable [id], or null if unknown. */
    suspend fun findBook(id: String): IslamicBook? =
        (loadBooks() as? Resource.Success)?.data?.firstOrNull { it.id == id }

    /** The on-disk target for a downloadable book; null for a bundled one. */
    fun fileFor(book: IslamicBook): File? =
        if (book.type == BookType.DOWNLOAD) File(context.filesDir, "book_${book.id}.pdf") else null

    /** True if [assetPath] is packaged in the APK. */
    fun assetExists(assetPath: String?): Boolean =
        assetPath != null && runCatching { context.assets.open(assetPath).close() }.isSuccess

    /** Status of every book download, keyed by [IslamicBook.id]. */
    val downloadStates: StateFlow<Map<String, DownloadStatus>> = downloadManager.states

    fun startDownload(book: IslamicBook) {
        val url = book.url ?: return
        val target = fileFor(book) ?: return
        downloadManager.start(book.id, url, target)
    }

    fun cancelDownload(book: IslamicBook) = downloadManager.cancel(book.id)

    fun isDownloaded(book: IslamicBook): Boolean {
        val file = fileFor(book) ?: return false
        return file.exists() && file.length() > 0L
    }

    /** Deletes the downloaded file and any half-written `.part` beside it. */
    fun deleteDownload(book: IslamicBook) {
        val file = fileFor(book) ?: return
        runCatching { file.delete() }
        runCatching { File(file.parentFile, file.name + ".part").delete() }
        downloadManager.reset(book.id)
    }
}
