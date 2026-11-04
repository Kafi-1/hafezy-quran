package com.hafeziquran.app

import android.app.Application
import com.hafeziquran.app.data.pdf.PdfPageRenderer
import com.hafeziquran.app.data.preferences.PrayerPreferences
import com.hafeziquran.app.prayer.AdhanScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

    @Inject
    lateinit var adhanScheduler: AdhanScheduler

    @Inject
    lateinit var prayerPreferences: PrayerPreferences

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Alarms can be wiped while the process is dead (reboot, force-stop), so
        // every cold start re-arms them. Cache-first, so this is cheap.
        appScope.launch {
            val location = withTimeoutOrNull(3_000) { prayerPreferences.location.first() }
            if (location != null) {
                withTimeoutOrNull(20_000) { adhanScheduler.rescheduleAll() }
            }
        }
    }

    override fun onTerminate() {
        // Only called on emulators, but releasing the native document here is
        // still the correct place for it.
        pdfRenderer.close()
        super.onTerminate()
    }
}
