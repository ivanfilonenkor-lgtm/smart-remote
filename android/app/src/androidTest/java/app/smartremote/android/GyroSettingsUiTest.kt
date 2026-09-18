package app.smartremote.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.smartremote.android.input.GyroStabilizationProfile
import app.smartremote.android.settings.RemoteSettings
import app.smartremote.android.ui.SettingsScreen
import app.smartremote.android.ui.SmartRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GyroSettingsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gyroProfilesRemainUsableAtNarrowWidthAndLargeFont() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val smooth = context.getString(R.string.gyro_profile_smooth)
        val balanced = context.getString(R.string.gyro_profile_balanced)
        val fast = context.getString(R.string.gyro_profile_fast)
        var selected = GyroStabilizationProfile.Smooth

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1.5f),
            ) {
                SmartRemoteTheme {
                    Box(Modifier.width(320.dp)) {
                        SettingsScreen(
                            settings = RemoteSettings(
                                airMouseEnabled = true,
                                gyroStabilizationProfile = selected,
                            ),
                            onPointerSensitivity = {},
                            onScrollSpeed = {},
                            onHaptics = {},
                            airMouseAvailable = true,
                            onAirMouseEnabled = {},
                            onGyroSensitivity = {},
                            onGyroStabilizationProfile = { selected = it },
                            onInvertGyroX = {},
                            onInvertGyroY = {},
                            onDone = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(smooth).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(balanced).performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(GyroStabilizationProfile.Balanced, selected) }
        composeRule.onNodeWithText(fast).performScrollTo().assertIsDisplayed()
    }
}
