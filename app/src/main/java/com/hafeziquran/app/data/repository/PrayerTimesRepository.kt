package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.data.model.HijriCalendarDay
import com.hafeziquran.app.data.model.PrayerDay
import com.hafeziquran.app.data.model.PrayerLocation
import com.hafeziquran.app.data.remote.AlAdhanApi
import com.hafeziquran.app.data.remote.AlAdhanCalendarResponse
import com.hafeziquran.app.data.remote.AlAdhanDayResponse
import com.hafeziquran.app.data.remote.toHijriCalendarDay
import com.hafeziquran.app.data.remote.toPrayerDay
import com.hafeziquran.app.domain.repository.PrayerTimesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache-first prayer-time access, implementing the domain contract.
 *
 * The Retrofit DTOs are JSON-encoded into a small SharedPreferences file keyed by
 * date and rounded coordinates. A day that has already been fetched therefore
 * works offline, and the last successful day is re-served with `stale = true` so
 * the screen never goes blank when the network is down.
 */
@Singleton
class PrayerTimesRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val api: AlAdhanApi,
    private val json: Json,
) : PrayerTimesRepository {
    private val cache = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)

    override suspend fun getDay(
        date: LocalDate,
        location: PrayerLocation,
        forceRefresh: Boolean,
    ): PrayerDay? {
        val key = dayKey(date, location)
        if (!forceRefresh) {
            cached(key)?.let { raw ->
                decodeDay(raw)?.data?.toPrayerDay()?.let { return it }
            }
        }

        api.fetchDay(date, location.latitude, location.longitude)?.let { response ->
            response.data?.toPrayerDay()?.let { day ->
                val encoded = json.encodeToString(
                    AlAdhanDayResponse.serializer(),
                    response,
                )
                cache.edit().putString(key, encoded).putString(LAST_DAY, encoded).apply()
                return day
            }
        }

        // No exact-day data. Serve the newest known day, clearly marked stale.
        return cached(LAST_DAY)?.let { decodeDay(it)?.data?.toPrayerDay()?.copy(stale = true) }
    }

    override suspend fun getMonth(
        year: Int,
        month: Int,
        location: PrayerLocation,
        forceRefresh: Boolean,
    ): List<HijriCalendarDay> {
        val key = monthKey(year, month, location)
        if (!forceRefresh) {
            cached(key)?.let { raw ->
                decodeMonth(raw).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }

        api.fetchMonth(year, month, location.latitude, location.longitude)?.let { response ->
            val parsed = response.toDays()
            if (parsed.isNotEmpty()) {
                val encoded = json.encodeToString(
                    AlAdhanCalendarResponse.serializer(),
                    response,
                )
                cache.edit()
                    .putString(key, encoded)
                    .putString(lastMonthKey(location), encoded)
                    .apply()
                return parsed
            }
        }

        return cached(lastMonthKey(location))?.let { decodeMonth(it) }.orEmpty()
    }

    private fun decodeDay(raw: String): AlAdhanDayResponse? =
        runCatching {
            json.decodeFromString(AlAdhanDayResponse.serializer(), raw)
        }.getOrNull()

    private fun decodeMonth(raw: String): List<HijriCalendarDay> =
        runCatching {
            json.decodeFromString(AlAdhanCalendarResponse.serializer(), raw)
        }
            .getOrNull()
            ?.toDays()
            .orEmpty()

    private fun AlAdhanCalendarResponse.toDays(): List<HijriCalendarDay> =
        data.mapNotNull { it.toHijriCalendarDay() }

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