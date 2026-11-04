package com.hafeziquran.app.prayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hafeziquran.app.MainActivity
import com.hafeziquran.app.R
import com.hafeziquran.app.data.model.PrayerName

/**
 * Plays the adhan as a `mediaPlayback` foreground service, so it keeps running
 * with the screen locked or the app swiped away.
 *
 * The audio is whatever file the user picked for that prayer (persisted SAF
 * URI); if none is set the system alarm ringtone is used, so an alarm is never
 * silent.
 */
class AdhanPlaybackService : Service() {

    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }

        val prayer = intent?.getStringExtra(EXTRA_PRAYER)
            ?.let { name -> PrayerName.entries.firstOrNull { it.name == name } }
            ?: PrayerName.FAJR

        startForeground(NOTIFICATION_ID, buildNotification(prayer))
        play(prayer, intent?.getStringExtra(EXTRA_AUDIO_URI))
        return START_NOT_STICKY
    }

    private fun play(prayer: PrayerName, uriString: String?) {
        stopPlayback()
        val uri = uriString?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setWakeMode(this@AdhanPlaybackService, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(this@AdhanPlaybackService, uri)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stopSelf() }
                setOnErrorListener { _, _, _ ->
                    stopSelf()
                    true
                }
                prepareAsync()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Adhan audio could not be loaded for ${prayer.name}", t)
            stopSelf()
            null
        }
    }

    private fun stopPlayback() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }

    override fun onDestroy() {
        stopPlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(prayer: PrayerName): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, AdhanPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("${prayer.banglaName}র আজান")
            .setContentText("আজানের সময় হয়েছে")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, "বন্ধ করুন", stop)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "আজান",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "নামাজের সময়ের আজান"
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "AdhanPlaybackService"
        private const val CHANNEL_ID = "adhan_channel"
        private const val NOTIFICATION_ID = 42_001

        const val ACTION_STOP = "com.hafeziquran.app.action.STOP_ADHAN"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_AUDIO_URI = "audio_uri"

        fun start(context: Context, prayer: PrayerName, audioUri: String?) {
            val intent = Intent(context, AdhanPlaybackService::class.java).apply {
                putExtra(EXTRA_PRAYER, prayer.name)
                putExtra(EXTRA_AUDIO_URI, audioUri)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}