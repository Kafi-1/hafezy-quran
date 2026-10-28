package com.hafeziquran.app.presentation.screens.banglaquran

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.hafeziquran.app.core.util.toBanglaDigits
import com.hafeziquran.app.presentation.screens.pdfviewer.PdfFileViewerScreen

/**
 * In-app viewer for the downloaded Bangla translation — the shared
 * [PdfFileViewerScreen] wired to [BanglaQuranViewerViewModel].
 */
@Composable
fun BanglaQuranViewerScreen(
    onBack: () -> Unit,
    viewModel: BanglaQuranViewerViewModel = hiltViewModel(),
) {
    PdfFileViewerScreen(
        viewer = viewModel,
        onBack = onBack,
        errorTitle = "বাংলা কোরআন খোলা যায়নি",
        title = { state ->
            if (state.isReady) {
                "পৃষ্ঠা ${state.currentPage.toBanglaDigits()} / ${state.pageCount.toBanglaDigits()}"
            } else {
                "বাংলা কোরআন"
            }
        },
    )
}
