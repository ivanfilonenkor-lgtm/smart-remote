package app.smartremote.android.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.hypot

data class GyroPointerSample(val cumulativeX: Float, val cumulativeY: Float)

internal class GyroMotionProcessor(
    private val sensitivity: Float,
    private val invertX: Boolean,
    private val invertY: Boolean,
    profile: GyroStabilizationProfile,
) {
    private val xFilter = OneEuroFilter(
        minCutoffHz = profile.minCutoffHz,
        beta = profile.beta,
        derivativeCutoffHz = profile.derivativeCutoffHz,
    )
    private val yFilter = OneEuroFilter(
        minCutoffHz = profile.minCutoffHz,
        beta = profile.beta,
        derivativeCutoffHz = profile.derivativeCutoffHz,
    )
    private val deadZone = profile.deadZoneRadPerSecond

    private var totalX = 0.0
    private var totalY = 0.0
    private var lastTimestampNs: Long? = null

    fun applyAngularVelocity(
        timestampNs: Long,
        rotationAroundXPerSecond: Float,
        rotationAroundYPerSecond: Float,
    ): GyroPointerSample? {
        val x = rotationAroundXPerSecond.toDouble()
        val y = rotationAroundYPerSecond.toDouble()
        if (!x.isFinite() || !y.isFinite()) {
            resetSignal()
            return null
        }

        val previousTimestamp = lastTimestampNs
        if (previousTimestamp == null) {
            prime(timestampNs, x, y)
            return null
        }

        val elapsedNs = timestampNs - previousTimestamp
        if (elapsedNs <= 0L) return null
        if (elapsedNs > MAX_GYRO_GAP_NS) {
            prime(timestampNs, x, y)
            return null
        }
        lastTimestampNs = timestampNs

        if (hypot(x, y) > SHAKE_REJECT_THRESHOLD_RAD_PER_SECOND) {
            resetSignal()
            return null
        }

        val dtSeconds = elapsedNs.toDouble() / NANOS_PER_SECOND
        val filteredX = xFilter.filter(x, dtSeconds)
        val filteredY = yFilter.filter(y, dtSeconds)
        val magnitude = hypot(filteredX, filteredY)
        if (magnitude <= deadZone) return null

        val deadZoneScale = (magnitude - deadZone) / magnitude
        val effectiveX = filteredX * deadZoneScale
        val effectiveY = filteredY * deadZoneScale
        val scale = MOUSE_UNITS_PER_RADIAN * sensitivity.toDouble() * dtSeconds

        var dx = -effectiveY * scale
        var dy = effectiveX * scale
        if (invertX) dx = -dx
        if (invertY) dy = -dy

        val outputMagnitude = hypot(dx, dy)
        if (outputMagnitude > MAX_OUTPUT_PER_SAMPLE) {
            val clampScale = MAX_OUTPUT_PER_SAMPLE / outputMagnitude
            dx *= clampScale
            dy *= clampScale
        }
        if (dx == 0.0 && dy == 0.0) return null

        totalX += dx
        totalY += dy
        if (!totalX.isFinite() || !totalY.isFinite()) {
            reset()
            return null
        }
        return GyroPointerSample(totalX.toFloat(), totalY.toFloat())
    }

    fun reset() {
        totalX = 0.0
        totalY = 0.0
        resetSignal()
    }

    private fun prime(timestampNs: Long, x: Double, y: Double) {
        xFilter.reset()
        yFilter.reset()
        xFilter.filter(x, NOMINAL_SAMPLE_SECONDS)
        yFilter.filter(y, NOMINAL_SAMPLE_SECONDS)
        lastTimestampNs = timestampNs
    }

    private fun resetSignal() {
        xFilter.reset()
        yFilter.reset()
        lastTimestampNs = null
    }

    companion object {
        internal const val MOUSE_UNITS_PER_RADIAN = 1_500.0
        internal const val SHAKE_REJECT_THRESHOLD_RAD_PER_SECOND = 8.0
        internal const val MAX_GYRO_GAP_NS = 50_000_000L
        internal const val MAX_OUTPUT_PER_SAMPLE = 48.0
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val NOMINAL_SAMPLE_SECONDS = 0.01
    }
}

