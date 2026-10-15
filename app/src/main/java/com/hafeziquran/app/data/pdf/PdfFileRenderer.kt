package com.hafeziquran.app.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** One entry of a PDF outline, flattened to a title + 1-based page. */
data class TocEntry(val title: String, val page: Int)

/**
 * Renders pages from an arbitrary PDF *file* — here, the downloaded Bangla
 * translation — as opposed to the bundled asset that [PdfPageRenderer] owns.
 *
 * A separate class on purpose. The reader keeps one long-lived document open on
 * the shipped mushaf; a downloaded book has its own lifecycle (it can be deleted
 * and re-fetched at any time), so it gets its own document handle, page cache and
 * render mutex. Sharing the singleton would mean tearing the reader's document
 * down whenever a book is deleted, coupling two unrelated screens.
 *
 * Not a singleton: one instance per open file, created and closed by its screen.
 */
class PdfFileRenderer(
    private val file: File,
    private val pdfiumCore: PdfiumCore,
) {
    private val renderMutex = Mutex()
    private val openMutex = Mutex()

    @Volatile private var fileDescriptor: ParcelFileDescriptor? = null
    @Volatile private var document: PdfDocument? = null

    @Volatile var pageCount: Int = 0
        private set

    @Volatile var pageAspectRatio: Float = 1.45f
        private set

    private val pageCache = object : LruCache<Int, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    /** Opens the document once and returns the page count. Safe to call again. */
    suspend fun open(): Int = openMutex.withLock {
        document?.let { if (pageCount > 0) return@withLock pageCount }

        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() == 0L) {
                throw IllegalStateException("Downloaded PDF missing: ${file.absolutePath}")
            }
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
            Log.d(TAG, "Opened ${file.name}: $pageCount pages")
            pageCount
        }
    }

    suspend fun renderPage(index: Int, targetWidth: Int): Bitmap {
        val width = targetWidth.coerceIn(64, MAX_RENDER_WIDTH)
        pageCache.get(index)?.let { if (it.width >= width) return it }

        open()

        // Reject a stray index before any native call; Pdfium aborts the process
        // on an invalid page, so it must become a catchable exception here.
        if (index < 0 || pageCount <= 0 || index >= pageCount) {
            throw IllegalArgumentException("Page index $index out of range (0..${pageCount - 1})")
        }

        return renderMutex.withLock {
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
                bitmap.eraseColor(Color.WHITE)
                pdfiumCore.renderPageBitmap(
                    doc, bitmap, index, 0, 0, width, height,
                    /* renderAnnot = */ true, /* textMask = */ false,
                )
                pageCache.put(index, bitmap)
                bitmap
            }
        }
    }

    suspend fun prefetch(index: Int, targetWidth: Int) {
        if (index < 0 || (pageCount > 0 && index >= pageCount)) return
        runCatching { renderPage(index, targetWidth) }
    }

    /**
     * Flattens the document outline into a title → page list, or `[]` when the
     * PDF carries no bookmarks (which is not an error — the viewer then simply
     * hides the surah jump).
     */
    suspend fun tableOfContents(): List<TocEntry> = withContext(Dispatchers.IO) {
        runCatching {
            open()
            val doc = document ?: return@runCatching emptyList()
            val flat = mutableListOf<TocEntry>()
            doc.getTableOfContents().forEach { flatten(it, flat) }
            flat
        }.getOrElse { throwable ->
            Log.w(TAG, "No table of contents for ${file.name}: ${throwable.message}")
            emptyList()
        }
    }

    private fun flatten(bookmark: PdfDocument.Bookmark, into: MutableList<TocEntry>) {
        val label = bookmark.title?.trim().orEmpty()
        if (label.isNotEmpty()) {
            into += TocEntry(title = label, page = (bookmark.pageIdx + 1L).toInt())
        }
        bookmark.children.forEach { flatten(it, into) }
    }

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

    private companion object {
        const val TAG = "PdfFileRenderer"
        const val MAX_RENDER_WIDTH = 1080

        fun cacheBytes(): Int {
            val heap = Runtime.getRuntime().maxMemory()
            return (heap / 8).coerceIn(16L * 1024 * 1024, 48L * 1024 * 1024).toInt()
        }
    }
}
