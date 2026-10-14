package com.hafeziquran.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.hafeziquran.app.data.pdf.PdfPageRenderer
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.Executors
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

    override fun onCreate() {
        super.onCreate()

        // Ad SDK init hits disk/network — keep it off the main thread so the
        // first frame is not delayed by it.
        Executors.newSingleThreadExecutor().execute {
            runCatching { MobileAds.initialize(this) }
        }
    }

    override fun onTerminate() {
        // Only called on emulators, but releasing the native document here is
        // still the correct place for it.
        pdfRenderer.close()
        super.onTerminate()
    }
}
