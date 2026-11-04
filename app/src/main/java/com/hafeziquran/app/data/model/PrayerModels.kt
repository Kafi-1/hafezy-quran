package com.hafeziquran.app.data.model

import java.time.LocalDate
import java.time.LocalTime

/** The five daily prayers, in the order they occur. */
enum class PrayerName(val apiKey: String, val banglaName: String) {
    FAJR("Fajr", "ফজর"),
    DHUHR("Dhuhr", "যোহর"),
    ASR("Asr", "আসর"),
    MAGHRIB("Maghrib", "মাগরিব"),
    ISHA("Isha", "ইশা"),
}

/** Hijri date as reported by AlAdhan for a Gregorian day. */
data class HijriDate(
    val day: Int,
    val monthNumber: Int,
    val monthEnglish: String,
    val monthArabic: String,
    val year: Int,
) {
    val isRamadan: Boolean get() = monthNumber == 9
}

/** Prayer times for a single Gregorian day. */
data class PrayerDay(
    val date: LocalDate,
    val times: Map<PrayerName, LocalTime>,
    val imsak: LocalTime?,
    val maghrib: LocalTime?,
    val hijri: HijriDate?,
    /** True when the network failed and this is an older cached day. */
    val stale: Boolean = false,
)

/** One cell of the Hijri calendar month grid. */
data class HijriCalendarDay(
    val gregorian: LocalDate,
    val hijriDay: Int,
    val hijriMonthNumber: Int,
    val hijriMonthEnglish: String,
    val hijriYear: Int,
)

/** A user-selected location, persisted after the one-time setup. */
data class PrayerLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)