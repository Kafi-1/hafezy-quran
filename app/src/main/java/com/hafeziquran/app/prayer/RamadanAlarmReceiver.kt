package com.hafeziquran.app.prayer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hafeziquran.app.MainActivity
import com.hafeziquran.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** The two Ramadan reminders, each with its own notification slot and alarm code. */
enum class RamadanReminder(val banglaTitle: String, val notificationId: Int) {
    SEHRI("সেহরি শেষের সময়", 42_101),
    IFTAR("ইফতারের সময়", 42_102),
}

/**
 * Posts the sehri/iftar reminder and immediately re-arms tomorrow's alarm.
 *
 * Notifications are used rather than an audio service: a short high-priority
 * alert is enough for these two moments, and it keeps working without the app
 * process alive.
 */
@AndroidEntryPoint
class RamadanAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: AdhanScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val type = intent.getStringExtra(EXTRA_TYPE)
            ?.let { name -> RamadanReminder.entries.firstOrNull { it.name == name } }
            ?: return

        showNotification(context, type)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(RECEIVER_TIMEOUT_MS) { scheduler.rescheduleAll() }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, type: RamadanReminder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val body = when (type) {
            RamadanReminder.SEHRI -> "সেহরির সময় শেষ হচ্ছে — রোজার নিয়ত করে নিন।"
            RamadanReminder.IFTAR -> "ইফতারের সময় হয়েছে।"
        }
        val open = PendingIntent.getActivity(
            context,
            type.notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(type.banglaTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(type.notificationId, notification)
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "সেহরি ও ইফতার",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "সেহরি ও ইফতারের সময়ের রিমাইন্ডার"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_REMINDER = "com.hafeziquran.app.action.RAMADAN_REMINDER"
        const val EXTRA_TYPE = "reminder_type"

        private const val CHANNEL_ID = "ramadan_channel"
        private const val RECEIVER_TIMEOUT_MS = 8_000L
    }
}