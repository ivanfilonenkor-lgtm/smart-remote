package app.smartremote.android.input

enum class GyroStabilizationProfile(
    internal val minCutoffHz: Double,
    internal val beta: Double,
    internal val derivativeCutoffHz: Double,
    internal val deadZoneRadPerSecond: Double,
) {
    Smooth(
        minCutoffHz = 0.65,
        beta = 0.04,
        derivativeCutoffHz = 1.0,
        deadZoneRadPerSecond = 0.045,
    ),
    Balanced(
        minCutoffHz = 1.20,
        beta = 0.06,
        derivativeCutoffHz = 1.0,
        deadZoneRadPerSecond = 0.030,
    ),
    Fast(
        minCutoffHz = 2.00,
        beta = 0.10,
        derivativeCutoffHz = 1.0,
        deadZoneRadPerSecond = 0.018,
    ),
}
