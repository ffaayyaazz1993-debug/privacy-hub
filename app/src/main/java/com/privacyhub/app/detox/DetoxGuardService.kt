package com.privacyhub.app.detox

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.privacyhub.app.FocusMapApplication
import com.privacyhub.app.MainActivity
import com.privacyhub.app.domain.detox.DetoxCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Narrow, user-requested restriction guard. Observes only window-state package
 * identity, never content. Shows a non-trapping overlay over selected apps while
 * at least one activation source is live. Any live activation can be overridden;
 * the overlay always offers a visible way out.
 */
class DetoxGuardService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: LinearLayout? = null
    private var blocked: Set<String> = emptySet()
    private var liveCount = 0
    private var lastBlockedPackage: String? = null
    private var activeSourceNames: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        val repo = FocusMapApplication.instance.repository
        scope.launch {
            combine(repo.observeActivations(), repo.observeProfiles()) { a, p -> a to p }
                .collect { (activations, profiles) ->
                    val now = System.currentTimeMillis()
                    val live = DetoxCoordinator.liveSources(activations, now)
                    liveCount = live.size
                    activeSourceNames = live.joinToString(", ") { l ->
                        profiles.firstOrNull { it.id == l.profileId }?.name ?: "Detox"
                    }.ifBlank { "none" }
                    val packages = mutableSetOf<String>()
                    live.forEach { a -> packages += repo.blockedPackages(listOf(a.profileId)) }
                    blocked = packages
                    Log.d("DetoxGuard", "sources=${live.size} blocked=${packages.size}")
                    val dndWanted = live.any { l ->
                        profiles.firstOrNull { it.id == l.profileId }?.dndEnabled == true
                    }
                    if (dndWanted) DndController.applyAll(this@DetoxGuardService)
                    else DndController.restore(this@DetoxGuardService)
                }
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        if (event?.eventType != android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        Log.d("DetoxGuard", "window=$pkg live=$liveCount blocked=${blocked.size}")
        if (pkg == packageName || pkg == "com.android.settings" ||
            pkg.contains("launcher") || pkg.contains("systemui")) { hideOverlay(); return }
        if (liveCount <= 0 || blocked.isEmpty()) { hideOverlay(); return }
        if (pkg in blocked) showOverlay(pkg) else hideOverlay()
    }

    override fun onInterrupt() {}

    private fun showOverlay(pkg: String) {
        if (lastBlockedPackage == pkg && overlay != null) return
        lastBlockedPackage = pkg
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (overlay != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6141B2A"))
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            isClickable = true
        }
        root.addView(TextView(this).apply {
            text = "You're currently in a Focus Session."
            setTextColor(Color.WHITE); textSize = 20f; gravity = Gravity.CENTER })
        root.addView(TextView(this).apply {
            text = "Restricted: $pkg\nDetox: $activeSourceNames\n$liveCount active source(s)"
            setTextColor(Color.parseColor("#B3FFFFFF")); textSize = 14f; gravity = Gravity.CENTER })
        fun action(label: String, color: Int, onClick: () -> Unit): Button = Button(this).apply {
            text = label; setBackgroundColor(color); setTextColor(Color.WHITE)
            setOnClickListener { onClick() }
        }
        root.addView(action("Return to FocusMap", Color.parseColor("#FF3F51B5")) {
            startActivity(Intent(this@DetoxGuardService, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        root.addView(action("Emergency override 5 min", Color.parseColor("#FFB71C1C")) { overrideAll(5) })
        root.addView(action("Emergency override 15 min", Color.parseColor("#FFB71C1C")) { overrideAll(15) })
        root.addView(action("End Detox session", Color.parseColor("#FF616161")) { endAll() })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        runCatching { wm.addView(root, params) }
            .onSuccess { overlay = root; Log.d("DetoxGuard", "overlay added for $pkg") }
            .onFailure { Log.e("DetoxGuard", "overlay failed for $pkg: ${it.message}", it) }
    }

    private fun hideOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay?.let { runCatching { wm.removeView(it) } }; overlay = null
        lastBlockedPackage = null
    }

    private fun overrideAll(minutes: Int) {
        val repo = FocusMapApplication.instance.repository
        scope.launch {
            repo.currentActivations().forEach { repo.emergencyOverride(it.id, minutes) }
            hideOverlay()
        }
    }

    private fun endAll() {
        val repo = FocusMapApplication.instance.repository
        scope.launch {
            repo.currentActivations().forEach { repo.endManualDetox(it.id) }
            hideOverlay()
        }
    }
}
