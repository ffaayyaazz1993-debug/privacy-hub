package com.privacyhub.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.privacyhub.app.privacy.HandleKind
import com.privacyhub.app.privacy.HandleView
import com.privacyhub.app.privacy.MaskConfig
import com.privacyhub.app.privacy.MaskStore
import com.privacyhub.app.privacy.MaskView

/**
 * System-wide blackout mask with a viewing window.
 *
 * The black area is built from FOUR separate NOT_TOUCHABLE strip windows placed
 * AROUND the aperture (top / bottom / left / right). The aperture itself has NO
 * overlay window over it at all, so touches there land directly on the app
 * underneath. This avoids Android's obscured-touch (tapjacking) rejection that a
 * full-screen overlay (even NOT_TOUCHABLE) triggers on real devices: any overlay
 * covering the target makes the target drop the touch. Leaving the aperture
 * uncovered is what lets the user actually interact with the app.
 */
class MaskService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var topStrip: View? = null
    private var bottomStrip: View? = null
    private var leftStrip: View? = null
    private var rightStrip: View? = null
    private var moveView: HandleView? = null
    private var resizeView: HandleView? = null

    private var topParams: WindowManager.LayoutParams? = null
    private var bottomParams: WindowManager.LayoutParams? = null
    private var leftParams: WindowManager.LayoutParams? = null
    private var rightParams: WindowManager.LayoutParams? = null
    private var moveParams: WindowManager.LayoutParams? = null
    private var resizeParams: WindowManager.LayoutParams? = null
    private var dragMask: MaskView? = null

    private var config: MaskConfig = MaskStore.DEFAULT
    private var running = false

    private val touchSlop: Int get() = ViewConfiguration.get(this).scaledTouchSlop

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
        if (running) { config = MaskStore.load(this); positionAll(); return }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        config = MaskStore.load(this)
        try {
            startForeground(NOTIF_ID, buildNotification())
            addStrips()
            addHandles()
            positionAll()
            running = true
        } catch (_: Throwable) {
            cleanup(); stopSelf()
        }
    }

    private fun screenSize(): Point {
        val p = Point()
        if (Build.VERSION.SDK_INT >= 30) {
            val b = windowManager.maximumWindowMetrics.bounds
            p.set(b.width(), b.height())
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(p)
        }
        return p
    }

    private fun stripColor(): Int =
        Color.argb((config.dimAlpha * 255).toInt().coerceIn(0, 255), 0, 0, 0)

    private fun newStrip(): View = View(this).apply {
        setBackgroundColor(stripColor())
        isClickable = false
        isFocusable = false
    }

    private fun stripParams() = WindowManager.LayoutParams(
        1, 1,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; windowAnimations = 0 }

    private fun addStrips() {
        if (topStrip != null) return
        topStrip = newStrip(); bottomStrip = newStrip()
        leftStrip = newStrip(); rightStrip = newStrip()
        topParams = stripParams(); bottomParams = stripParams()
        leftParams = stripParams(); rightParams = stripParams()
        windowManager.addView(topStrip, topParams)
        windowManager.addView(bottomStrip, bottomParams)
        windowManager.addView(leftStrip, leftParams)
        windowManager.addView(rightStrip, rightParams)
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
    ).apply { gravity = Gravity.TOP or Gravity.START; windowAnimations = 0 }

    private fun place(view: View?, p: WindowManager.LayoutParams?, x: Int, y: Int, w: Int, h: Int) {
        if (view == null || p == null) return
        p.x = x; p.y = y
        p.width = w.coerceAtLeast(0); p.height = h.coerceAtLeast(0)
        // A zero-size strip would be invisible; keep it but it draws nothing.
        runCatching { windowManager.updateViewLayout(view, p) }
    }

    private fun positionAll() {
        if (topStrip == null) return
        val screen = screenSize()
        val sw = screen.x; val sh = screen.y
        if (sw == 0 || sh == 0) { handler.post { positionAll() }; return }
        val r = MaskStore.apertureRectF(sw, sh, config, resources.displayMetrics.density)
        val l = r.left.toInt(); val t = r.top.toInt()
        val rr = r.right.toInt(); val b = r.bottom.toInt()
        val col = stripColor()
        topStrip?.setBackgroundColor(col); bottomStrip?.setBackgroundColor(col)
        leftStrip?.setBackgroundColor(col); rightStrip?.setBackgroundColor(col)
        // Four strips around the aperture; the aperture region stays uncovered.
        place(topStrip, topParams, 0, 0, sw, t)
        place(bottomStrip, bottomParams, 0, b, sw, sh - b)
        place(leftStrip, leftParams, 0, t, l, b - t)
        place(rightStrip, rightParams, rr, t, sw - rr, b - t)
        val size = handleSizePx
        place(moveView, moveParams, (r.centerX() - size / 2f).toInt(), (b - size / 2f).toInt(), size, size)
        place(resizeView, resizeParams, (rr - size / 2f).toInt(), (b - size / 2f).toInt(), size, size)
    }

    private fun persistAndRender() {
        config = MaskStore.normalize(config)
        MaskStore.save(this, config)
        positionAll()
    }

    // ---- smooth drag: one full-screen repaint instead of 6 window repositions ----
    private fun beginDrag() {
        if (dragMask != null) return
        val dm = object : MaskView(this) {
            private val hFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 16, 32, 56) }
            private val hStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(220, 255, 255, 255)
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (width == 0 || height == 0) return
                val r = MaskStore.apertureRectF(width, height, config, resources.displayMetrics.density)
                val hs = handleSizePx.toFloat()
                drawMove(canvas, r.centerX(), r.bottom, hs)
                drawResize(canvas, r.right, r.bottom, hs)
            }
            private fun drawMove(c: Canvas, cx: Float, cy: Float, s: Float) {
                val rad = s / 2f - 3f
                c.drawCircle(cx, cy, rad, hFill); c.drawCircle(cx, cy, rad, hStroke)
                c.drawLine(cx - rad * 0.45f, cy, cx + rad * 0.45f, cy, hStroke)
                c.drawLine(cx, cy - rad * 0.45f, cx, cy + rad * 0.45f, hStroke)
            }
            private fun drawResize(c: Canvas, cx: Float, cy: Float, s: Float) {
                val rf = android.graphics.RectF(cx - s / 2f + 3f, cy - s / 2f + 3f, cx + s / 2f - 3f, cy + s / 2f - 3f)
                c.drawRect(rf, hFill); c.drawRect(rf, hStroke)
                c.drawLine(rf.left + rf.width() * 0.25f, rf.top + rf.height() * 0.75f,
                    rf.right - rf.width() * 0.15f, rf.top + rf.height() * 0.25f, hStroke)
            }
        }
        // Match the resting strip mask exactly: sharp corners, no border. (The
        // resting aperture is the rectangular gap between strips; a rounded drag
        // preview would visibly morph chamfered<->sharp on tap/release.)
        dm.showBorder = false
        dm.config = config.copy(cornerRadiusDp = 0f)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; windowAnimations = 0 }
        dragMask = dm
        runCatching { windowManager.addView(dm, p) }
        // Hide the strips in the SAME frame the drag mask first draws, so there is
        // never a frame with no mask composited (that gap was the flicker).
        dm.onceOnPreDraw {
            topStrip?.visibility = View.GONE; bottomStrip?.visibility = View.GONE
            leftStrip?.visibility = View.GONE; rightStrip?.visibility = View.GONE
            moveView?.alpha = 0f; resizeView?.alpha = 0f
        }
    }

    private fun updateDrag() {
        dragMask?.config = config
    }

    private fun endDrag() {
        val dm = dragMask ?: return
        dragMask = null
        // Restore + reposition the strips under the still-present drag mask, then
        // remove the drag mask only AFTER the strips have drawn (no unmasked frame).
        topStrip?.visibility = View.VISIBLE; bottomStrip?.visibility = View.VISIBLE
        leftStrip?.visibility = View.VISIBLE; rightStrip?.visibility = View.VISIBLE
        moveView?.alpha = 1f; resizeView?.alpha = 1f
        persistAndRender()
        val ts = topStrip
        if (ts != null) {
            ts.onceOnPreDraw { handler.post { runCatching { windowManager.removeView(dm) } } }
        } else {
            runCatching { windowManager.removeView(dm) }
        }
    }

    // ---- aperture move / resize ----
    private var startRawX = 0f; private var startRawY = 0f
    private var startLeft = 0f; private var startTop = 0f

    private fun onMoveTouch(e: MotionEvent): Boolean {
        val screen = screenSize(); val w = screen.x; val h = screen.y
        if (w == 0 || h == 0) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = e.rawX; startRawY = e.rawY
                startLeft = config.left; startTop = config.top; return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - startRawX; val dy = e.rawY - startRawY
                if (dragMask == null && dx * dx + dy * dy < touchSlop * touchSlop) return true
                val cw = config.right - config.left; val ch = config.bottom - config.top
                val left = (startLeft + dx / w).coerceIn(0f, 1f - cw)
                val top = (startTop + dy / h).coerceIn(0f, 1f - ch)
                config = config.copy(left = left, top = top, right = left + cw, bottom = top + ch)
                if (dragMask == null) beginDrag()
                updateDrag(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (dragMask != null) endDrag(); return true }
        }
        return false
    }

    private var startRight = 0f; private var startBottom = 0f
    private fun onResizeTouch(e: MotionEvent): Boolean {
        val screen = screenSize(); val w = screen.x; val h = screen.y
        if (w == 0 || h == 0) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = e.rawX; startRawY = e.rawY
                startRight = config.right; startBottom = config.bottom; return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - startRawX; val dy = e.rawY - startRawY
                if (dragMask == null && dx * dx + dy * dy < touchSlop * touchSlop) return true
                val right = (startRight + dx / w).coerceAtLeast(config.left + 0.06f).coerceAtMost(1f)
                val bottom = (startBottom + dy / h).coerceAtLeast(config.top + 0.06f).coerceAtMost(1f)
                config = config.copy(right = right, bottom = bottom)
                if (dragMask == null) beginDrag()
                updateDrag(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (dragMask != null) endDrag(); return true }
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
        listOf(topStrip, bottomStrip, leftStrip, rightStrip, moveView, resizeView, dragMask).forEach { v ->
            if (v != null) runCatching { windowManager.removeView(v) }
        }
        topStrip = null; bottomStrip = null; leftStrip = null; rightStrip = null
        moveView = null; resizeView = null; dragMask = null
        topParams = null; bottomParams = null; leftParams = null; rightParams = null
        moveParams = null; resizeParams = null
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


/** Run [action] once, right before the next draw of this view (then detach). */
private inline fun View.onceOnPreDraw(crossinline action: () -> Unit) {
    viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            action()
            return true
        }
    })
}
