package com.hafeziquran.app.data.download

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Lifecycle of a single background download, keyed by an app-chosen id. */
sealed interface DownloadStatus {
    data object Idle : DownloadStatus

    data class InProgress(val bytes: Long, val total: Long) : DownloadStatus {
        /** 0..1, or 0 when the server did not send a `Content-Length`. */
        val fraction: Float
            get() = if (total > 0L) (bytes.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    }

    data class Completed(val bytes: Long) : DownloadStatus

    data class Failed(val message: String) : DownloadStatus

    data object Cancelled : DownloadStatus
}

/**
 * A small streaming downloader for large assets (the 25 MB Bangla mushaf, surah
 * audio). Deliberately *not* Android's `DownloadManager`: that hands the file to
 * a system service and gives no in-app progress stream, no per-file cancel, and
 * on API 29+ writes into the public collection where our offline reader cannot
 * reach it without a permission.
 *
 * Every download streams to `<name>.part` and is renamed into place only after it
 * completes, so a killed process or a cancelled download can never leave a
 * half-written file that the reader would then try to open.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val states: StateFlow<Map<String, DownloadStatus>> = _states.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()

    fun stateFlow(key: String): Flow<DownloadStatus> =
        states.map { it[key] ?: DownloadStatus.Idle }

    /** Seeds the state from disk for a file that may already be downloaded. */
    fun markCompletedIfPresent(key: String, file: File) {
        if (file.exists() && file.length() > 0L) {
            set(key, DownloadStatus.Completed(file.length()))
        }
    }

    fun start(key: String, url: String, target: File) {
        if (jobs[key]?.isActive == true) return
        jobs[key] = scope.launch { runDownload(key, url, target) }
    }

    fun cancel(key: String) {
        jobs.remove(key)?.cancel()
        set(key, DownloadStatus.Cancelled)
    }

    /** Cancels any in-flight job and forgets the key entirely (back to Idle). */
    fun reset(key: String) {
        jobs.remove(key)?.cancel()
        set(key, DownloadStatus.Idle)
    }

    private suspend fun runDownload(key: String, url: String, target: File) {
        val partFile = File(target.parentFile, target.name + ".part")
        try {
            val alreadyHave = if (partFile.exists()) partFile.length() else 0L

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                if (alreadyHave > 0L) setRequestProperty("Range", "bytes=$alreadyHave-")
            }
            connection.connect()

            val code = connection.responseCode
            val resumed = code == HttpURLConnection.HTTP_PARTIAL && alreadyHave > 0L
            if (code !in 200..299) {
                connection.disconnect()
                set(key, DownloadStatus.Failed("সার্ভার ত্রুটি ($code)"))
                return
            }

            val startBytes = if (resumed) alreadyHave else 0L
            val length = connection.contentLengthLong
            val total = if (length > 0L) length + startBytes else -1L
            set(key, DownloadStatus.InProgress(startBytes, total))

            connection.inputStream.use { input ->
                FileOutputStream(partFile, resumed).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = startBytes
                    var lastTick = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        // Throttle UI updates: a 25 MB file is ~400 chunks, and
                        // one state emission per chunk would rebuild the bar
                        // pointlessly often.
                        if (now - lastTick >= PROGRESS_INTERVAL_MS) {
                            lastTick = now
                            set(key, DownloadStatus.InProgress(written, total))
                        }
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            if (target.exists()) target.delete()
            if (!partFile.renameTo(target)) {
                partFile.copyTo(target, overwrite = true)
                partFile.delete()
            }
            set(key, DownloadStatus.Completed(target.length()))
        } catch (cancellation: CancellationException) {
            set(key, DownloadStatus.Cancelled)
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "Download failed for key=$key url=$url", throwable)
            set(key, DownloadStatus.Failed("ডাউনলোড ব্যর্থ হয়েছে"))
        } finally {
            jobs.remove(key)
        }
    }

    private fun set(key: String, status: DownloadStatus) {
        _states.update { it + (key to status) }
    }

    private companion object {
        const val TAG = "DownloadManager"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val PROGRESS_INTERVAL_MS = 200L
    }
}
