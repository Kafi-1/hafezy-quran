package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.data.download.DownloadManager
import com.hafeziquran.app.data.download.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the optional Bangla translation mushaf: its on-disk file, its download,
 * and its removal.
 *
 * The file lives in the app's private `filesDir`, which needs no storage
 * permission and is wiped with an uninstall — the only storage model that is
 * both Play-policy clean and reachable by Pdfium through a file descriptor.
 */
@Singleton
class BanglaQuranRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val downloadManager: DownloadManager,
) {
    val file: File = File(context.filesDir, AppConstants.BANGLA_QURAN_FILE)

    init {
        // A file left over from a previous session is already "downloaded".
        downloadManager.markCompletedIfPresent(KEY, file)
    }

    val downloadState: Flow<DownloadStatus> = downloadManager.stateFlow(KEY)

    fun startDownload() = downloadManager.start(KEY, AppConstants.BANGLA_QURAN_URL, file)

    fun pauseDownload() = downloadManager.pause(KEY)

    fun cancelDownload() = downloadManager.cancel(KEY)

    fun isDownloaded(): Boolean = file.exists() && file.length() > 0L

    fun sizeBytes(): Long = if (file.exists()) file.length() else 0L

    /** Deletes the downloaded file and any half-written `.part` beside it. */
    fun delete() {
        runCatching { file.delete() }
        runCatching { File(file.parentFile, file.name + ".part").delete() }
        downloadManager.reset(KEY)
    }

    private companion object {
        const val KEY = "bangla_quran_pdf"
    }
}
