package app.smartremote.android.network

import app.smartremote.android.RemoteViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {
    @Test
    fun volumeRateIsAtMostThirtyHertzAndFinalValueIsForced() {
        val limiter = RateLimiter(RemoteViewModel.VOLUME_SEND_INTERVAL_MS)
        assertTrue(limiter.shouldEmit(1_000))
        assertFalse(limiter.shouldEmit(1_033))
        assertTrue(limiter.shouldEmit(1_034))
        assertTrue(limiter.shouldEmit(1_035, force = true))
        assertTrue(1_000.0 / RemoteViewModel.VOLUME_SEND_INTERVAL_MS <= 30.0)
    }

    @Test
    fun realtimeCadenceIsAtMostSixtyHertz() {
        assertTrue(1_000.0 / SmartRemoteConnection.REALTIME_SEND_INTERVAL_MS <= 60.0)
    }
}
