package com.privacyhub.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Relays touches captured in the privacy viewing window into the app underneath.
 * Accessibility-dispatched gestures bypass the obscured-touch (tapjacking) block
 * that stops a normal overlay from being tapped-through over other apps.
 * Requires the user to enable this service in Android accessibility settings.
 */
class PrivacyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    /** Dispatch a stroke along [path] (screen coords) over [durationMs]. */
    fun relay(path: Path, durationMs: Long, onDone: () -> Unit): Boolean {
        if (path.isEmpty) {
            onDone()
            return false
        }
        val stroke = GestureDescription.StrokeDescription(
            path, 0, durationMs.coerceIn(1L, 5000L)
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = onDone()
                override fun onCancelled(gestureDescription: GestureDescription?) = onDone()
            },
            null
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: PrivacyAccessibilityService? = null
    }
}
