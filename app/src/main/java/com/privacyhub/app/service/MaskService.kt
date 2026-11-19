package com.privacyhub.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.privacyhub.app.PrivacyAccessibilityService
import com.privacyhub.app.privacy.HandleKind
import com.privacyhub.app.privacy.HandleView
import com.privacyhub.app.privacy.MaskConfig
import com.privacyhub.app.privacy.MaskStore
import com.privacyhub.app.privacy.MaskView

/**
 * System-wide blackout mask with a viewing window. The black area is
 * NOT_TOUCHABLE. The aperture is a transparent touch-capture window: touches
 * there are recorded and re-dispatched into the app underneath via the
 * accessibility service (which bypasses Android's obscured-touch block).
 *
 * During dispatch the capture window is made NOT_TOUCHABLE so the injected
 * gesture reaches the underlying app instead of looping back into our overlay.
 */
class MaskService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var maskView: MaskView? = null
    private var captureView: View? = null
    private var moveView: HandleView? = null
    private var resizeView: HandleView? = null

    private var captureParams: WindowManager.LayoutParams? = null
    private var moveParams: WindowManager.LayoutParams? = null
    private var resizeParams: WindowManager.LayoutParams? = null

    private var config: MaskConfig = MaskStore.DEFAULT
    private var running = false

    private val handleSizePx: Int
        get() = (56 * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { cleanup(); stopSelf(); return START_NOT_STICKY }
            ACTION_CONCEAL -> { config = MaskStore.CONCEALED.copy(dimAlpha = config.dimAlpha); persistAndRender() }
            ACTION_REVEAL -> { config = MaskStore.DEFAULT.copy(dimAlpha = config.dimAlpha); persistAndRender() }
            else -> startMask()
        }
        return START_NOT_STICKY
    }

    private fun startMask() {
        if (running) { config = MaskStore.load(this); render(); return }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        config = MaskStore.load(this)
        try {
            startForeground(NOTIF_ID, buildNotification())
            addMask()
            addCapture()
            addHandles()
            positionAll()
            running = true
        } catch (_: Throwable) {
            cleanup(); stopSelf()
        }
    }

    private fun addMask() {
        if (maskView != null) return
        val view = MaskView(this).apply { config = this@MaskService.config }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        maskView = view
        windowManager.addView(view, params)
    }

    private fun addCapture() {
        if (captureView != null) return
        val view = View(this).apply {
            setBackgroundColor(0x00000000)
            isClickable = true
            setOnTouchListener { _, e -> relayTouch(e) }
        }
        val params = WindowManager.LayoutParams(
            10, 10,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        captureView = view
        captureParams = params
        windowManager.addView(view, params)
    }

    private fun addHandles() {
        if (moveView != null || resizeView != null) return
        val size = handleSizePx
        val move = HandleView(this, kind = HandleKind.MOVE).apply {
            setOnTouchListener { _, e -> onMoveTouch(e) }
        }
        val resize = HandleView(this, kind = HandleKind.RESIZE).apply {
            setOnTouchListener { _, e -> onResizeTouch(e) }
        }
        moveParams = handleParams(size); resizeParams = handleParams(size)
        moveView = move; resizeView = resize
        windowManager.addView(move, moveParams)
        windowManager.addView(resize, resizeParams)
    }

    private fun handleParams(size: Int) = WindowManager.LayoutParams(
        size, size,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun positionAll() {
        val mask = maskView ?: return
        val w = mask.width; val h = mask.height
        if (w == 0 || h == 0) { handler.post { positionAll() }; return }
        val r = MaskStore.apertureRectF(w, h, config, resources.displayMetrics.density)
        captureParams?.let { p ->
            p.x = r.left.toInt(); p.y = r.top.toInt()
            p.width = (r.right - r.left).toInt().coerceAtLeast(1)
            p.height = (r.bottom - r.top).toInt().coerceAtLeast(1)
            captureView?.let { windowManager.updateViewLayout(it, p) }
        }
        val size = handleSizePx
        moveParams?.let { p ->
            p.x = (r.centerX() - size / 2f).toInt(); p.y = (r.bottom - size / 2f).toInt()
            moveView?.let { windowManager.updateViewLayout(it, p) }
        }
        resizeParams?.let { p ->
            p.x = (r.right - size / 2f).toInt(); p.y = (r.bottom - size / 2f).toInt()
            resizeView?.let { windowManager.updateViewLayout(it, p) }
        }
        mask.config = config
    }

    private fun render() {
        maskView?.config = config
        positionAll()
    }

    private fun persistAndRender() {
        config = MaskStore.normalize(config)
        MaskStore.save(this, config)
        render()
    }

    // ---- touch relay ----
    private val points = ArrayList<Pair<Float, Float>>()
    private var downTime = 0L

    private fun relayTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                points.clear(); points.add(e.rawX to e.rawY); downTime = SystemClock.uptimeMillis()
                return true
            }
            MotionEvent.ACTION_MOVE -> { points.add(e.rawX to e.rawY); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                points.add(e.rawX to e.rawY)
                dispatchRelay()
                return true
            }
        }
        return false
    }

    private fun dispatchRelay() {
        val service = PrivacyAccessibilityService.instance
        if (service == null || points.isEmpty()) { points.clear(); return }
        val path = Path().apply {
            moveTo(points.first().first, points.first().second)
            for (i in 1 until points.size) lineTo(points[i].first, points[i].second)
        }
        val duration = (SystemClock.uptimeMillis() - downTime).coerceIn(1L, 2000L)
        points.clear()
        // Make the capture window non-touchable so the injected gesture reaches
        // the app underneath rather than our own overlay.
        setCaptureTouchable(false)
        val dispatched = service.relay(path, duration) { setCaptureTouchable(true) }
        if (!dispatched) setCaptureTouchable(true)
    }

    private fun setCaptureTouchable(touchable: Boolean) {
        val view = captureView ?: return
        val p = captureParams ?: return
        p.flags = if (touchable) {
            p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching { windowManager.updateViewLayout(view, p) }
    }

    // ---- aperture move / resize ----
    private var startRawX = 0f; private var startRawY = 0f
    private var startLeft = 0f; private var startTop = 0f

    private fun onMoveTouch(e: MotionEvent): Boolean {
        val mask = maskView ?: return false
        val w = mask.width; val h = mask.height
        if (w == 0 || h == 0) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = e.rawX; startRawY = e.rawY
                startLeft = config.left; startTop = config.top; return true
            }
            MotionEvent.ACTION_MOVE -> {
                val cw = config.right - config.left; val ch = config.bottom - config.top
                val left = (startLeft + (e.rawX - startRawX) / w).coerceIn(0f, 1f - cw)
                val top = (startTop + (e.rawY - startRawY) / h).coerceIn(0f, 1f - ch)
                config = config.copy(left = left, top = top, right = left + cw, bottom = top + ch)
                render(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { persistAndRender(); return true }
        }
        return false
    }

    private var startRight = 0f; private var startBottom = 0f
    private fun onResizeTouch(e: MotionEvent): Boolean {
        val mask = maskView ?: return false
        val w = mask.width; val h = mask.height
        if (w == 0 || h == 0) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = e.rawX; startRawY = e.rawY
                startRight = config.right; startBottom = config.bottom; return true
            }
            MotionEvent.ACTION_MOVE -> {
                val right = (startRight + (e.rawX - startRawX) / w).coerceAtLeast(config.left + 0.06f).coerceAtMost(1f)
                val bottom = (startBottom + (e.rawY - startRawY) / h).coerceAtLeast(config.top + 0.06f).coerceAtMost(1f)
                config = config.copy(right = right, bottom = bottom)
                render(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { persistAndRender(); return true }
        }
        return false
    }

    private fun buildNotification(): Notification {
        val channelId = "smart_privacy_mask"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Privacy mask", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val conceal = android.app.PendingIntent.getService(
            this, 1, Intent(this, MaskService::class.java).setAction(ACTION_CONCEAL),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val reveal = android.app.PendingIntent.getService(
            this, 2, Intent(this, MaskService::class.java).setAction(ACTION_REVEAL),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val stop = android.app.PendingIntent.getService(
            this, 3, Intent(this, MaskService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Smart Privacy Screen")
            .setContentText("Viewing window active over your apps")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_view, "Conceal", conceal)
            .addAction(android.R.drawable.ic_menu_view, "Reveal", reveal)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .build()
    }

    private fun cleanup() {
        running = false
        listOf(maskView, captureView, moveView, resizeView).forEach { v ->
            if (v != null) runCatching { windowManager.removeView(v) }
        }
        maskView = null; captureView = null; moveView = null; resizeView = null
        captureParams = null; moveParams = null; resizeParams = null
        if (::windowManager.isInitialized) runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    override fun onDestroy() { cleanup(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "com.privacyhub.app.STOP_MASK"
        const val ACTION_CONCEAL = "com.privacyhub.app.CONCEAL"
        const val ACTION_REVEAL = "com.privacyhub.app.REVEAL"
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            context.startService(Intent(context, MaskService::class.java))
        }
        fun stop(context: Context) {
            context.startService(Intent(context, MaskService::class.java).setAction(ACTION_STOP))
        }
    }
}
