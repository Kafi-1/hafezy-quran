package com.hafeziquran.app.presentation.screens.pdfviewer

import android.graphics.Bitmap
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.pdf.TocEntry
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything a [PdfFileViewerScreen] needs to render a PDF-backed document.
 *
 * [documentTitle] is the book's own name and is blank for the Bangla mushaf,
 * whose title is fixed by its wrapper.
 */
data class PdfFileViewerState(
    val isLoading: Boolean = true,
    val pageCount: Int = 0,
    val currentPage: Int = 1,
    val isBookmarked: Boolean = false,
    val nightMode: Boolean = false,
    val pageAspectRatio: Float = 1.45f,
    /** Flattened PDF outline; empty when the file carries no bookmarks. */
    val toc: List<TocEntry> = emptyList(),
    val errorMessage: String? = null,
    /** One-shot scroll request; cleared once the pager has moved. */
    val jumpRequest: Int? = null,
    val documentTitle: String = "",
) {
    val isReady: Boolean get() = !isLoading && errorMessage == null && pageCount > 0
}

/**
 * Contract shared by the two on-device PDF viewers — the downloaded Bangla
 * translation and the downloadable books. [PdfFileViewerScreen] drives the UI
 * purely through this, so both documents reuse one pager/zoom/night-mode/TOC
 * implementation and only their ViewModels and titles differ.
 */
interface PdfFileViewer {
    val uiState: StateFlow<PdfFileViewerState>

    suspend fun renderPage(pageIndex: Int, widthPx: Int): Resource<Bitmap>

    fun prefetchNeighbours(page: Int, widthPx: Int)

    fun onPageSettled(page: Int)

    fun toggleBookmark()

    fun setNightMode(enabled: Boolean)

    fun requestJump(page: Int)

    fun consumeJumpRequest()

    fun retry()
}
