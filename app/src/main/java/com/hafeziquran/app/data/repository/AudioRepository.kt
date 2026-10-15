package com.hafeziquran.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.hafeziquran.app.data.download.DownloadManager
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.model.Reciter
import com.hafeziquran.app.data.model.SurahInfo
import com.hafeziquran.app.data.remote.QuranAudioApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Aggregate progress of a "download the whole Quran" run. */
data class BatchProgress(
    val done: Int = 0,
    val total: Int = 0,
    val running: Boolean = false,
) {
    val fraction: Float
        get() = if (total > 0) (done.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
}

/**
 * Owns downloaded surah audio: where the files live, whether a given
 * (reciter, surah) is on disk, its download state, and the media item the player
 * should use — the local file when present, the CDN otherwise.
 *
 * Files are app-private (`filesDir/audio/{slug}/{n}.mp3`) so no storage
 * permission is needed and uninstalling cleans them up.
 */
@Singleton
class AudioRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Every in-flight/known download, keyed by [key]. Shared by all reciters. */
    val downloads: StateFlow<Map<String, DownloadStatus>> = downloadManager.states

    private val _batch = MutableStateFlow(BatchProgress())
    val batch: StateFlow<BatchProgress> = _batch.asStateFlow()

    private var batchJob: Job? = null
    private var activeKey: String? = null

    fun key(reciter: Reciter, surahNumber: Int): String = "${reciter.slug}_$surahNumber"

    fun surahNumberFromKey(key: String): Int? = key.substringAfterLast('_').toIntOrNull()

    fun audioFile(reciter: Reciter, surahNumber: Int): File {
        val dir = File(context.filesDir, "audio/${reciter.slug}")
        dir.mkdirs()
        return File(dir, "$surahNumber.mp3")
    }

    fun isDownloaded(reciter: Reciter, surahNumber: Int): Boolean {
        val file = audioFile(reciter, surahNumber)
        return file.exists() && file.length() > 0L
    }

    /** Seeds the download map from disk so files from earlier sessions show as done. */
    fun seedDownloaded(reciter: Reciter) {
        val dir = File(context.filesDir, "audio/${reciter.slug}")
        val files = dir.listFiles() ?: return
        files.forEach { file ->
            val number = file.name.removeSuffix(".mp3").toIntOrNull() ?: return@forEach
            if (file.length() > 0L) {
                downloadManager.markCompletedIfPresent(key(reciter, number), file)
            }
        }
    }

    fun download(reciter: Reciter, surahNumber: Int) {
        val file = audioFile(reciter, surahNumber)
        if (file.exists() && file.length() > 0L) return
        downloadManager.start(
            key = key(reciter, surahNumber),
            url = QuranAudioApi.surahUrl(reciter, surahNumber),
            target = file,
        )
    }

    fun cancelDownload(reciter: Reciter, surahNumber: Int) =
        downloadManager.cancel(key(reciter, surahNumber))

    fun delete(reciter: Reciter, surahNumber: Int) {
        val file = audioFile(reciter, surahNumber)
        runCatching { file.delete() }
        runCatching { File(file.parentFile, file.name + ".part").delete() }
        downloadManager.reset(key(reciter, surahNumber))
    }

    /** Deletes every downloaded surah for [reciter]. Returns how many were removed. */
    fun deleteAll(reciter: Reciter): Int {
        val files = File(context.filesDir, "audio/${reciter.slug}").listFiles() ?: return 0
        var count = 0
        files.forEach { file ->
            val number = file.name.removeSuffix(".mp3").toIntOrNull()
            if (file.name.endsWith(".mp3") && file.delete()) count++
            if (number != null) downloadManager.reset(key(reciter, number))
        }
        return count
    }

    /**
     * Downloads [numbers] for [reciter] one after another, exposing [batch].
     *
     * Sequential rather than parallel: 114 simultaneous connections would hammer
     * the CDN and the device for no gain, and a single-file-at-a-time run gives a
     * stable, resumable-by-restarting progress count.
     */
    fun downloadAll(reciter: Reciter, numbers: List<Int>) {
        if (batchJob?.isActive == true) return
        batchJob = scope.launch {
            val pending = numbers.filter { !isDownloaded(reciter, it) }
            _batch.value = BatchProgress(0, pending.size, true)
            var done = 0
            for (number in pending) {
                val k = key(reciter, number)
                activeKey = k
                download(reciter, number)
                // Suspend until this file reaches a terminal state.
                downloadManager.states.first { map ->
                    when (map[k]) {
                        is DownloadStatus.Completed,
                        is DownloadStatus.Failed,
                        is DownloadStatus.Cancelled,
                        -> true

                        else -> false
                    }
                }
                activeKey = null
                done++
                _batch.value = BatchProgress(done, pending.size, true)
            }
            _batch.value = BatchProgress(pending.size, pending.size, false)
        }
    }

    fun cancelDownloadAll() {
        activeKey?.let { downloadManager.cancel(it) }
        activeKey = null
        batchJob?.cancel()
        batchJob = null
        _batch.value = BatchProgress()
    }

    /**
     * The player's item for one surah: the downloaded file when it exists,
     * otherwise the CDN stream URL. [MediaItem.mediaId] carries the surah number
     * so the UI can tell which row is playing.
     */
    fun mediaItem(reciter: Reciter, surah: SurahInfo): MediaItem {
        val file = audioFile(reciter, surah.number)
        val uri = if (file.exists() && file.length() > 0L) Uri.fromFile(file)
        else Uri.parse(QuranAudioApi.surahUrl(reciter, surah.number))

        return MediaItem.Builder()
            .setMediaId(surah.number.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(surah.bangla)
                    .setArtist(reciter.banglaName)
                    .build(),
            )
            .build()
    }
}
