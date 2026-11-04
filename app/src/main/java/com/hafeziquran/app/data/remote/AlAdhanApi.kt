package com.hafeziquran.app.data.remote

import com.hafeziquran.app.data.model.HijriCalendarDay
import com.hafeziquran.app.data.model.HijriDate
import com.hafeziquran.app.data.model.PrayerDay
import com.hafeziquran.app.data.model.PrayerName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin AlAdhan client. The API needs no key, so this is plain
 * [HttpURLConnection] rather than a networking library: one GET, a body read,
 * and a kotlinx-serialization decode.
 *
 * Timings are requested with [CALCULATION_METHOD] (University of Islamic
 * Sciences, Karachi — the method used by most Bangladeshi calendars) and an
 * explicit Asia/Dhaka timezone so the returned clock times are local.
 */
@Singleton
class AlAdhanApi @Inject constructor() {

    suspend fun fetchDayRaw(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
    ): String? = withContext(Dispatchers.IO) {
        val day = date.format(DATE_FORMAT)
        get(
            "https://api.aladhan.com/v1/timings/$day" +
                "?latitude=$latitude&longitude=$longitude" +
                "&method=$CALCULATION_METHOD&timezone=${TIMEZONE.name}",
        )
    }

    /**
     * Gregorian month → Hijri calendar. Used for the month grid; one request
     * covers the whole visible page.
     */
    suspend fun fetchMonthRaw(
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double,
    ): String? = withContext(Dispatchers.IO) {
        get(
            "https://api.aladhan.com/v1/gToHCalendar/$month/$year" +
                "?latitude=$latitude&longitude=$longitude" +
                "&method=$CALCULATION_METHOD&timezone=${TIMEZONE.name}",
        )
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "HafezyQuran/1.0")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        /** University of Islamic Sciences, Karachi. */
        const val CALCULATION_METHOD = 1

        val TIMEZONE = java.time.ZoneId.of("Asia/Dhaka")

        private val DATE_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)
    }
}

/**
 * Decodes raw AlAdhan payloads into the small domain models the UI needs.
 *
 * Kept separate from [AlAdhanApi] so cached JSON can be re-decoded without any
 * network involvement.
 */
object AlAdhanJson {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val TIME_PART = Regex("(\\d{1,2}):(\\d{2})")

    fun parseDay(raw: String): PrayerDay? {
        val response = runCatching { json.decodeFromString<DayResponse>(raw) }.getOrNull()
        val data = response?.data ?: return null
        val times = buildMap {
            PrayerName.entries.forEach { prayer ->
                parseTime(data.timings[prayer.apiKey])?.let { put(prayer, it) }
            }
        }
        if (times.size < PrayerName.entries.size) return null
        val date = runCatching {
            LocalDate.of(
                data.date?.gregorian?.year?.toIntOrNull() ?: 0,
                data.date?.gregorian?.month?.number ?: 0,
                data.date?.gregorian?.day?.toIntOrNull() ?: 0,
            )
        }.getOrNull() ?: return null
        return PrayerDay(
            date = date,
            times = times,
            imsak = parseTime(data.timings[IMSak_KEY]),
            maghrib = times[PrayerName.MAGHRIB],
            hijri = data.date?.hijri?.toModel(),
        )
    }

    fun parseMonth(raw: String): List<HijriCalendarDay> {
        val response = runCatching { json.decodeFromString<CalendarResponse>(raw) }.getOrNull()
        return response?.data.orEmpty().mapNotNull { entry ->
            val gregorian = entry.date?.gregorian ?: return@mapNotNull null
            val hijri = entry.date?.hijri ?: return@mapNotNull null
            val year = gregorian.year.toIntOrNull() ?: return@mapNotNull null
            val dayOfMonth = gregorian.day.toIntOrNull() ?: return@mapNotNull null
            val hijriDay = hijri.day.toIntOrNull() ?: return@mapNotNull null
            val hijriYear = hijri.year.toIntOrNull() ?: return@mapNotNull null
            val date = runCatching { LocalDate.of(year, gregorian.month.number, dayOfMonth) }
                .getOrNull() ?: return@mapNotNull null
            HijriCalendarDay(
                gregorian = date,
                hijriDay = hijriDay,
                hijriMonthNumber = hijri.month.number,
                hijriMonthEnglish = hijri.month.en,
                hijriYear = hijriYear,
            )
        }
    }

    private fun parseTime(value: String?): LocalTime? {
        if (value == null) return null
        val match = TIME_PART.find(value) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun HijriDto.toModel(): HijriDate? {
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

    private const val IMSak_KEY = "Imsak"

    @Serializable
    private data class DayResponse(val data: DayData? = null)

    @Serializable
    private data class CalendarResponse(val data: List<DayData> = emptyList())

    @Serializable
    private data class DayData(
        val timings: Map<String, String> = emptyMap(),
        val date: DateInfo? = null,
    )

    @Serializable
    private data class DateInfo(
        val hijri: HijriDto? = null,
        val gregorian: GregorianDto? = null,
    )

    @Serializable
    private data class HijriDto(
        val day: String = "",
        val month: MonthDto = MonthDto(),
        val year: String = "",
    )

    @Serializable
    private data class GregorianDto(
        val day: String = "",
        val month: MonthDto = MonthDto(),
        val year: String = "",
    )

    @Serializable
    private data class MonthDto(
        val number: Int = 0,
        val en: String = "",
        val ar: String = "",
    )
}