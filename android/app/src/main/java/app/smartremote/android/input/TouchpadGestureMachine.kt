package app.smartremote.android.input

import kotlin.math.hypot

data class TouchPoint(val id: Long, val x: Float, val y: Float)

sealed interface GestureOutput {
    data class Pointer(val streamId: UInt, val sequence: UInt, val totalX: Float, val totalY: Float) : GestureOutput
    data class Scroll(val streamId: UInt, val sequence: UInt, val totalX: Float, val totalY: Float) : GestureOutput
    data class MouseButton(val button: Button, val down: Boolean) : GestureOutput
    data object Click : GestureOutput
    data object RightClick : GestureOutput
}

enum class Button { Left, Right }

class TouchpadGestureMachine(
    private val touchSlop: Float,
    private val doubleTapTimeoutMs: Long,
    private val dragHoldMs: Long = 160,
    private val pointerSensitivity: Float = 1f,
    private val scrollSpeed: Float = 1f,
    private val streamIdProvider: (() -> UInt)? = null,
) {
    private enum class Mode { Idle, OneCandidate, Pointer, Drag, TwoCandidate, Scroll, TwoFinishing, Cancelled }

    private var mode = Mode.Idle
    private var secondTap = false
    private var downTimeMs = 0L
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var totalX = 0f
    private var totalY = 0f
    private var streamCounter = 0u
    private var sequence = 0u
    private var lastTapTimeMs: Long? = null
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var previousCount = 0

    fun update(timeMs: Long, points: List<TouchPoint>): List<GestureOutput> {
        val sorted = points.sortedBy(TouchPoint::id)
        val output = mutableListOf<GestureOutput>()
        when {
            sorted.size >= 3 -> cancelInto(output)
            previousCount == 0 && sorted.size == 1 -> beginOne(timeMs, sorted[0])
            previousCount <= 1 && sorted.size == 2 -> beginTwo(sorted)
            sorted.size == 1 -> updateOne(timeMs, sorted[0], output)
            sorted.size == 2 -> updateTwo(sorted, output)
            sorted.isEmpty() -> finish(timeMs, output)
        }
        previousCount = sorted.size
        return output
    }

    fun cancel(): List<GestureOutput> {
        val output = mutableListOf<GestureOutput>()
        cancelInto(output)
        previousCount = 0
        return output
    }

    private fun beginOne(timeMs: Long, point: TouchPoint) {
        mode = Mode.OneCandidate
        downTimeMs = timeMs
        startX = point.x
        startY = point.y
        lastX = point.x
        lastY = point.y
        totalX = 0f
        totalY = 0f
        sequence = 0u
        streamCounter = streamIdProvider?.invoke() ?: streamCounter + 1u
        secondTap = lastTapTimeMs?.let { previous ->
            timeMs - previous <= doubleTapTimeoutMs && distance(point.x, point.y, lastTapX, lastTapY) <= touchSlop * 2
        } ?: false
    }

    private fun beginTwo(points: List<TouchPoint>) {
        mode = Mode.TwoCandidate
        val (x, y) = centroid(points)
        startX = x
        startY = y
        lastX = x
        lastY = y
        totalX = 0f
        totalY = 0f
        sequence = 0u
        streamCounter = streamIdProvider?.invoke() ?: streamCounter + 1u
        lastTapTimeMs = null
    }

    private fun updateOne(timeMs: Long, point: TouchPoint, output: MutableList<GestureOutput>) {
        if (mode == Mode.TwoCandidate) {
            mode = Mode.TwoFinishing
            return
        }
        if (mode == Mode.Scroll) {
            // Fingers are normally lifted one after another. A completed scroll must stay
            // disqualified from the two-finger-tap/right-click path while the last finger lifts.
            mode = Mode.Cancelled
            return
        }
        if (mode == Mode.TwoFinishing || mode == Mode.Cancelled) return
        val moved = distance(point.x, point.y, startX, startY) > touchSlop
        if (mode == Mode.OneCandidate && secondTap && (moved || timeMs - downTimeMs >= dragHoldMs)) {
            mode = Mode.Drag
            output += GestureOutput.MouseButton(Button.Left, true)
        } else if (mode == Mode.OneCandidate && !secondTap && moved) {
            mode = Mode.Pointer
        }
        if (mode == Mode.Pointer || mode == Mode.Drag) {
            val dx = (point.x - lastX) * pointerSensitivity
            val dy = (point.y - lastY) * pointerSensitivity
            totalX += dx
            totalY += dy
            if (dx != 0f || dy != 0f) {
                output += GestureOutput.Pointer(streamCounter, sequence++, totalX, totalY)
            }
        }
        lastX = point.x
        lastY = point.y
    }

    private fun updateTwo(points: List<TouchPoint>, output: MutableList<GestureOutput>) {
        if (mode != Mode.TwoCandidate && mode != Mode.Scroll) return
        val (x, y) = centroid(points)
        if (mode == Mode.TwoCandidate && distance(x, y, startX, startY) > touchSlop) {
            mode = Mode.Scroll
        }
        if (mode == Mode.Scroll) {
            totalX += (x - lastX) * scrollSpeed
            totalY += (y - lastY) * scrollSpeed
            output += GestureOutput.Scroll(streamCounter, sequence++, totalX, totalY)
        }
        lastX = x
        lastY = y
    }

    private fun finish(timeMs: Long, output: MutableList<GestureOutput>) {
        when (mode) {
            Mode.OneCandidate -> {
                output += GestureOutput.Click
                lastTapTimeMs = timeMs
                lastTapX = startX
                lastTapY = startY
            }
            Mode.Drag -> {
                output += GestureOutput.MouseButton(Button.Left, false)
                lastTapTimeMs = null
            }
            Mode.TwoCandidate, Mode.TwoFinishing -> {
                output += GestureOutput.RightClick
                lastTapTimeMs = null
            }
            Mode.Pointer, Mode.Scroll, Mode.Cancelled, Mode.Idle -> lastTapTimeMs = null
        }
        mode = Mode.Idle
        secondTap = false
    }

    private fun cancelInto(output: MutableList<GestureOutput>) {
        if (mode == Mode.Drag) output += GestureOutput.MouseButton(Button.Left, false)
        mode = Mode.Cancelled
        lastTapTimeMs = null
    }

    private fun centroid(points: List<TouchPoint>): Pair<Float, Float> =
        points.map(TouchPoint::x).average().toFloat() to points.map(TouchPoint::y).average().toFloat()

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        hypot(x1 - x2, y1 - y2)
}
