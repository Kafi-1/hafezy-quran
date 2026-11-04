package com.hafeziquran.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin AlAdhan client over the Retrofit [AlAdhanService].
 *
 * Network failures are swallowed into `null`: the repository answers from its
 * cache in that case, so the UI never has to distinguish "offline" from
 * "malformed response".
 */
@Singleton
class AlAdhanApi @Inject constructor(
    private val service: AlAdhanService,
) {

    suspend fun fetchDay(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
    ): AlAdhanDayResponse? = withContext(Dispatchers.IO) {
        runCatching {
            service.timings(
                date = date.format(DATE_FORMAT),
                latitude = latitude,
                longitude = longitude,
                method = CALCULATION_METHOD,
                timezone = TIMEZONE_ID,
            )
        }.getOrNull()
    }

    suspend fun fetchMonth(
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double,
    ): AlAdhanCalendarResponse? = withContext(Dispatchers.IO) {
        runCatching {
            service.hijriCalendar(
                month = month,
                year = year,
                latitude = latitude,
                longitude = longitude,
                method = CALCULATION_METHOD,
                timezone = TIMEZONE_ID,
            )
        }.getOrNull()
    }

    companion object {
        /** University of Islamic Sciences, Karachi — used by Bangladeshi calendars. */
        const val CALCULATION_METHOD = 1

        private const val TIMEZONE_ID = "Asia/Dhaka"

        private val DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)
    }
}