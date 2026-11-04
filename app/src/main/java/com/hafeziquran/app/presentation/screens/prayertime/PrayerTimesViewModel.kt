package com.hafeziquran.app.presentation.screens.prayertime

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.core.constants.District
import com.hafeziquran.app.data.model.HijriCalendarDay
import com.hafeziquran.app.data.model.HijriDate
import com.hafeziquran.app.data.model.PrayerDay
import com.hafeziquran.app.data.model.PrayerLocation
import com.hafeziquran.app.data.model.PrayerName
import com.hafeziquran.app.data.preferences.PrayerPreferences
import com.hafeziquran.app.data.repository.PrayerTimesRepository
import com.hafeziquran.app.prayer.AdhanScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

data class PrayerTimesUiState(
    val loading: Boolean = false,
    val monthLoading: Boolean = false,
    val gpsLoading: Boolean = false,
    val error: String? = null,
    val location: PrayerLocation? = null,
    val today: PrayerDay? = null,
    val hijri: HijriDate? = null,
    val stale: Boolean = false,
    val adhanEnabled: Map<PrayerName, Boolean> = emptyMap(),
    val audioUris: Map<PrayerName, String?> = emptyMap(),
    val displayedMonth: YearMonth = YearMonth.now(),
    val monthDays: List<HijriCalendarDay> = emptyList(),
    val canScheduleExact: Boolean = true,
) {
    val isRamadan: Boolean get() = hijri?.isRamadan == true
}

@HiltViewModel
class PrayerTimesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PrayerTimesRepository,
    private val preferences: PrayerPreferences,
    private val scheduler: AdhanScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(PrayerTimesUiState())
    val state: StateFlow<PrayerTimesUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(canScheduleExact = scheduler.canScheduleExact()) }

        viewModelScope.launch {
            preferences.location.collect { location ->
                _state.update { it.copy(location = location) }
                if (location != null) {
                    load(location, month = _state.value.displayedMonth)
                } else {
                    _state.update { it.copy(loading = false) }
                }
            }
        }

        viewModelScope.launch {
            combine(
                preferences.allAdhanEnabled,
                preferences.allAudioUris,
            ) { enabled, uris -> enabled to uris }
                .collect { (enabled, uris) ->
                    _state.update { it.copy(adhanEnabled = enabled, audioUris = uris) }
                }
        }
    }

    fun selectDistrict(district: District) {
        viewModelScope.launch {
            preferences.setLocation(
                PrayerLocation(district.name, district.latitude, district.longitude),
            )
        }
    }

    fun useGps(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(gpsLoading = true) }
            val location = withContext(Dispatchers.IO) { findGpsLocation() }
            if (location != null) {
                val label = withContext(Dispatchers.IO) { labelFor(location) }
                preferences.setLocation(
                    PrayerLocation(label, location.latitude, location.longitude),
                )
            }
            _state.update { it.copy(gpsLoading = false) }
            onResult(location != null)
        }
    }

    fun changeLocation() {
        // Clearing the location returns the screen to district/GPS setup.
        viewModelScope.launch {
            preferences.clearLocation()
            _state.update { it.copy(today = null, hijri = null, monthDays = emptyList()) }
        }
    }

    fun setAdhanEnabled(prayer: PrayerName, enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAdhanEnabled(prayer, enabled)
            if (enabled) {
                scheduler.rescheduleAll()
            } else {
                scheduler.cancel(prayer)
            }
        }
    }

    fun setAudioUri(prayer: PrayerName, uri: String?) {
        viewModelScope.launch { preferences.setAdhanAudioUri(prayer, uri) }
    }

    fun changeMonth(offset: Long) {
        val month = _state.value.displayedMonth.plusMonths(offset)
        val location = _state.value.location ?: return
        _state.update { it.copy(displayedMonth = month, monthLoading = true) }
        viewModelScope.launch {
            val days = withContext(Dispatchers.IO) {
                repository.getMonth(month.year, month.monthValue, location)
            }
            _state.update { it.copy(monthDays = days, monthLoading = false) }
        }
    }

    fun refreshExactAlarmPermission() {
        _state.update { it.copy(canScheduleExact = scheduler.canScheduleExact()) }
    }

    fun refresh() {
        val location = _state.value.location ?: return
        viewModelScope.launch(Dispatchers.IO) {
            load(location, month = _state.value.displayedMonth, force = true)
        }
    }

    private suspend fun load(
        location: PrayerLocation,
        month: YearMonth,
        force: Boolean = false,
    ) {
        _state.update { it.copy(loading = true, error = null) }
        val today = LocalDate.now()
        val day = withContext(Dispatchers.IO) { repository.getDay(today, location, force) }
        // Prefetch tomorrow so the next day's alarms can be scheduled even if the
        // app is not opened again.
        withContext(Dispatchers.IO) { repository.getDay(today.plusDays(1), location, force) }
        val days = withContext(Dispatchers.IO) {
            repository.getMonth(month.year, month.monthValue, location, force)
        }
        _state.update {
            it.copy(
                loading = false,
                today = day,
                hijri = day?.hijri,
                stale = day?.stale == true,
                monthDays = days,
                displayedMonth = month,
                error = if (day == null) {
                    "নামাজের সময় আনতে পারা যায়নি। ইন্টারনেট সংযোগ দেখুন।"
                } else {
                    null
                },
            )
        }
        withContext(Dispatchers.IO) { scheduler.rescheduleAll() }
    }

    private suspend fun findGpsLocation(): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
        val known = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter { System.currentTimeMillis() - it.time < FRESH_FIX_MS }
            .maxByOrNull { it.time }
        return known ?: requestSingleFix(manager)
    }

    private suspend fun requestSingleFix(manager: LocationManager): Location? {
        val provider = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstOrNull { p ->
            runCatching { manager.isProviderEnabled(p) }.getOrDefault(false)
        } ?: return null
        return withTimeoutOrNull(15_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (continuation.isActive) continuation.resume(location)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(
                            provider: String?,
                            status: Int,
                            extras: Bundle?,
                        ) = Unit

                        override fun onProviderEnabled(provider: String) = Unit

                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    continuation.invokeOnCancellation {
                        runCatching { manager.removeUpdates(listener) }
                    }
                    runCatching {
                        manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    }.onFailure {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }
    }

    private fun labelFor(location: Location): String {
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = runCatching {
            geocoder.getFromLocation(location.latitude, location.longitude, 1)
        }.getOrNull()?.firstOrNull()
        return address?.locality
            ?: address?.subAdminArea
            ?: address?.adminArea
            ?: String.format(
                Locale.US,
                "GPS %.3f, %.3f",
                location.latitude,
                location.longitude,
            )
    }

    private companion object {
        const val FRESH_FIX_MS = 10 * 60 * 1000L
    }
}