package com.hafeziquran.app.data.download

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    /** A recoverable stop: the `.part` file stays on disk and [DownloadManager.start] resumes it. */
    data class Paused(val bytes: Long, val total: Long) : DownloadStatus {
        val fraction: Float
            get() = if (total > 0L) (bytes.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    }

    data class Completed(val bytes: Long) : DownloadStatus

    data class Failed(val message: String) : DownloadStatus

    /** User-cancelled: the partial file has been removed. */
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
 *
 * Pause vs cancel:
 *  - [pause] keeps the `.part` file, so [start] resumes with an HTTP Range request.
 *  - [cancel] deletes the `.part` file and resets progress.
 *
 * Cancellation is *cooperative*: `input.read` blocks on the socket, so the active
 * connection is disconnected first to unblock it, and the loop calls
 * [ensureActive] after every chunk. Without that, a cancel could sit for up to the
 * 30 s read timeout while the old job kept writing.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val states: StateFlow<Map<String, DownloadStatus>> = _states.asStateFlow()

    private val lock = Any()
    private val jobs = mutableMapOf<String, Job>()
    /** Detached jobs that are still unwinding after pause/cancel; start joins them. */
    private val orphanJobs = mutableMapOf<String, Job>()
    private val connections = mutableMapOf<String, HttpURLConnection>()
    private val targets = mutableMapOf<String, File>()
    private val totals = mutableMapOf<String, Long>()

    fun stateFlow(key: String): Flow<DownloadStatus> =
        states.map { it[key] ?: DownloadStatus.Idle }

    /** Seeds the state from disk for a file that may already be downloaded. */
    fun markCompletedIfPresent(key: String, file: File) {
        if (file.exists() && file.length() > 0L) {
            set(key, DownloadStatus.Completed(file.length()))
        }
    }

    fun start(key: String, url: String, target: File) {
        synchronized(lock) {
            if (jobs[key]?.isActive == true) return
            val previous = jobs[key] ?: orphanJobs[key]
            // Cancel means "throw the bytes away"; pause/failure means "resume".
            val restartFromScratch = _states.value[key] is DownloadStatus.Cancelled
            targets[key] = target
            val job = scope.launch(start = CoroutineStart.LAZY) {
                // Serialise against a paused/cancelled job still unwinding:
                // two writers on the same .part would corrupt the resume.
                previous?.join()
                synchronized(lock) {
                    if (orphanJobs[key] === previous) orphanJobs.remove(key)
                }
                if (restartFromScratch) {
                    File(target.parentFile, target.name + ".part").delete()
                }
                runDownload(key, url, target)
            }
            jobs[key] = job
            job.start()
        }
    }

    /** Stops [key] but keeps its partial bytes for a later resume. */
    fun pause(key: String) {
        val job = synchronized(lock) {
            val active = jobs.remove(key) ?: return
            if (!active.isCompleted) orphanJobs[key] = active
            active
        }
        disconnect(key)
        job.cancel()

        val part = partFile(key)
        val bytes = part?.takeIf { it.exists() }?.length() ?: 0L
        val total = synchronized(lock) { totals[key] ?: -1L }
        set(key, DownloadStatus.Paused(bytes, total))
    }

    /** Stops [key] and removes whatever it had downloaded. */
    fun cancel(key: String) {
        val job = synchronized(lock) {
            jobs.remove(key)?.also { if (!it.isCompleted) orphanJobs[key] = it }
        }
        disconnect(key)
        job?.cancel()
        partFile(key)?.delete()
        set(key, DownloadStatus.Cancelled)
        // A read already in flight can still open/write the .part after the
        // delete above; remove it once the job has fully unwound.
        scope.launch {
            job?.join()
            if (synchronized(lock) { jobs[key] == null }) partFile(key)?.delete()
        }
    }

    /** Cancels any in-flight job and forgets the key entirely (back to Idle). */
    fun reset(key: String) {
        val job = synchronized(lock) {
            jobs.remove(key)?.also { if (!it.isCompleted) orphanJobs[key] = it }
        }
        disconnect(key)
        job?.cancel()
        partFile(key)?.delete()
        set(key, DownloadStatus.Idle)
    }

    private fun disconnect(key: String) {
        synchronized(lock) { connections.remove(key) }?.disconnect()
    }

    private fun partFile(key: String): File? {
        val target = synchronized(lock) { targets[key] } ?: return null
        return File(target.parentFile, target.name + ".part")
    }

    private suspend fun runDownload(key: String, url: String, target: File) {
        val self = currentCoroutineContext()[Job]
        val partFile = File(target.parentFile, target.name + ".part")
        try {
            currentCoroutineContext().ensureActive()
            val alreadyHave = if (partFile.exists()) partFile.length() else 0L

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                if (alreadyHave > 0L) setRequestProperty("Range", "bytes=$alreadyHave-")
            }
            synchronized(lock) { connections[key] = connection }
            connection.connect()
            currentCoroutineContext().ensureActive()

            val code = connection.responseCode
            val resumed = code == HttpURLConnection.HTTP_PARTIAL && alreadyHave > 0L
            if (code !in 200..299) {
                connection.disconnect()
                setIfCurrent(key, self, DownloadStatus.Failed("সার্ভার ত্রুটি ($code)"))
                return
            }

            val startBytes = if (resumed) alreadyHave else 0L
            val length = connection.contentLengthLong
            val total = if (length > 0L) length + startBytes else -1L
            synchronized(lock) { totals[key] = total }
            setIfCurrent(key, self, DownloadStatus.InProgress(startBytes, total))

            // A server that ignored our Range header starts from zero, so the
            // old bytes must be discarded rather than appended.
            connection.inputStream.use { input ->
                FileOutputStream(partFile, resumed).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = startBytes
                    var lastTick = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        // Throttle UI updates: a 25 MB file is ~400 chunks, and
                        // one state emission per chunk would rebuild the bar
                        // pointlessly often.
                        if (now - lastTick >= PROGRESS_INTERVAL_MS) {
                            lastTick = now
                            setIfCurrent(key, self, DownloadStatus.InProgress(written, total))
                        }
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            currentCoroutineContext().ensureActive()
            if (target.exists()) target.delete()
            if (!partFile.renameTo(target)) {
                partFile.copyTo(target, overwrite = true)
                partFile.delete()
            }
            setIfCurrent(key, self, DownloadStatus.Completed(target.length()))
        } catch (cancellation: CancellationException) {
            // pause()/cancel()/reset() already published their own terminal
            // state; touching it here would race a freshly started retry.
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "Download failed for key=$key url=$url", throwable)
            setIfCurrent(key, self, DownloadStatus.Failed("ডাউনলোড ব্যর্থ হয়েছে"))
        } finally {
            synchronized(lock) {
                if (jobs[key] === self) jobs.remove(key)
                if (orphanJobs[key] === self) orphanJobs.remove(key)
                connections.remove(key)
            }
        }
    }

    /**
     * Only the job that owns [key] may publish a non-terminal state. A cancelled
     * job whose exception arrives after a resume must not clobber the new state.
     */
    private fun setIfCurrent(key: String, job: Job?, status: DownloadStatus) {
        val current = synchronized(lock) { jobs[key] }
        if (current === job) set(key, status)
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
