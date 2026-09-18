package app.smartremote.android.settings

import android.content.Context
import app.smartremote.android.input.GyroStabilizationProfile
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.smartRemoteDataStore by preferencesDataStore(name = "smart_remote")

data class RemoteSettings(
    val host: String = "",
    val port: Int = 8765,
    val pointerSensitivity: Float = 1f,
    val scrollSpeed: Float = 1f,
    val haptics: Boolean = true,
    val airMouseEnabled: Boolean = false,
    val gyroSensitivity: Float = 1f,
    val gyroStabilizationProfile: GyroStabilizationProfile = GyroStabilizationProfile.Smooth,
    val invertGyroX: Boolean = true,
    val invertGyroY: Boolean = false,
    val pointerMode: PointerMode = PointerMode.Touchpad,
)

enum class PointerMode { Touchpad, AirMouse }

class SettingsRepository(private val context: Context) {
    private object Keys {
        val host = stringPreferencesKey("host")
        val port = intPreferencesKey("port")
        val pointerSensitivity = floatPreferencesKey("pointer_sensitivity")
        val scrollSpeed = floatPreferencesKey("scroll_speed")
        val haptics = booleanPreferencesKey("haptics")
        val clientId = stringPreferencesKey("client_id")
        val airMouseEnabled = booleanPreferencesKey("air_mouse_enabled")
        val gyroSensitivity = floatPreferencesKey("gyro_sensitivity")
        val gyroStabilizationProfile = stringPreferencesKey("gyro_stabilization_profile")
        val invertGyroX = booleanPreferencesKey("invert_gyro_x")
        val invertGyroY = booleanPreferencesKey("invert_gyro_y")
        val pointerMode = stringPreferencesKey("pointer_mode")
    }

    val settings: Flow<RemoteSettings> = context.smartRemoteDataStore.data.map { preferences ->
        RemoteSettings(
            host = preferences[Keys.host].orEmpty(),
            port = preferences[Keys.port] ?: 8765,
            pointerSensitivity = (preferences[Keys.pointerSensitivity] ?: 1f).coerceIn(0.5f, 2f),
            scrollSpeed = (preferences[Keys.scrollSpeed] ?: 1f).coerceIn(0.5f, 2f),
            haptics = preferences[Keys.haptics] ?: true,
            airMouseEnabled = preferences[Keys.airMouseEnabled] ?: false,
            gyroSensitivity = (preferences[Keys.gyroSensitivity] ?: 1f).coerceIn(0.5f, 3f),
            gyroStabilizationProfile = gyroProfileFromStored(preferences[Keys.gyroStabilizationProfile]),
            invertGyroX = preferences[Keys.invertGyroX] ?: true,
            invertGyroY = preferences[Keys.invertGyroY] ?: false,
            pointerMode = preferences[Keys.pointerMode]
                ?.let { stored -> PointerMode.entries.firstOrNull { it.name == stored } }
                ?: PointerMode.Touchpad,
        )
    }

    suspend fun saveEndpoint(host: String, port: Int) {
        context.smartRemoteDataStore.edit {
            it[Keys.host] = host
            it[Keys.port] = port
        }
    }

    suspend fun savePointerSensitivity(value: Float) {
        context.smartRemoteDataStore.edit {
            it[Keys.pointerSensitivity] = value.coerceIn(0.5f, 2f)
        }
    }

    suspend fun saveScrollSpeed(value: Float) {
        context.smartRemoteDataStore.edit {
            it[Keys.scrollSpeed] = value.coerceIn(0.5f, 2f)
        }
    }

    suspend fun saveHaptics(enabled: Boolean) {
        context.smartRemoteDataStore.edit { it[Keys.haptics] = enabled }
    }

    suspend fun saveAirMouseEnabled(enabled: Boolean) {
        context.smartRemoteDataStore.edit { it[Keys.airMouseEnabled] = enabled }
    }

    suspend fun saveGyroSensitivity(value: Float) {
        context.smartRemoteDataStore.edit {
            it[Keys.gyroSensitivity] = value.coerceIn(0.5f, 3f)
        }
    }

    suspend fun saveGyroStabilizationProfile(profile: GyroStabilizationProfile) {
        context.smartRemoteDataStore.edit { it[Keys.gyroStabilizationProfile] = profile.name }
    }

    suspend fun saveInvertGyroY(inverted: Boolean) {
        context.smartRemoteDataStore.edit { it[Keys.invertGyroY] = inverted }
    }

    suspend fun saveInvertGyroX(inverted: Boolean) {
        context.smartRemoteDataStore.edit { it[Keys.invertGyroX] = inverted }
    }

    suspend fun savePointerMode(mode: PointerMode) {
        context.smartRemoteDataStore.edit { it[Keys.pointerMode] = mode.name }
    }

    suspend fun clientId(): String {
        context.smartRemoteDataStore.data.first()[Keys.clientId]?.let { return it }
        val generated = UUID.randomUUID().toString()
        context.smartRemoteDataStore.edit { preferences ->
            if (preferences[Keys.clientId] == null) preferences[Keys.clientId] = generated
        }
        return context.smartRemoteDataStore.data.first()[Keys.clientId] ?: generated
    }
}

internal fun gyroProfileFromStored(value: String?): GyroStabilizationProfile =
    value?.let { stored -> GyroStabilizationProfile.entries.firstOrNull { it.name == stored } }
        ?: GyroStabilizationProfile.Smooth
