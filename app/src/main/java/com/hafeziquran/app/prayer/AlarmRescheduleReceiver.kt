package com.hafeziquran.app.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hafeziquran.app.data.preferences.PrayerPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Re-arms adhan alarms after a reboot, a clock/timezone change, or an app update —
 * all events that discard or invalidate alarms registered by the old process.
 */
@AndroidEntryPoint
class AlarmRescheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferences: PrayerPreferences

    @Inject
    lateinit var scheduler: AdhanScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val location = withTimeoutOrNull(5_000) { preferences.location.first() }
                if (location != null) {
                    withTimeoutOrNull(20_000) { scheduler.rescheduleAll() }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}