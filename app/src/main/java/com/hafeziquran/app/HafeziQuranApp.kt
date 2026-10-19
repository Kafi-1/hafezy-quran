package com.hafeziquran.app

import android.app.Application
import com.hafeziquran.app.data.pdf.PdfPageRenderer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Hilt is bootstrapped here; everything else is deliberately lazy so cold start
 * stays cheap (the 13 MB PDF is not touched until the reader opens).
 */
@HiltAndroidApp
class HafeziQuranApp : Application() {

    @Inject
    lateinit var pdfRenderer: PdfPageRenderer

    override fun onTerminate() {
        // Only called on emulators, but releasing the native document here is
        // still the correct place for it.
        pdfRenderer.close()
        super.onTerminate()
    }
}
