package com.hafeziquran.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hafeziquran.app.data.model.PrayerLocation
import com.hafeziquran.app.data.model.PrayerName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Single DataStore for the prayer-times feature. */
private val Context.prayerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "prayer_settings",
)

/** Everything the alarm scheduler needs, read in one DataStore pass. */
data class PrayerSettingsSnapshot(
    val location: PrayerLocation?,
    val adhanEnabled: Map<PrayerName, Boolean>,
    val audioUris: Map<PrayerName, String?>,
    val sehriAlarmEnabled: Boolean,
    val iftarAlarmEnabled: Boolean,
    /** Per-prayer jamaat shift in minutes applied on top of the AlAdhan times. */
    val tuneMinutes: Map<PrayerName, Int>,
)

/**
 * Persists the one-time location choice plus per-prayer adhan settings.
 *
 * Kept out of [UserPreferences] so the reading/appearance keys stay focused; this
 * is a second DataStore file and needs no migration.
 */
@Singleton
class PrayerPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val LOCATION_NAME = stringPreferencesKey("location_name")
        val LOCATION_LAT = doublePreferencesKey("location_lat")
        val LOCATION_LNG = doublePreferencesKey("location_lng")
        val SEHRI_ALARM = booleanPreferencesKey("sehri_alarm_enabled")
        val IFTAR_ALARM = booleanPreferencesKey("iftar_alarm_enabled")
    }

    private fun enabledKey(prayer: PrayerName) =
        booleanPreferencesKey("adhan_enabled_${prayer.name}")

    private fun audioKey(prayer: PrayerName) =
        stringPreferencesKey("adhan_audio_${prayer.name}")

    private fun tuneKey(prayer: PrayerName) =
        intPreferencesKey("tune_minutes_${prayer.name}")

    val location: Flow<PrayerLocation?> = context.prayerDataStore.data.map { prefs ->
        val name = prefs[Keys.LOCATION_NAME]
        val lat = prefs[Keys.LOCATION_LAT]
        val lng = prefs[Keys.LOCATION_LNG]
        if (name != null && lat != null && lng != null) {
            PrayerLocation(name = name, latitude = lat, longitude = lng)
        } else {
            null
        }
    }

    fun adhanEnabled(prayer: PrayerName): Flow<Boolean> = context.prayerDataStore.data.map {
        it[enabledKey(prayer)] ?: true
    }

    fun adhanAudioUri(prayer: PrayerName): Flow<String?> = context.prayerDataStore.data.map {
        it[audioKey(prayer)]
    }

    val allAdhanEnabled: Flow<Map<PrayerName, Boolean>> = context.prayerDataStore.data.map { prefs ->
        PrayerName.entries.associateWith { prefs[enabledKey(it)] ?: true }
    }

    val allAudioUris: Flow<Map<PrayerName, String?>> = context.prayerDataStore.data.map { prefs ->
        PrayerName.entries.associateWith { prefs[audioKey(it)] }
    }

    val allTuneMinutes: Flow<Map<PrayerName, Int>> = context.prayerDataStore.data.map { prefs ->
        PrayerName.entries.associateWith { prefs[tuneKey(it)] ?: 0 }
    }

    val sehriAlarmEnabled: Flow<Boolean> = context.prayerDataStore.data.map {
        it[Keys.SEHRI_ALARM] ?: false
    }

    val iftarAlarmEnabled: Flow<Boolean> = context.prayerDataStore.data.map {
        it[Keys.IFTAR_ALARM] ?: false
    }

    suspend fun snapshot(): PrayerSettingsSnapshot {
        val prefs = context.prayerDataStore.data.first()
        val location = run {
            val name = prefs[Keys.LOCATION_NAME]
            val lat = prefs[Keys.LOCATION_LAT]
            val lng = prefs[Keys.LOCATION_LNG]
            if (name != null && lat != null && lng != null) {
                PrayerLocation(name, lat, lng)
            } else {
                null
            }
        }
        return PrayerSettingsSnapshot(
            location = location,
            adhanEnabled = PrayerName.entries.associateWith { prefs[enabledKey(it)] ?: true },
            audioUris = PrayerName.entries.associateWith { prefs[audioKey(it)] },
            sehriAlarmEnabled = prefs[Keys.SEHRI_ALARM] ?: false,
            iftarAlarmEnabled = prefs[Keys.IFTAR_ALARM] ?: false,
            tuneMinutes = PrayerName.entries.associateWith { prefs[tuneKey(it)] ?: 0 },
        )
    }

    suspend fun setLocation(location: PrayerLocation) {
        context.prayerDataStore.edit { prefs ->
            prefs[Keys.LOCATION_NAME] = location.name
            prefs[Keys.LOCATION_LAT] = location.latitude
            prefs[Keys.LOCATION_LNG] = location.longitude
        }
    }

    suspend fun clearLocation() {
        context.prayerDataStore.edit { prefs ->
            prefs.remove(Keys.LOCATION_NAME)
            prefs.remove(Keys.LOCATION_LAT)
            prefs.remove(Keys.LOCATION_LNG)
        }
    }

    suspend fun setAdhanEnabled(prayer: PrayerName, enabled: Boolean) {
        context.prayerDataStore.edit { it[enabledKey(prayer)] = enabled }
    }

    suspend fun setAdhanAudioUri(prayer: PrayerName, uri: String?) {
        context.prayerDataStore.edit { prefs ->
            if (uri == null) prefs.remove(audioKey(prayer)) else prefs[audioKey(prayer)] = uri
        }
    }

    suspend fun setTuneMinutes(prayer: PrayerName, minutes: Int) {
        context.prayerDataStore.edit { it[tuneKey(prayer)] = minutes }
    }

    /** Applies one offset per prayer at once (used by the preset / reset buttons). */
    suspend fun setAllTuneMinutes(offsets: Map<PrayerName, Int>) {
        context.prayerDataStore.edit { prefs ->
            offsets.forEach { (prayer, minutes) -> prefs[tuneKey(prayer)] = minutes }
        }
    }

    suspend fun setSehriAlarmEnabled(enabled: Boolean) {
        context.prayerDataStore.edit { it[Keys.SEHRI_ALARM] = enabled }
    }

    suspend fun setIftarAlarmEnabled(enabled: Boolean) {
        context.prayerDataStore.edit { it[Keys.IFTAR_ALARM] = enabled }
    }
}