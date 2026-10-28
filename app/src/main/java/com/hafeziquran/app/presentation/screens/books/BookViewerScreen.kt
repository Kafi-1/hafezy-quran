package com.hafeziquran.app.presentation.screens.books

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.hafeziquran.app.core.util.toBanglaDigits
import com.hafeziquran.app.presentation.screens.pdfviewer.PdfFileViewerScreen

/**
 * Full-screen viewer for a downloaded "ইসলামিক বুক" entry — the shared
 * [PdfFileViewerScreen] wired to [BookViewerViewModel].
 */
@Composable
fun BookViewerScreen(
    onBack: () -> Unit,
    viewModel: BookViewerViewModel = hiltViewModel(),
) {
    PdfFileViewerScreen(
        viewer = viewModel,
        onBack = onBack,
        errorTitle = "বই খোলা যায়নি",
        title = { state ->
            if (state.isReady) {
                "${state.documentTitle} — পৃষ্ঠা ${state.currentPage.toBanglaDigits()} / " +
                    state.pageCount.toBanglaDigits()
            } else {
                state.documentTitle.ifBlank { "বই" }
            }
        },
    )
}
