package com.hafeziquran.app.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hafeziquran.app.data.model.PrayerDay
import com.hafeziquran.app.data.model.PrayerName
import com.hafeziquran.app.data.preferences.PrayerPreferences
import com.hafeziquran.app.data.preferences.PrayerSettingsSnapshot
import com.hafeziquran.app.domain.prayer.PrayerTuner
import com.hafeziquran.app.domain.repository.PrayerTimesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers one exact [AlarmManager] alarm per enabled prayer for that prayer's
 * next occurrence — today if it has not passed, otherwise tomorrow.
 *
 * Every call is idempotent: the PendingIntent for a prayer is reused (same
 * request code, `FLAG_UPDATE_CURRENT`), so re-running cancels/replaces the old
 * alarm instead of stacking duplicates. The alarms live in the system, so they
 * still fire after the app process is killed; [AlarmRescheduleReceiver] and
 * [AdhanReceiver] rebuild them after boot, timezone changes and each fire.
 */
@Singleton
class AdhanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PrayerPreferences,
    private val repository: PrayerTimesRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Whether alarms can be delivered to the minute on Android 12+. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun rescheduleAll() {
        val snapshot = preferences.snapshot()
        val location = snapshot.location
        if (location == null) {
            PrayerName.entries.forEach(::cancel)
            RamadanReminder.entries.forEach(::cancelReminder)
            return
        }

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val tune = snapshot.tuneMinutes

        // Pull both days up-front so the per-prayer loop makes no further IO.
        // Alarms fire at the tuned (jamaat) time, not the raw waqt start.
        val todayDay = repository.getDay(today, location)?.validFor(today)
            ?.let { PrayerTuner.apply(it, tune) }
        val tomorrowDay = repository.getDay(today.plusDays(1), location)
            ?.validFor(today.plusDays(1))
            ?.let { PrayerTuner.apply(it, tune) }

        PrayerName.entries.forEach { prayer ->
            if (snapshot.adhanEnabled[prayer] == false) {
                cancel(prayer)
                return@forEach
            }
            val at = nextOccurrence(prayer, now, todayDay, tomorrowDay)
            // No times available (offline + nothing cached): leave whatever alarm
            // is already registered rather than dropping it.
            if (at != null) schedule(prayer, at)
        }

        scheduleRamadanReminders(snapshot, now, todayDay, tomorrowDay)
    }

    /** Cancel a single prayer's pending alarm (used when its switch goes off). */
    fun cancel(prayer: PrayerName) {
        val intent = pendingIntent(prayer, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(intent)
    }

    private fun schedule(prayer: PrayerName, at: LocalDateTime) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = pendingIntent(prayer, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            // No exact-alarm grant: deliver as close to the minute as allowed
            // rather than not at all. The UI offers to grant the permission.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    private fun scheduleRamadanReminders(
        snapshot: PrayerSettingsSnapshot,
        now: LocalDateTime,
        today: PrayerDay?,
        tomorrow: PrayerDay?,
    ) {
        // Sehri ends at Imsak; iftar is Maghrib — both come straight from AlAdhan.
        scheduleReminder(RamadanReminder.SEHRI, snapshot.sehriAlarmEnabled, now, today, tomorrow) {
            it.imsak
        }
        scheduleReminder(RamadanReminder.IFTAR, snapshot.iftarAlarmEnabled, now, today, tomorrow) {
            it.maghrib
        }
    }

    private fun scheduleReminder(
        type: RamadanReminder,
        enabled: Boolean,
        now: LocalDateTime,
        today: PrayerDay?,
        tomorrow: PrayerDay?,
        timeOf: (PrayerDay) -> LocalTime?,
    ) {
        if (!enabled) {
            cancelReminder(type)
            return
        }
        val at = nextTime(now, today, tomorrow, timeOf) ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = reminderIntent(type, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    private fun cancelReminder(type: RamadanReminder) {
        val intent = reminderIntent(type, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(intent)
    }

    private fun nextTime(
        now: LocalDateTime,
        today: PrayerDay?,
        tomorrow: PrayerDay?,
        timeOf: (PrayerDay) -> LocalTime?,
    ): LocalDateTime? {
        today?.let { day ->
            timeOf(day)?.let { time ->
                val candidate = LocalDateTime.of(day.date, time)
                if (candidate.isAfter(now.plusSeconds(30))) return candidate
            }
        }
        tomorrow?.let { day ->
            timeOf(day)?.let { return LocalDateTime.of(day.date, it) }
        }
        return null
    }

    private fun nextOccurrence(
        prayer: PrayerName,
        now: LocalDateTime,
        today: PrayerDay?,
        tomorrow: PrayerDay?,
    ): LocalDateTime? {
        today?.times?.get(prayer)?.let { time ->
            val candidate = LocalDateTime.of(today.date, time)
            if (candidate.isAfter(now.plusSeconds(30))) return candidate
        }
        tomorrow?.times?.get(prayer)?.let { time ->
            return LocalDateTime.of(tomorrow.date, time)
        }
        return null
    }

    private fun PrayerDay.validFor(date: LocalDate): PrayerDay? =
        takeIf { !it.stale && it.date == date }

    private fun pendingIntent(prayer: PrayerName, flags: Int): PendingIntent? {
        val intent = Intent(context, AdhanReceiver::class.java).apply {
            action = AdhanReceiver.ACTION_ADHAN
            putExtra(AdhanReceiver.EXTRA_PRAYER, prayer.name)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + prayer.ordinal,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun reminderIntent(type: RamadanReminder, flags: Int): PendingIntent? {
        val intent = Intent(context, RamadanAlarmReceiver::class.java).apply {
            action = RamadanAlarmReceiver.ACTION_REMINDER
            putExtra(RamadanAlarmReceiver.EXTRA_TYPE, type.name)
        }
        return PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_BASE + type.ordinal,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE_BASE = 1_100
        const val REMINDER_REQUEST_BASE = 1_200
    }
}