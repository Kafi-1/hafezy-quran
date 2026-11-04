package com.hafeziquran.app.data.remote

import com.hafeziquran.app.data.model.HijriCalendarDay
import com.hafeziquran.app.data.model.HijriDate
import com.hafeziquran.app.data.model.PrayerDay
import com.hafeziquran.app.data.model.PrayerName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/**
 * Wire shapes returned by [AlAdhanService], kept deliberately close to the API
 * JSON so they can be cached and decoded again without any lossy mapping.
 */
@Serializable
data class AlAdhanDayResponse(val data: AlAdhanDayData? = null)

@Serializable
data class AlAdhanCalendarResponse(val data: List<AlAdhanDayData> = emptyList())

@Serializable
data class AlAdhanDayData(
    val timings: Map<String, String> = emptyMap(),
    val date: AlAdhanDateInfo? = null,
)

@Serializable
data class AlAdhanDateInfo(
    val hijri: AlAdhanHijri? = null,
    val gregorian: AlAdhanGregorian? = null,
)

@Serializable
data class AlAdhanHijri(
    val day: String = "",
    val month: AlAdhanMonth = AlAdhanMonth(),
    val year: String = "",
)

@Serializable
data class AlAdhanGregorian(
    val day: String = "",
    val month: AlAdhanMonth = AlAdhanMonth(),
    val year: String = "",
)

@Serializable
data class AlAdhanMonth(
    val number: Int = 0,
    val en: String = "",
    val ar: String = "",
)

private val TIME_PART = Regex("(\\d{1,2}):(\\d{2})")
private const val IMSAK_KEY = "Imsak"

/**
 * Maps a wire payload to the domain [PrayerDay]. Returns null when the payload is
 * incomplete (for example a partially filtered response) so callers can fall back
 * to the cache instead of showing blanks.
 */
fun AlAdhanDayData.toPrayerDay(): PrayerDay? {
    val times = buildMap {
        PrayerName.entries.forEach { prayer ->
            parseTime(timings[prayer.apiKey])?.let { put(prayer, it) }
        }
    }
    if (times.size < PrayerName.entries.size) return null
    val localDate = gregorianDate() ?: return null
    return PrayerDay(
        date = localDate,
        times = times,
        imsak = parseTime(timings[IMSak_KEY]),
        maghrib = times[PrayerName.MAGHRIB],
        hijri = date?.hijri?.toModel(),
    )
}

fun AlAdhanDayData.toHijriCalendarDay(): HijriCalendarDay? {
    val gregorian = date?.gregorian ?: return null
    val hijri = date?.hijri ?: return null
    val year = gregorian.year.toIntOrNull() ?: return null
    val dayOfMonth = gregorian.day.toIntOrNull() ?: return null
    val hijriDay = hijri.day.toIntOrNull() ?: return null
    val hijriYear = hijri.year.toIntOrNull() ?: return null
    val localDate = runCatching { LocalDate.of(year, gregorian.month.number, dayOfMonth) }
        .getOrNull() ?: return null
    return HijriCalendarDay(
        gregorian = localDate,
        hijriDay = hijriDay,
        hijriMonthNumber = hijri.month.number,
        hijriMonthEnglish = hijri.month.en,
        hijriYear = hijriYear,
        imsak = parseTime(timings[IMSak_KEY]),
        maghrib = parseTime(timings[PrayerName.MAGHRIB.apiKey]),
    )
}

private fun AlAdhanDayData.gregorianDate(): LocalDate? {
    val gregorian = date?.gregorian ?: return null
    val year = gregorian.year.toIntOrNull() ?: return null
    val day = gregorian.day.toIntOrNull() ?: return null
    return runCatching { LocalDate.of(year, gregorian.month.number, day) }.getOrNull()
}

private fun AlAdhanHijri.toModel(): HijriDate? {
    val day = day.toIntOrNull() ?: return null
    val year = year.toIntOrNull() ?: return null
    return HijriDate(
        day = day,
        monthNumber = month.number,
        monthEnglish = month.en,
        monthArabic = month.ar,
        year = year,
    )
}

private fun parseTime(value: String?): LocalTime? {
    if (value == null) return null
    val match = TIME_PART.find(value) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime.of(hour, minute)
}