package app.smartremote.android.input

import android.view.Surface
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GyroMotionProcessorTest {
    @Test
    fun smoothProfileSuppressesStationaryNoiseForTenSeconds() {
        val processor = processor(profile = GyroStabilizationProfile.Smooth)
        assertNull(processor.applyAngularVelocity(0L, 0.02f, -0.02f))
        repeat(1_000) { index ->
            val noise = if (index % 2 == 0) 0.02f else -0.02f
            assertNull(processor.applyAngularVelocity((index + 1L) * 10_000_000L, noise, -noise))
        }
    }

    @Test
    fun physicalRotationIsIndependentOfSensorRate() {
        val at50Hz = runConstantRotation(sampleRateHz = 50)
        val at100Hz = runConstantRotation(sampleRateHz = 100)
        val at200Hz = runConstantRotation(sampleRateHz = 200)
        assertEquals(at100Hz, at50Hz, at100Hz * 0.02)
        assertEquals(at100Hz, at200Hz, at100Hz * 0.02)
    }

    @Test
    fun stabilizationProfilesSuppressTremorInExpectedOrder() {
        val smoothTravel = tremorTravel(GyroStabilizationProfile.Smooth)
        val balancedTravel = tremorTravel(GyroStabilizationProfile.Balanced)
        val fastTravel = tremorTravel(GyroStabilizationProfile.Fast)
        val values = "smooth=$smoothTravel balanced=$balancedTravel fast=$fastTravel"
        assertTrue(values, smoothTravel < balancedTravel)
        assertTrue(values, balancedTravel < fastTravel)
    }

    @Test
    fun softDeadZoneIsContinuousAtThreshold() {
        val processor = processor(profile = GyroStabilizationProfile.Smooth)
        assertNull(processor.applyAngularVelocity(0L, 0.0451f, 0f))
        val sample = processor.applyAngularVelocity(10_000_000L, 0.0451f, 0f)
        assertNotNull(sample)
        assertTrue(abs(sample!!.cumulativeY) in 0f..0.01f)
    }

    @Test
    fun sensitivityAndAxisInversionAreApplied() {
        val regular = processor(sensitivity = 1f, invertX = false, invertY = false)
        val doubled = processor(sensitivity = 2f, invertX = false, invertY = false)
        val inverted = processor(sensitivity = 1f, invertX = true, invertY = true)
        listOf(regular, doubled, inverted).forEach {
            assertNull(it.applyAngularVelocity(0L, 0.2f, -0.3f))
        }
        val a = regular.applyAngularVelocity(10_000_000L, 0.2f, -0.3f)!!
        val b = doubled.applyAngularVelocity(10_000_000L, 0.2f, -0.3f)!!
        val c = inverted.applyAngularVelocity(10_000_000L, 0.2f, -0.3f)!!
        assertEquals(a.cumulativeX * 2f, b.cumulativeX, 0.0001f)
        assertEquals(a.cumulativeY * 2f, b.cumulativeY, 0.0001f)
        assertEquals(-a.cumulativeX, c.cumulativeX, 0.0001f)
        assertEquals(-a.cumulativeY, c.cumulativeY, 0.0001f)
    }

    @Test
    fun shakeAndLongGapDoNotCreateCatchUpMovementOrTail() {
        val processor = processor()
        assertNull(processor.applyAngularVelocity(0L, 0.5f, 0f))
        assertNull(processor.applyAngularVelocity(10_000_000L, 9f, 0f))
        assertNull(processor.applyAngularVelocity(20_000_000L, 0f, 0f))
        assertNull(processor.applyAngularVelocity(30_000_000L, 0f, 0f))

        assertNull(processor.applyAngularVelocity(100_000_000L, 0.5f, 0f))
        assertNotNull(processor.applyAngularVelocity(110_000_000L, 0.5f, 0f))
    }

    @Test
    fun invalidAndOutOfOrderSamplesAreIgnored() {
        val processor = processor()
        assertNull(processor.applyAngularVelocity(10_000_000L, Float.NaN, 0f))
        assertNull(processor.applyAngularVelocity(20_000_000L, 0.5f, 0f))
        assertNull(processor.applyAngularVelocity(20_000_000L, 0.5f, 0f))
        assertNotNull(processor.applyAngularVelocity(30_000_000L, 0.5f, 0f))
    }

    @Test
    fun outputVectorIsClampedWithoutChangingDirection() {
        val processor = processor(sensitivity = 3f, profile = GyroStabilizationProfile.Fast)
        assertNull(processor.applyAngularVelocity(0L, 5f, 5f))
        val sample = processor.applyAngularVelocity(10_000_000L, 5f, 5f)!!
        val magnitude = kotlin.math.hypot(sample.cumulativeX, sample.cumulativeY)
        assertEquals(GyroMotionProcessor.MAX_OUTPUT_PER_SAMPLE.toFloat(), magnitude, 0.001f)
        assertTrue(sample.cumulativeX < 0f)
        assertTrue(sample.cumulativeY > 0f)
    }

    @Test
    fun angularVelocityIsRemappedForEveryDisplayRotation() {
        assertEquals(1f to 2f, remapAngularVelocity(Surface.ROTATION_0, 1f, 2f))
        assertEquals(2f to -1f, remapAngularVelocity(Surface.ROTATION_90, 1f, 2f))
        assertEquals(-1f to -2f, remapAngularVelocity(Surface.ROTATION_180, 1f, 2f))
        assertEquals(-2f to 1f, remapAngularVelocity(Surface.ROTATION_270, 1f, 2f))
    }

    private fun runConstantRotation(sampleRateHz: Int): Double {
        val processor = processor(profile = GyroStabilizationProfile.Balanced)
        val intervalNs = 1_000_000_000L / sampleRateHz
        assertNull(processor.applyAngularVelocity(0L, 0.5f, 0f))
        var sample: GyroPointerSample? = null
        repeat(sampleRateHz) { index ->
            sample = processor.applyAngularVelocity((index + 1L) * intervalNs, 0.5f, 0f) ?: sample
        }
        return sample!!.cumulativeY.toDouble()
    }

    private fun tremorTravel(profile: GyroStabilizationProfile): Double {
        val processor = processor(profile = profile)
        var previousX = 0f
        var previousY = 0f
        var travel = 0.0
        assertNull(processor.applyAngularVelocity(0L, 0f, 0f))
        repeat(500) { index ->
            val seconds = (index + 1) / 100.0
            val x = (0.08 * sin(seconds * Math.PI * 8.0)).toFloat()
            val sample = processor.applyAngularVelocity((index + 1L) * 10_000_000L, x, 0f)
            if (sample != null) {
                travel += abs(sample.cumulativeX - previousX) + abs(sample.cumulativeY - previousY)
                previousX = sample.cumulativeX
                previousY = sample.cumulativeY
            }
        }
        return travel
    }

    private fun processor(
        sensitivity: Float = 1f,
        invertX: Boolean = false,
        invertY: Boolean = false,
        profile: GyroStabilizationProfile = GyroStabilizationProfile.Smooth,
    ) = GyroMotionProcessor(sensitivity, invertX, invertY, profile)
}
