package app.smartremote.android.input

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneEuroFilterTest {
    @Test
    fun constantInputIsPreserved() {
        val filter = OneEuroFilter(minCutoffHz = 0.65, beta = 0.04)
        repeat(100) { assertEquals(0.25, filter.filter(0.25, 0.01), 1e-12) }
    }

    @Test
    fun lowCutoffSuppressesAlternatingNoise() {
        val filter = OneEuroFilter(minCutoffHz = 0.65, beta = 0.0)
        var filteredMagnitude = 0.0
        repeat(200) { index ->
            val raw = if (index % 2 == 0) 0.1 else -0.1
            filteredMagnitude += abs(filter.filter(raw, 0.01))
        }
        assertTrue(filteredMagnitude / 200.0 < 0.02)
    }

    @Test
    fun resetRemovesPreviousFilterTail() {
        val filter = OneEuroFilter(minCutoffHz = 0.65, beta = 0.04)
        repeat(10) { filter.filter(1.0, 0.01) }
        filter.reset()
        assertEquals(0.0, filter.filter(0.0, 0.01), 0.0)
    }
}
