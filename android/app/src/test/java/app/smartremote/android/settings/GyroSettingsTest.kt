package app.smartremote.android.settings

import app.smartremote.android.input.GyroStabilizationProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class GyroSettingsTest {
    @Test
    fun missingOrUnknownStoredProfileMigratesToSmooth() {
        assertEquals(GyroStabilizationProfile.Smooth, gyroProfileFromStored(null))
        assertEquals(GyroStabilizationProfile.Smooth, gyroProfileFromStored("OldProfile"))
    }

    @Test
    fun everyKnownProfileRoundTripsByName() {
        GyroStabilizationProfile.entries.forEach { profile ->
            assertEquals(profile, gyroProfileFromStored(profile.name))
        }
    }
}
