package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.data.asr.AsrModelFiles
import com.hafeziquran.app.data.asr.AsrModelValidator
import com.hafeziquran.app.data.asr.FastConformerEngine
import com.hafeziquran.app.data.download.DownloadManager
import com.hafeziquran.app.data.download.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the optional FastConformer ASR files for Sobok: the ONNX graph and its
 * SentencePiece vocabulary, their download, validation, and deletion (which also
 * frees the loaded ONNX session).
 *
 * Nothing is bundled in the APK — the ~84 MB graph is fetched only when the user
 * first opens the feature and can be deleted again from Settings. The two files
 * download in parallel; the reported progress is their sum.
 */
@Singleton
class SobokModelRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val downloadManager: DownloadManager,
    private val engine: FastConformerEngine,
) {
    private val modelsDir: File = File(context.filesDir, AppConstants.SOBOK_MODEL_DIR)
    val modelFile: File = File(modelsDir, AppConstants.SOBOK_MODEL_FILE)
    val vocabFile: File = File(modelsDir, AppConstants.SOBOK_VOCAB_FILE)

    /** Models superseded by the current one (the old whisper.cpp ggml files). */
    private val legacyFiles: List<File> =
        AppConstants.SOBOK_LEGACY_MODEL_FILES.map { File(modelsDir, it) }

    /**
     * Header checks read a few bytes, but `isDownloaded()` runs on every download
     * progress tick and `files()` runs for every segment. Cache the verdict keyed
     * by length + mtime so an unchanged file is only inspected once.
     */
    private val validationLock = Any()
    private var modelStamp: FileStamp? = null
    private var modelStampValid = false
    private var vocabStamp: FileStamp? = null
    private var vocabStampValid = false

    init {
        modelsDir.mkdirs()
        // Files left from a previous session count as downloaded until the
        // structural check below rejects them.
        downloadManager.markCompletedIfPresent(KEY_MODEL, modelFile)
        downloadManager.markCompletedIfPresent(KEY_VOCAB, vocabFile)
        // If the current files are already in place, reclaim the old ones' disk.
        cleanupLegacyModel()
    }

    val downloadState: Flow<DownloadStatus> =
        combine(
            downloadManager.stateFlow(KEY_MODEL),
            downloadManager.stateFlow(KEY_VOCAB),
        ) { model, vocab -> merge(model, vocab) }

    fun startDownload() {
        modelsDir.mkdirs()
        downloadManager.start(KEY_MODEL, AppConstants.SOBOK_MODEL_URL, modelFile)
        downloadManager.start(KEY_VOCAB, AppConstants.SOBOK_VOCAB_URL, vocabFile)
    }

    fun cancelDownload() {
        downloadManager.cancel(KEY_MODEL)
        downloadManager.cancel(KEY_VOCAB)
    }

    /**
     * True only when both files are present, large enough, and structurally look
     * like the files we expect. A truncated graph must never be advertised as
     * usable: ONNX Runtime would abort on it in native code.
     */
    fun isDownloaded(): Boolean = isModelValid() && isVocabValid()

    fun sizeBytes(): Long =
        (if (modelFile.exists()) modelFile.length() else 0L) +
            (if (vocabFile.exists()) vocabFile.length() else 0L)

    /** Paths of both files, or null until the download is complete and valid. */
    fun files(): AsrModelFiles? = if (isDownloaded()) {
        AsrModelFiles(modelFile.absolutePath, vocabFile.absolutePath)
    } else {
        null
    }

    /** Deletes a completed-but-invalid download and returns the state to Idle. */
    fun discardInvalid() {
        removeFiles()
        downloadManager.reset(KEY_MODEL)
        downloadManager.reset(KEY_VOCAB)
    }

    /**
     * Reclaims the disk used by superseded models — but only once the current
     * files are present and valid, so a half-finished migration never leaves the
     * user with no usable model. Safe to call repeatedly; idempotent.
     */
    fun cleanupLegacyModel() {
        if (!isDownloaded()) return
        legacyFiles.forEach { runCatching { it.delete() } }
    }

    /** Frees the ONNX session first, then removes the files and partial downloads. */
    suspend fun delete() = withContext(Dispatchers.IO) {
        engine.release()
        removeFiles()
        downloadManager.reset(KEY_MODEL)
        downloadManager.reset(KEY_VOCAB)
    }

    private fun isModelValid(): Boolean {
        val file = modelFile
        val stamp = stampOf(file) ?: return false
        synchronized(validationLock) {
            if (stamp == modelStamp) return modelStampValid
            val valid = AsrModelValidator.isValidModel(file, AppConstants.SOBOK_MODEL_MIN_BYTES)
            modelStamp = stamp
            modelStampValid = valid
            return valid
        }
    }

    private fun isVocabValid(): Boolean {
        val file = vocabFile
        val stamp = stampOf(file) ?: return false
        synchronized(validationLock) {
            if (stamp == vocabStamp) return vocabStampValid
            val valid = AsrModelValidator.isValidVocab(file, AppConstants.SOBOK_VOCAB_MIN_BYTES)
            vocabStamp = stamp
            vocabStampValid = valid
            return valid
        }
    }

    private fun stampOf(file: File): FileStamp? =
        if (file.isFile) FileStamp(file.length(), file.lastModified()) else null

    private fun removeFiles() {
        runCatching { modelFile.delete() }
        runCatching { vocabFile.delete() }
        runCatching { partFile(modelFile).delete() }
        runCatching { partFile(vocabFile).delete() }
        legacyFiles.forEach { runCatching { it.delete() } }
    }

    private fun partFile(target: File): File = File(target.parentFile, target.name + ".part")

    /** Sums the two per-file states into one progress figure for the UI. */
    private fun merge(model: DownloadStatus, vocab: DownloadStatus): DownloadStatus {
        (model as? DownloadStatus.Failed)?.let { return it }
        (vocab as? DownloadStatus.Failed)?.let { return it }
        if (model is DownloadStatus.Cancelled || vocab is DownloadStatus.Cancelled) {
            return DownloadStatus.Cancelled
        }
        if (model is DownloadStatus.Completed && vocab is DownloadStatus.Completed) {
            return DownloadStatus.Completed(model.bytes + vocab.bytes)
        }
        val bytes = bytesOf(model) + bytesOf(vocab)
        val total = totalOf(model) + totalOf(vocab)
        return when {
            model is DownloadStatus.InProgress || vocab is DownloadStatus.InProgress ->
                DownloadStatus.InProgress(bytes, total)
            model is DownloadStatus.Paused || vocab is DownloadStatus.Paused ->
                DownloadStatus.Paused(bytes, total)
            else -> DownloadStatus.Idle
        }
    }

    private fun bytesOf(status: DownloadStatus): Long = when (status) {
        is DownloadStatus.InProgress -> status.bytes
        is DownloadStatus.Paused -> status.bytes
        is DownloadStatus.Completed -> status.bytes
        else -> 0L
    }

    private fun totalOf(status: DownloadStatus): Long = when (status) {
        is DownloadStatus.InProgress -> status.total
        is DownloadStatus.Paused -> status.total
        is DownloadStatus.Completed -> status.bytes
        else -> 0L
    }

    private data class FileStamp(val length: Long, val modified: Long)

    private companion object {
        const val KEY_MODEL = "sobok_model"
        const val KEY_VOCAB = "sobok_vocab"
    }
}