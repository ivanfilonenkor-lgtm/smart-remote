package app.smartremote.android.input

import kotlin.math.PI
import kotlin.math.abs

/**
 * Timestamp-aware implementation of the 1 Euro filter described by Casiez, Roussel and Vogel:
 * https://gery.casiez.net/publications/CHI2012-casiez.pdf
 */
internal class OneEuroFilter(
    private val minCutoffHz: Double,
    private val beta: Double,
    private val derivativeCutoffHz: Double = 1.0,
) {
    private val valueFilter = LowPassFilter()
    private val derivativeFilter = LowPassFilter()
    private var previousInput: Double? = null

    init {
        require(minCutoffHz > 0.0)
        require(beta >= 0.0)
        require(derivativeCutoffHz > 0.0)
    }

    fun filter(value: Double, dtSeconds: Double): Double {
        require(value.isFinite())
        require(dtSeconds > 0.0 && dtSeconds.isFinite())

        val previous = previousInput
        val rawDerivative = if (previous == null) 0.0 else (value - previous) / dtSeconds
        previousInput = value
        val derivative = derivativeFilter.filter(
            rawDerivative,
            smoothingFactor(derivativeCutoffHz, dtSeconds),
        )
        val cutoff = minCutoffHz + beta * abs(derivative)
        return valueFilter.filter(value, smoothingFactor(cutoff, dtSeconds))
    }

    fun reset() {
        valueFilter.reset()
        derivativeFilter.reset()
        previousInput = null
    }

    private fun smoothingFactor(cutoffHz: Double, dtSeconds: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoffHz)
        return 1.0 / (1.0 + tau / dtSeconds)
    }
}

private class LowPassFilter {
    private var filteredValue: Double? = null

    fun filter(value: Double, alpha: Double): Double {
        require(alpha > 0.0 && alpha <= 1.0)
        val filtered = filteredValue?.let { previous ->
            alpha * value + (1.0 - alpha) * previous
        } ?: value
        filteredValue = filtered
        return filtered
    }

    fun reset() {
        filteredValue = null
    }
}
