package com.hafeziquran.app.domain.repository

import com.hafeziquran.app.data.model.HijriCalendarDay
import com.hafeziquran.app.data.model.PrayerDay
import com.hafeziquran.app.data.model.PrayerLocation
import java.time.LocalDate

/**
 * Domain contract for prayer-time access. Presentation and the alarm scheduler
 * depend on this interface, not on the cache/network implementation.
 */
interface PrayerTimesRepository {

    suspend fun getDay(
        date: LocalDate,
        location: PrayerLocation,
        forceRefresh: Boolean = false,
    ): PrayerDay?

    suspend fun getMonth(
        year: Int,
        month: Int,
        location: PrayerLocation,
        forceRefresh: Boolean = false,
    ): List<HijriCalendarDay>
}