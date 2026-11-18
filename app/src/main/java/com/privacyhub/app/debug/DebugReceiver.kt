package com.privacyhub.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.privacyhub.app.BuildConfig
import com.privacyhub.app.privacy.TiltMath
import com.privacyhub.app.service.OverlayService

/**
 * Debug-only hook for emulators and hardware without a usable rotation sensor:
 *   adb shell am broadcast -a com.privacyhub.app.DEBUG_SET_TILT --ef angle 40
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        val angle = intent.getDoubleExtra(EXTRA_ANGLE, 0.0)
        TiltMath.simulatedAngleDeg = angle
        if (OverlayService.isRunning) {
            OverlayService.refresh(context)
        }
    }

    companion object {
        const val EXTRA_ANGLE = "angle"
    }
}
