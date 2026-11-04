package com.hafeziquran.app.domain.calendar

import com.hafeziquran.app.data.model.HijriCalendarDay

/** A notable Hijri date shown on the calendar. */
data class IslamicEvent(
    val title: String,
    val arabicTitle: String,
    val monthNumber: Int,
    val day: Int,
)

/**
 * Pure domain catalogue of observances. Kept free of Android types so the
 * calendar grid and any future widget can share the same rules.
 */
object IslamicEvents {

    private val events = listOf(
        IslamicEvent("হিজরি নববর্ষ", "رأس السنة الهجرية", 1, 1),
        IslamicEvent("আশুরা", "عاشوراء", 1, 10),
        IslamicEvent("শবে মেরাজ", "الإسراء والمعراج", 7, 27),
        IslamicEvent("শবে বরাত", "ليلة البراءة", 8, 15),
        IslamicEvent("রমজান শুরু", "أول رمضان", 9, 1),
        IslamicEvent("শবে কদর", "ليلة القدر", 9, 27),
        IslamicEvent("ঈদুল ফিতর", "عيد الفطر", 10, 1),
        IslamicEvent("আরাফাহ", "يوم عرفة", 12, 9),
        IslamicEvent("ঈদুল আজহা", "عيد الأضحى", 12, 10),
    )

    fun forDate(hijriMonthNumber: Int, hijriDay: Int): IslamicEvent? =
        events.firstOrNull { it.monthNumber == hijriMonthNumber && it.day == hijriDay }

    fun forDay(day: HijriCalendarDay): IslamicEvent? =
        forDate(day.hijriMonthNumber, day.hijriDay)

    /** Observances present in the given month page, in calendar order. */
    fun inMonth(days: List<HijriCalendarDay>): List<Pair<HijriCalendarDay, IslamicEvent>> =
        days.mapNotNull { day -> forDay(day)?.let { event -> day to event } }
            .sortedBy { it.first.gregorian }

    private val monthNames = listOf(
        "মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি",
        "জমাদিউল আউয়াল", "জমাদিউস সানি", "রজব", "শাবান",
        "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ",
    )

    fun monthNameBangla(number: Int): String =
        monthNames.getOrElse(number - 1) { "" }
}