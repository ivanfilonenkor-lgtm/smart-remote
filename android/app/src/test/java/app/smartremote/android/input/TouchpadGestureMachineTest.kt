package app.smartremote.android.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadGestureMachineTest {
    private fun machine() = TouchpadGestureMachine(
        touchSlop = 5f,
        doubleTapTimeoutMs = 300,
        dragHoldMs = 150,
    )

    @Test
    fun tapProducesOrderedClick() {
        val machine = machine()
        assertTrue(machine.update(0, listOf(point(0, 10f, 10f))).isEmpty())
        assertEquals(listOf(GestureOutput.Click), machine.update(50, emptyList()))
    }

    @Test
    fun pointerUsesCumulativeDisplacement() {
        val machine = machine()
        machine.update(0, listOf(point(0, 10f, 10f)))
        val first = machine.update(10, listOf(point(0, 20f, 13f))).single() as GestureOutput.Pointer
        val second = machine.update(20, listOf(point(0, 25f, 15f))).single() as GestureOutput.Pointer
        assertEquals(10f, first.totalX)
        assertEquals(3f, first.totalY)
        assertEquals(15f, second.totalX)
        assertEquals(5f, second.totalY)
        assertEquals(first.streamId, second.streamId)
        assertTrue(second.sequence > first.sequence)
    }

    @Test
    fun doubleTapHoldStartsAndAlwaysReleasesDrag() {
        val machine = machine()
        machine.update(0, listOf(point(0, 10f, 10f)))
        machine.update(30, emptyList())
        machine.update(100, listOf(point(0, 10f, 10f)))
        assertEquals(
            listOf(GestureOutput.MouseButton(Button.Left, true)),
            machine.update(260, listOf(point(0, 10f, 10f))),
        )
        assertEquals(
            listOf(GestureOutput.MouseButton(Button.Left, false)),
            machine.update(270, emptyList()),
        )
    }

    @Test
    fun cancellationReleasesActiveDrag() {
        val machine = machine()
        machine.update(0, listOf(point(0, 0f, 0f)))
        machine.update(10, emptyList())
        machine.update(50, listOf(point(0, 0f, 0f)))
        machine.update(60, listOf(point(0, 10f, 0f)))
        assertEquals(
            listOf(GestureOutput.MouseButton(Button.Left, false)),
            machine.cancel(),
        )
    }

    @Test
    fun twoFingerTapIsRightClickAndSlideIsScroll() {
        val tap = machine()
        tap.update(0, listOf(point(0, 0f, 0f)))
        tap.update(1, listOf(point(0, 0f, 0f), point(1, 10f, 0f)))
        assertEquals(listOf(GestureOutput.RightClick), tap.update(20, emptyList()))

        val scroll = machine()
        scroll.update(0, listOf(point(0, 0f, 0f)))
        scroll.update(1, listOf(point(0, 0f, 0f), point(1, 10f, 0f)))
        val output = scroll.update(
            10,
            listOf(point(0, 0f, 20f), point(1, 10f, 20f)),
        ).single() as GestureOutput.Scroll
        assertEquals(20f, output.totalY)
        assertTrue(scroll.update(20, emptyList()).isEmpty())
    }

    @Test
    fun staggeredFingerReleaseAfterScrollNeverProducesRightClick() {
        val machine = machine()
        machine.update(0, listOf(point(0, 0f, 0f)))
        machine.update(1, listOf(point(0, 0f, 0f), point(1, 10f, 0f)))
        assertTrue(
            machine.update(
                10,
                listOf(point(0, 0f, 20f), point(1, 10f, 20f)),
            ).single() is GestureOutput.Scroll,
        )
        assertTrue(machine.update(20, listOf(point(0, 0f, 20f))).isEmpty())
        assertTrue(machine.update(30, emptyList()).isEmpty())
    }

    @Test
    fun staggeredReleaseWithoutMovementRemainsTwoFingerRightClick() {
        val machine = machine()
        machine.update(0, listOf(point(0, 0f, 0f)))
        machine.update(1, listOf(point(0, 0f, 0f), point(1, 10f, 0f)))
        assertTrue(machine.update(10, listOf(point(0, 0f, 0f))).isEmpty())
        assertEquals(listOf(GestureOutput.RightClick), machine.update(20, emptyList()))
    }

    @Test
    fun productionCanSupplySessionWideStreamIds() {
        var next = 40u
        val machine = TouchpadGestureMachine(
            touchSlop = 5f,
            doubleTapTimeoutMs = 300,
            streamIdProvider = { ++next },
        )
        machine.update(0, listOf(point(0, 0f, 0f)))
        val first = machine.update(10, listOf(point(0, 10f, 0f))).single() as GestureOutput.Pointer
        machine.update(20, emptyList())
        machine.update(30, listOf(point(0, 0f, 0f)))
        val second = machine.update(40, listOf(point(0, 10f, 0f))).single() as GestureOutput.Pointer
        assertEquals(41u, first.streamId)
        assertEquals(42u, second.streamId)
    }

    private fun point(id: Long, x: Float, y: Float) = TouchPoint(id, x, y)
}
