package com.hafeziquran.app.domain.prayer

import com.hafeziquran.app.data.model.HijriCalendarDay
import com.hafeziquran.app.data.model.PrayerDay
import com.hafeziquran.app.data.model.PrayerName

/**
 * Shifts the AlAdhan "waqt start" times to match the user's own mosque.
 *
 * AlAdhan reports when a prayer time *begins* (for example Dhuhr at solar noon,
 * which in Dhaka is around 11:45 in winter). Many mosques hold the jamaat later
 * than that, so the app lets the user add a fixed number of minutes per prayer.
 *
 * The shift is applied on top of whatever the repository returned, so raw values
 * are never mutated: the same cached [PrayerDay] can be tuned differently at any
 * time without a network round-trip. Times wrap past midnight naturally.
 */
object PrayerTuner {

    /** Fine-tune bounds and step, in minutes. */
    const val MIN_OFFSET = -30
    const val MAX_OFFSET = 120
    const val STEP = 5

    /** Default "waqt start": all offsets zero, the unmodified AlAdhan times. */
    val WAQT_START: Map<PrayerName, Int> = PrayerName.entries.associateWith { 0 }

    /** A common jamaat pattern in Bangladeshi mosques, as a starting point. */
    val JAMAAT_PRESET: Map<PrayerName, Int> = mapOf(
        PrayerName.FAJR to 15,
        PrayerName.DHUHR to 20,
        PrayerName.ASR to 20,
        PrayerName.MAGHRIB to 5,
        PrayerName.ISHA to 15,
    )

    fun isWaqtStart(offsets: Map<PrayerName, Int>): Boolean = offsets.values.all { it == 0 }

    /**
     * Returns [day] with each prayer time shifted. Imsak tracks the Fajr offset
     * (sehri ends just before Fajr) and iftar tracks Maghrib, so the Ramadan card
     * stays consistent with the tuned prayer list.
     */
    fun apply(day: PrayerDay, offsets: Map<PrayerName, Int>): PrayerDay {
        if (isWaqtStart(offsets)) return day
        val times = day.times.mapValues { (prayer, time) ->
            time.plusMinutes((offsets[prayer] ?: 0).toLong())
        }
        return day.copy(
            times = times,
            imsak = day.imsak?.plusMinutes((offsets[PrayerName.FAJR] ?: 0).toLong()),
            maghrib = day.maghrib?.plusMinutes((offsets[PrayerName.MAGHRIB] ?: 0).toLong()),
        )
    }

    /** Same shift for a calendar cell (only imsak/maghrib are carried). */
    fun apply(day: HijriCalendarDay, offsets: Map<PrayerName, Int>): HijriCalendarDay {
        if (isWaqtStart(offsets)) return day
        return day.copy(
            imsak = day.imsak?.plusMinutes((offsets[PrayerName.FAJR] ?: 0).toLong()),
            maghrib = day.maghrib?.plusMinutes((offsets[PrayerName.MAGHRIB] ?: 0).toLong()),
        )
    }
}
