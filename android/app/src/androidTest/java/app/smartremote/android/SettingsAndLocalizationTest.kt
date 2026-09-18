package app.smartremote.android

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import app.smartremote.android.settings.SettingsRepository
import app.smartremote.android.settings.PointerMode
import app.smartremote.android.input.GyroStabilizationProfile
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsAndLocalizationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun settingsArePersistedAndClamped() = runBlocking {
        val repository = SettingsRepository(context)
        repository.saveEndpoint("192.168.1.25", 9876)
        repository.savePointerSensitivity(9f)
        repository.saveScrollSpeed(0.1f)
        repository.saveHaptics(false)
        repository.saveAirMouseEnabled(true)
        repository.saveGyroSensitivity(9f)
        repository.saveGyroStabilizationProfile(GyroStabilizationProfile.Balanced)
        repository.saveInvertGyroX(true)
        repository.saveInvertGyroY(true)
        repository.savePointerMode(PointerMode.AirMouse)

        val saved = repository.settings.first()
        assertEquals("192.168.1.25", saved.host)
        assertEquals(9876, saved.port)
        assertEquals(2f, saved.pointerSensitivity)
        assertEquals(0.5f, saved.scrollSpeed)
        assertFalse(saved.haptics)
        assertEquals(true, saved.airMouseEnabled)
        assertEquals(3f, saved.gyroSensitivity)
        assertEquals(GyroStabilizationProfile.Balanced, saved.gyroStabilizationProfile)
        assertEquals(true, saved.invertGyroX)
        assertEquals(true, saved.invertGyroY)
        assertEquals(PointerMode.AirMouse, saved.pointerMode)

        // Keep the test application deterministic for MainActivityTest, which now
        // auto-connects whenever a saved endpoint is present.
        repository.saveEndpoint("", 8765)
        repository.savePointerMode(PointerMode.Touchpad)
    }

    @Test
    fun russianAndEnglishResourcesAreDistinct() {
        fun title(locale: Locale): String {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            return context.createConfigurationContext(configuration).getString(R.string.connect_title)
        }

        assertEquals("Connect to Windows", title(Locale.ENGLISH))
        assertEquals("Подключение к Windows", title(Locale.forLanguageTag("ru")))
    }
}
