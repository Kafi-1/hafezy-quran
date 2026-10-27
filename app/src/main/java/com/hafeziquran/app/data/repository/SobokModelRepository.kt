package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.data.download.DownloadManager
import com.hafeziquran.app.data.download.DownloadStatus
import com.hafeziquran.app.data.whisper.WhisperEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the optional Whisper model for Sobok: its private-storage file, download,
 * validation, and deletion (which also frees the loaded native context).
 *
 * Nothing is bundled in the APK — the ~75 MB `ggml-tiny.bin` file is fetched only
 * when the user first opens the feature and can be deleted again from Settings.
 */
@Singleton
class SobokModelRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val downloadManager: DownloadManager,
    private val whisperEngine: WhisperEngine,
) {
    private val modelsDir: File = File(context.filesDir, AppConstants.SOBOK_MODEL_DIR)
    val file: File = File(modelsDir, AppConstants.SOBOK_MODEL_FILE)

    /** Previous base model (`ggml-model-q8_0.bin`), superseded by the tiny model. */
    private val legacyFile: File = File(modelsDir, AppConstants.SOBOK_LEGACY_MODEL_FILE)

    init {
        modelsDir.mkdirs()
        // A valid file left from a previous session counts as downloaded.
        downloadManager.markCompletedIfPresent(KEY, file)
        // If the tiny model is already in place, reclaim the old base model's disk.
        cleanupLegacyModel()
    }

    val downloadState: Flow<DownloadStatus> = downloadManager.stateFlow(KEY)

    fun startDownload() {
        modelsDir.mkdirs()
        downloadManager.start(KEY, AppConstants.SOBOK_MODEL_URL, file)
    }

    fun cancelDownload() = downloadManager.cancel(KEY)

    /**
     * True only for a complete-looking model: both the size floor and the GGML
     * magic must pass, so an HTML error page or a truncated partial download can
     * never be handed to whisper.cpp.
     */
    fun isDownloaded(): Boolean =
        file.exists() &&
            file.length() >= AppConstants.SOBOK_MODEL_MIN_BYTES &&
            hasGgmlMagic()

    fun sizeBytes(): Long = if (file.exists()) file.length() else 0L

    fun modelPath(): String? = if (isDownloaded()) file.absolutePath else null

    /** Deletes a completed-but-invalid file and returns the state to Idle. */
    fun discardInvalid() {
        runCatching { file.delete() }
        runCatching { partFile().delete() }
        downloadManager.reset(KEY)
    }

    /**
     * Reclaims the disk used by the superseded base model — but only once the tiny
     * model is present and valid, so a half-finished migration never leaves the
     * user with no usable model. Safe to call repeatedly; idempotent.
     */
    fun cleanupLegacyModel() {
        if (!isDownloaded()) return
        if (!legacyFile.exists()) return
        runCatching { legacyFile.delete() }
    }

    /** Frees the native context first, then removes the model and partial file. */
    suspend fun delete() = withContext(Dispatchers.IO) {
        whisperEngine.release()
        runCatching { file.delete() }
        runCatching { partFile().delete() }
        runCatching { legacyFile.delete() }
        downloadManager.reset(KEY)
    }

    private fun partFile(): File = File(file.parentFile, file.name + ".part")

    private fun hasGgmlMagic(): Boolean = runCatching {
        file.inputStream().use { input ->
            val magic = ByteArray(4)
            input.read(magic) == 4 && magic.contentEquals(GGML_MAGIC)
        }
    }.getOrDefault(false)

    private companion object {
        const val KEY = "sobok_whisper_model"

        /** Little-endian "ggml" as stored at the start of the legacy model file. */
        val GGML_MAGIC = byteArrayOf(0x6c, 0x6d, 0x67, 0x67)
    }
}
