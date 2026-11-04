package com.hafeziquran.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit binding for the keyless AlAdhan API.
 *
 * Timings use the University of Islamic Sciences, Karachi calculation method and
 * an explicit Asia/Dhaka timezone so the returned clock times are local.
 */
interface AlAdhanService {

    @GET("v1/timings/{date}")
    suspend fun timings(
        @Path("date") date: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int,
        @Query("timezone") timezone: String,
    ): AlAdhanDayResponse

    /**
     * Gregorian month → Hijri calendar. Each entry also carries that day's
     * timings, so one request powers both the month grid and the Ramadan
     * sehri/iftar schedule.
     */
    @GET("v1/gToHCalendar/{month}/{year}")
    suspend fun hijriCalendar(
        @Path("month") month: Int,
        @Path("year") year: Int,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int,
        @Query("timezone") timezone: String,
    ): AlAdhanCalendarResponse
}