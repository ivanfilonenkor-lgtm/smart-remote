package app.smartremote.android.network

internal class RateLimiter(private val minimumIntervalMs: Long) {
    private var lastEmissionMs: Long? = null

    init {
        require(minimumIntervalMs > 0)
    }

    fun shouldEmit(nowMs: Long, force: Boolean = false): Boolean {
        val last = lastEmissionMs
        if (!force && last != null && nowMs - last < minimumIntervalMs) return false
        lastEmissionMs = nowMs
        return true
    }
}
