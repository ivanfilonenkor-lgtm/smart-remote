package app.smartremote.android.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirMouseStateTest {
    @Test
    fun holdAndLockComposeWithoutStoppingEachOther() {
        val controller = AirMouseActivationController()
        assertEquals(ActivityTransition.Start, controller.setAimHeld(true))
        assertEquals(ActivityTransition.None, controller.setLocked(true))
        assertEquals(ActivityTransition.None, controller.setAimHeld(false))
        assertTrue(controller.state.active)
        assertEquals(ActivityTransition.Stop, controller.setLocked(false))
        assertFalse(controller.state.active)
    }

    @Test
    fun heldMouseButtonActivatesMovementAndResetStopsEverything() {
        val controller = AirMouseActivationController()
        assertEquals(ActivityTransition.Start, controller.setButtonHeld(Button.Left, true))
        assertTrue(controller.state.leftHeld)
        assertEquals(ActivityTransition.Stop, controller.reset())
        assertEquals(AirMouseActivationState(), controller.state)
    }
}
