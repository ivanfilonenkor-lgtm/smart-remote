package app.smartremote.android.input

data class AirMouseActivationState(
    val aimHeld: Boolean = false,
    val locked: Boolean = false,
    val leftHeld: Boolean = false,
    val rightHeld: Boolean = false,
) {
    val active: Boolean
        get() = aimHeld || locked || leftHeld || rightHeld
}

enum class ActivityTransition { None, Start, Stop }

class AirMouseActivationController {
    var state: AirMouseActivationState = AirMouseActivationState()
        private set

    fun setAimHeld(held: Boolean): ActivityTransition = update(state.copy(aimHeld = held))

    fun setLocked(locked: Boolean): ActivityTransition = update(state.copy(locked = locked))

    fun setButtonHeld(button: Button, held: Boolean): ActivityTransition = when (button) {
        Button.Left -> update(state.copy(leftHeld = held))
        Button.Right -> update(state.copy(rightHeld = held))
    }

    fun reset(): ActivityTransition = update(AirMouseActivationState())

    private fun update(next: AirMouseActivationState): ActivityTransition {
        val wasActive = state.active
        state = next
        return when {
            !wasActive && next.active -> ActivityTransition.Start
            wasActive && !next.active -> ActivityTransition.Stop
            else -> ActivityTransition.None
        }
    }
}