internal fun remapAngularVelocity(rotation: Int, x: Float, y: Float): Pair<Float, Float> =
    when (rotation) {
        Surface.ROTATION_90 -> y to -x
        Surface.ROTATION_180 -> -x to -y
        Surface.ROTATION_270 -> -y to x
        else -> x to y
    }

class GyroPointerController(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gameRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val selectedSensor = gyroscope ?: gameRotationVector
    private val generation = AtomicLong(0L)

    private var registeredListener: SensorEventListener? = null
    private var sensorThread: HandlerThread? = null

    val isAvailable: Boolean
        get() = selectedSensor != null

    val usesGameRotationVector: Boolean
        get() = gyroscope == null && gameRotationVector != null

    @Synchronized
    fun start(
        sensitivity: Float,
        invertX: Boolean,
        invertY: Boolean,
        profile: GyroStabilizationProfile,
        onSample: (GyroPointerSample) -> Unit,
    ): Boolean {
        stop()
        val sensor = selectedSensor ?: return false
        val sessionGeneration = generation.incrementAndGet()
        val processor = GyroMotionProcessor(
            sensitivity = sensitivity.coerceIn(0.5f, 3f),
            invertX = invertX,
            invertY = invertY,
            profile = profile,
        )
        val thread = HandlerThread("SmartRemoteGyroscope").also(HandlerThread::start)
        var previousRotationMatrix: FloatArray? = null
        var previousRotationTimestampNs: Long? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (generation.get() != sessionGeneration) return
                val sample = if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    val (x, y) = remapAngularVelocity(displayRotation(), event.values[0], event.values[1])
                    processor.applyAngularVelocity(event.timestamp, x, y)
                } else {
                    val raw = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(raw, event.values)
                    val current = remapRotationMatrix(raw, displayRotation())
                    val previous = previousRotationMatrix
                    val previousTimestamp = previousRotationTimestampNs
                    previousRotationMatrix = current
                    previousRotationTimestampNs = event.timestamp
                    if (previous == null || previousTimestamp == null) {
                        null
                    } else {
                        val elapsedNs = event.timestamp - previousTimestamp
                        if (elapsedNs <= 0L || elapsedNs > GyroMotionProcessor.MAX_GYRO_GAP_NS) {
                            null
                        } else {
                            val change = FloatArray(3)
                            SensorManager.getAngleChange(change, current, previous)
                            val seconds = elapsedNs.toFloat() / 1_000_000_000f
                            processor.applyAngularVelocity(
                                timestampNs = event.timestamp,
                                rotationAroundXPerSecond = change[1] / seconds,
                                rotationAroundYPerSecond = change[2] / seconds,
                            )
                        }
                    }
                }
                if (sample != null && generation.get() == sessionGeneration) onSample(sample)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        registeredListener = listener
        sensorThread = thread
        val registered = sensorManager.registerListener(
            listener,
            sensor,
            SENSOR_SAMPLING_PERIOD_US,
            0,
            Handler(thread.looper),
        )
        if (!registered) {
            stop()
            return false
        }
        return true
    }

    @Synchronized
    fun stop() {
        generation.incrementAndGet()
        registeredListener?.let(sensorManager::unregisterListener)
        registeredListener = null
        sensorThread?.quitSafely()
        sensorThread = null
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = windowManager.defaultDisplay.rotation

    private fun remapRotationMatrix(matrix: FloatArray, rotation: Int): FloatArray {
        if (rotation == Surface.ROTATION_0) return matrix
        val output = FloatArray(9)
        val (xAxis, yAxis) = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        return if (SensorManager.remapCoordinateSystem(matrix, xAxis, yAxis, output)) output else matrix
    }

    companion object {
        internal const val SENSOR_SAMPLING_PERIOD_US = 10_000
    }
}
