package com.privacyhub.app.privacy

import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.asin

/**
 * Converts the rotation-vector sensor into a signed left/right tilt angle in
 * degrees. Screen-horizontal gravity is used, so the same rule works whether
 * the phone is held portrait or landscape.
 */
object TiltMath {

    /** Debug/test hook: when non-null, this angle is used instead of the sensor. */
    @Volatile
    var simulatedAngleDeg: Double? = null

    private val rotationMatrix = FloatArray(9)

    fun lateralAngleDeg(values: FloatArray): Double {
        val simulated = simulatedAngleDeg
        if (simulated != null) return simulated
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)

        // rotationMatrix[6] is dot(screenX, worldUp); world gravity points down.
        val lateralGravity = -rotationMatrix[6].toDouble().coerceIn(-1.0, 1.0)
        return Math.toDegrees(asin(lateralGravity))
    }

    /**
     * Map the signed tilt angle to overlay alpha.
     * Below [thresholdDeg] the screen stays fully clear. Above it, alpha ramps
     * toward [maxAlpha] across [rampDeg] degrees.
     */
    fun alphaFor(
        angleDeg: Double,
        neutralDeg: Double,
        sensitivity: Double,
        thresholdDeg: Double,
        rampDeg: Double = 40.0,
        maxAlpha: Double = 0.72,
    ): Double {
        val delta = abs(angleDeg - neutralDeg) * sensitivity
        if (delta <= thresholdDeg) return 0.0
        val t = ((delta - thresholdDeg) / rampDeg).coerceIn(0.0, 1.0)
        return t * maxAlpha
    }
}
