package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.data.model.HijriCalendarDay
import com.hafeziquran.app.data.model.PrayerDay
import com.hafeziquran.app.data.model.PrayerLocation
import com.hafeziquran.app.data.remote.AlAdhanApi
import com.hafeziquran.app.data.remote.AlAdhanJson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache-first prayer-time access.
 *
 * Raw AlAdhan JSON is stored in a small SharedPreferences file keyed by date and
 * rounded coordinates. A day that has already been fetched therefore works
 * offline, and the last successful day is re-served with `stale = true` so the
 * screen never goes blank when the network is down.
 */
@Singleton
class PrayerTimesRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val api: AlAdhanApi,
) {
    private val cache = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)

    suspend fun getDay(
        date: LocalDate,
        location: PrayerLocation,
        forceRefresh: Boolean = false,
    ): PrayerDay? {
        val key = dayKey(date, location)
        if (!forceRefresh) {
            cached(key)?.let { raw -> AlAdhanJson.parseDay(raw)?.let { return it } }
        }

        val raw = api.fetchDayRaw(date, location.latitude, location.longitude)
        if (raw != null) {
            AlAdhanJson.parseDay(raw)?.let { day ->
                cache.edit().putString(key, raw).putString(LAST_DAY, raw).apply()
                return day
            }
        }

        // No exact-day data. Serve the newest known day, clearly marked stale.
        return cached(LAST_DAY)?.let { AlAdhanJson.parseDay(it)?.copy(stale = true) }
    }

    suspend fun getMonth(
        year: Int,
        month: Int,
        location: PrayerLocation,
        forceRefresh: Boolean = false,
    ): List<HijriCalendarDay> {
        val key = monthKey(year, month, location)
        if (!forceRefresh) {
            cached(key)?.let { raw ->
                AlAdhanJson.parseMonth(raw).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }

        val raw = api.fetchMonthRaw(year, month, location.latitude, location.longitude)
        if (raw != null) {
            val parsed = AlAdhanJson.parseMonth(raw)
            if (parsed.isNotEmpty()) {
                cache.edit().putString(key, raw).putString(lastMonthKey(location), raw).apply()
                return parsed
            }
        }

        return cached(lastMonthKey(location))?.let { AlAdhanJson.parseMonth(it) }.orEmpty()
    }

    private fun cached(key: String): String? = cache.getString(key, null)

    private fun dayKey(date: LocalDate, location: PrayerLocation): String =
        "day_${date}_${coord(location.latitude)}_${coord(location.longitude)}"

    private fun monthKey(year: Int, month: Int, location: PrayerLocation): String =
        "month_${year}_${month}_${coord(location.latitude)}_${coord(location.longitude)}"

    private fun lastMonthKey(location: PrayerLocation): String =
        "last_month_${coord(location.latitude)}_${coord(location.longitude)}"

    private fun coord(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private companion object {
        const val CACHE_NAME = "prayer_cache"
        const val LAST_DAY = "last_day"
    }
}