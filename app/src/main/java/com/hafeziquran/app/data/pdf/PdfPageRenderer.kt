package com.hafeziquran.app.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.hafeziquran.app.core.constants.AppConstants
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import com.shockwave.pdfium.util.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Renders scanned mushaf pages from the bundled PDF to bitmaps.
 *
 * ### Why a cached copy on disk?
 * Pdfium needs a real file descriptor, and `AssetManager` cannot hand one out.
 * On first launch we stream `assets/quran.pdf` into `filesDir` once; every launch
 * after that opens the cached file directly.
 *
 * ### Threading
 * [PdfiumCore] is *not* safe to render the same document from two threads. All
 * native calls are funnelled through [renderMutex]; callers still get their own
 * coroutine so the UI never blocks.
 *
 * ### Memory
 * A 1080-px-wide page costs ~6 MB as ARGB_8888. [pageCache] is byte-bounded and
 * evicts least-recently-used pages, so a long reading session stays flat.
 */
@Singleton
class PdfPageRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfiumCore: PdfiumCore,
) {
    private val renderMutex = Mutex()
    private val openMutex = Mutex()

    // Written under [openMutex], read under [renderMutex] — volatile so the
    // publish is visible across the two different locks.
    @Volatile
    private var fileDescriptor: ParcelFileDescriptor? = null

    @Volatile
    private var document: PdfDocument? = null

    /** Total pages reported by Pdfium; `0` until [open] completes. */
    @Volatile
    var pageCount: Int = 0
        private set

    /** Height ÷ width of the last opened document, for placeholder sizing. */
    @Volatile
    var pageAspectRatio: Float = 1.45f
        private set

    private val pageCache = object : LruCache<Int, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    private companion object {
        /**
         * Roughly an eighth of the heap, clamped. Deliberately conservative: the
         * pager keeps two neighbours alive on top of the cache, so a greedy cache
         * here is what pushes low-RAM phones into an OutOfMemoryError.
         */
        fun cacheBytes(): Int {
            val heap = Runtime.getRuntime().maxMemory()
            return (heap / 8).coerceIn(16L * 1024 * 1024, 48L * 1024 * 1024).toInt()
        }

        /** Never rasterise wider than this. At ~1080 px the scan is already
         *  sharper than any phone panel, and every extra px costs ~4 bytes. */
        const val MAX_RENDER_WIDTH = 1080
    }

    /**
     * Opens (or reuses) the document and returns the page count.
     * Safe to call repeatedly; the work happens once.
     */
    suspend fun open(): Int = openMutex.withLock {
        document?.let { return@withLock pageCount }

        withContext(Dispatchers.IO) {
            val file = ensureLocalCopy()
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val doc = pdfiumCore.newDocument(pfd)

            fileDescriptor = pfd
            document = doc
            pageCount = pdfiumCore.getPageCount(doc)

            if (pageCount > 0) {
                val size: Size = pdfiumCore.getPageSize(doc, 0)
                if (size.width > 0) {
                    pageAspectRatio = size.height.toFloat() / size.width.toFloat()
                }
            }
            pageCount
        }
    }

    /**
     * Returns the page bitmap, decoding it if not already cached.
     *
     * @param index zero-based page index.
     * @param targetWidth desired pixel width; clamped to [MAX_RENDER_WIDTH] and a
     *        64-px floor. Widths are bucketed so a pinch-zoom does not evict the
     *        cache on every frame.
     */
    suspend fun renderPage(index: Int, targetWidth: Int): Bitmap {
        val width = targetWidth.coerceIn(64, MAX_RENDER_WIDTH)
        pageCache.get(index)?.let { if (it.width >= width) return it }

        open()

        return renderMutex.withLock {
            // Another coroutine may have produced it while we waited.
            pageCache.get(index)?.let { if (it.width >= width) return@withLock it }

            withContext(Dispatchers.IO) {
                val doc = document ?: error("PDF not open")
                val size: Size = pdfiumCore.getPageSize(doc, index)
                val ratio = if (size.width > 0) {
                    size.height.toFloat() / size.width.toFloat()
                } else {
                    pageAspectRatio
                }
                val height = (width * ratio).roundToInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Scanned pages have white paper; clearing avoids grey halos where
                // Pdfium leaves the buffer untouched around the page edge.
                bitmap.eraseColor(Color.WHITE)
                pdfiumCore.renderPageBitmap(
                    doc, bitmap, index, 0, 0, width, height, true,
                )
                pageCache.put(index, bitmap)
                bitmap
            }
        }
    }

    /** Non-blocking lookup used to show an already-decoded page instantly. */
    fun cachedPage(index: Int): Bitmap? = pageCache.get(index)

    /** Pre-decodes neighbours so a page turn feels instant. */
    suspend fun prefetch(index: Int, targetWidth: Int) {
        if (index < 0 || (pageCount > 0 && index >= pageCount)) return
        runCatching { renderPage(index, targetWidth) }
    }

    fun evictPage(index: Int) = pageCache.remove(index)

    /** Streams the PDF out of `assets/` once. Re-copies if the APK shipped a
     *  different build (detected via size, which is cheap and good enough).
     *
     *  `openFd` only works for *uncompressed* assets. If aapt ever compresses the
     *  PDF it throws `FileNotFoundException: This file can not be opened as a file
     *  descriptor; it is probably compressed`, which used to abort the whole copy
     *  and left the reader with nothing to open. Treat the size check as a pure
     *  optimisation: when it cannot be read, just re-copy. */
    private fun ensureLocalCopy(): File {
        val target = File(context.filesDir, AppConstants.QURAN_PDF_ASSET)
        val expectedSize = runCatching {
            context.assets.openFd(AppConstants.QURAN_PDF_ASSET).use { it.length }
        }.getOrNull()

        if (target.exists() && target.length() > 0L &&
            (expectedSize == null || target.length() == expectedSize)
        ) {
            return target
        }

        context.assets.open(AppConstants.QURAN_PDF_ASSET).use { input ->
            target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        return target
    }

    /** Releases native resources. Called when the process is about to die. */
    fun close() {
        runCatching {
            document?.let { pdfiumCore.closeDocument(it) }
            fileDescriptor?.close()
        }
        document = null
        fileDescriptor = null
        pageCount = 0
        pageCache.evictAll()
    }
}
