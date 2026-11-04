package com.hafeziquran.app.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hafeziquran.app.data.model.PrayerName
import com.hafeziquran.app.data.preferences.PrayerPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Receives a prayer's exact alarm, starts the adhan foreground service, and
 * immediately schedules the prayer's next occurrence.
 *
 * [goAsync] keeps the process alive for the short coroutine that reads settings
 * and re-arms alarms, since a plain [onReceive] is killed as soon as it returns.
 */
@AndroidEntryPoint
class AdhanReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferences: PrayerPreferences

    @Inject
    lateinit var scheduler: AdhanScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADHAN) return
        val prayer = intent.getStringExtra(EXTRA_PRAYER)
            ?.let { name -> PrayerName.entries.firstOrNull { it.name == name } }
            ?: return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = withTimeoutOrNull(RECEIVER_TIMEOUT_MS) { preferences.snapshot() }
                if (snapshot?.adhanEnabled?.get(prayer) == true) {
                    // The system alarm gives the receiver a short allowlist
                    // window in which starting a foreground service is legal.
                    runCatching {
                        AdhanPlaybackService.start(context, prayer, snapshot.audioUris[prayer])
                    }.onFailure {
                        Log.w(TAG, "Could not start adhan playback for $prayer", it)
                    }
                }
                withTimeoutOrNull(RECEIVER_TIMEOUT_MS) { scheduler.rescheduleAll() }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADHAN = "com.hafeziquran.app.action.ADHAN"
        const val EXTRA_PRAYER = "prayer"

        private const val TAG = "AdhanReceiver"
        private const val RECEIVER_TIMEOUT_MS = 8_000L
    }
}