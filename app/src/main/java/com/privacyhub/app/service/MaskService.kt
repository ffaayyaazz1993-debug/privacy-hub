package com.privacyhub.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.privacyhub.app.MainActivity
import com.privacyhub.app.privacy.HandleKind
import com.privacyhub.app.privacy.HandleView
import com.privacyhub.app.privacy.MaskConfig
import com.privacyhub.app.privacy.MaskStore
import com.privacyhub.app.privacy.MaskView

/**
 * Draws the blackout mask over other apps and hosts the small touchable
 * move/resize handles. The main mask window is deliberately NOT_TOUCHABLE so
 * touches outside the handles still reach the underlying app.
 */
class MaskService : Service() {

    private var windowManager: WindowManager? = null
    private var maskView: MaskView? = null
    private var moveView: HandleView? = null
    private var resizeView: HandleView? = null
    private var moveParams: WindowManager.LayoutParams? = null
    private var resizeParams: WindowManager.LayoutParams? = null
    private var config: MaskConfig = MaskStore.DEFAULT
    private var running = false

    private val density: Float get() = resources.displayMetrics.density
    private val handleSizePx: Int get() = (56 * density).toInt().coerceAtLeast(40)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cleanup()
                stopSelf()
            }
            ACTION_UPDATE -> applyConfig()
            ACTION_CONCEAL -> applyConcealed()
            ACTION_REVEAL -> applyRevealed()
            else -> startMask()
        }
        return START_NOT_STICKY
    }

    private fun startMask() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                stopSelf()
                return
            }
            startForeground(NOTIFICATION_ID, buildNotification())
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            config = MaskStore.load(this)
            addMask()
            addHandles()
            positionHandles()
            running = true
            instance = this
            isRunning = true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Mask failed to start", e)
            cleanup()
            stopSelf()
        }
    }

    private fun addMask() {
        val view = MaskView(this)
        view.config = config
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
            alpha = 1f
        }
        windowManager?.addView(view, params)
        maskView = view
    }

    private fun addHandles() {
        val wm = windowManager ?: return

        val move = HandleView(this, kind = HandleKind.MOVE)
        move.setOnTouchListener { _, event -> onMoveTouch(event) }
        moveParams = handleParams(handleSizePx, -handleSizePx * 2, -handleSizePx * 2)
        wm.addView(move, moveParams)
        moveView = move

        val resize = HandleView(this, kind = HandleKind.RESIZE)
        resize.setOnTouchListener { _, event -> onResizeTouch(event) }
        resizeParams = handleParams(handleSizePx, -handleSizePx * 2, -handleSizePx * 2)
        wm.addView(resize, resizeParams)
        resizeView = resize
    }

    private fun handleParams(size: Int, x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun positionHandles() {
        val mv = maskView ?: return
        if (mv.width == 0 || mv.height == 0) {
            mv.post { positionHandles() }
            return
        }
        val rect = MaskStore.apertureRectF(mv.width, mv.height, config, density)
        val loc = IntArray(2)
        mv.getLocationOnScreen(loc)

        val moveSize = handleSizePx
        placeHandle(
            moveView,
            moveParams,
            loc[0] + rect.centerX().toInt() - moveSize / 2,
            loc[1] + rect.bottom.toInt() - moveSize / 2
        )
        placeHandle(
            resizeView,
            resizeParams,
            loc[0] + rect.right.toInt() - moveSize / 2,
            loc[1] + rect.bottom.toInt() - moveSize / 2
        )
    }

    private fun placeHandle(
        view: HandleView?,
        params: WindowManager.LayoutParams?,
        x: Int,
        y: Int
    ) {
        if (view == null || params == null) return
        if (params.x == x && params.y == y &&
            params.width == handleSizePx && params.height == handleSizePx
        ) return
        params.x = x
        params.y = y
        params.width = handleSizePx
        params.height = handleSizePx
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private var moveStartX = 0f
    private var moveStartY = 0f
    private var moveStartLeft = 0f
    private var moveStartTop = 0f

    private fun onMoveTouch(event: MotionEvent): Boolean {
        val mv = maskView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                moveStartX = event.rawX
                moveStartY = event.rawY
                moveStartLeft = config.left
                moveStartTop = config.top
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val w = config.right - config.left
                val h = config.bottom - config.top
                var left = moveStartLeft + (event.rawX - moveStartX) / mv.width
                var top = moveStartTop + (event.rawY - moveStartY) / mv.height
                left = left.coerceIn(0f, 1f - w)
                top = top.coerceIn(0f, 1f - h)
                setConfig(
                    config.copy(
                        left = left,
                        top = top,
                        right = left + w,
                        bottom = top + h
                    ),
                    save = false
                )
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                MaskStore.save(this, config)
                return true
            }
        }
        return false
    }

    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var resizeStartRight = 0f
    private var resizeStartBottom = 0f

    private fun onResizeTouch(event: MotionEvent): Boolean {
        val mv = maskView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resizeStartX = event.rawX
                resizeStartY = event.rawY
                resizeStartRight = config.right
                resizeStartBottom = config.bottom
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val right = (resizeStartRight + (event.rawX - resizeStartX) / mv.width)
                    .coerceAtLeast(config.left + MIN_SIZE)
                    .coerceAtMost(1f)
                val bottom = (resizeStartBottom + (event.rawY - resizeStartY) / mv.height)
                    .coerceAtLeast(config.top + MIN_SIZE)
                    .coerceAtMost(1f)
                setConfig(
                    config.copy(right = right, bottom = bottom),
                    save = false
                )
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                MaskStore.save(this, config)
                return true
            }
        }
        return false
    }

    private fun setConfig(next: MaskConfig, save: Boolean) {
        config = MaskStore.normalize(next)
        if (save) MaskStore.save(this, config)
        applyConfig()
    }

    private fun applyConfig() {
        config = MaskStore.load(this)
        maskView?.config = config
        maskView?.invalidate()
        positionHandles()
    }

    private fun applyConcealed() {
        setConfig(MaskStore.CONCEALED, save = true)
    }

    private fun applyRevealed() {
        setConfig(MaskStore.DEFAULT, save = true)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Privacy mask",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the smart privacy mask running"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MaskService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val conceal = PendingIntent.getService(
            this,
            2,
            Intent(this, MaskService::class.java).setAction(ACTION_CONCEAL),
            PendingIntent.FLAG_IMMUTABLE
        )
        val reveal = PendingIntent.getService(
            this,
            3,
            Intent(this, MaskService::class.java).setAction(ACTION_REVEAL),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Privacy Screen is active")
            .setContentText("Blackout mask is covering other apps")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .addAction(0, "Conceal", conceal)
            .addAction(0, "Reveal", reveal)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun cleanup() {
        running = false
        moveView?.let { runCatching { windowManager?.removeView(it) } }
        resizeView?.let { runCatching { windowManager?.removeView(it) } }
        maskView?.let { runCatching { windowManager?.removeView(it) } }
        moveView = null
        resizeView = null
        maskView = null
        instance = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.privacyhub.app.action.START"
        const val ACTION_STOP = "com.privacyhub.app.action.STOP"
        const val ACTION_UPDATE = "com.privacyhub.app.action.UPDATE"
        const val ACTION_CONCEAL = "com.privacyhub.app.action.CONCEAL"
        const val ACTION_REVEAL = "com.privacyhub.app.action.REVEAL"

        private const val CHANNEL_ID = "smart_privacy_mask"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "SmartPrivacy"
        private const val MIN_SIZE = 0.06f

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var instance: MaskService? = null

        fun start(context: Context) {
            if (isRunning) return
            context.startForegroundService(
                Intent(context, MaskService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MaskService::class.java).setAction(ACTION_STOP)
            )
        }

        fun update(context: Context) {
            val service = instance
            if (service != null) {
                service.applyConfig()
            } else {
                context.startService(
                    Intent(context, MaskService::class.java).setAction(ACTION_UPDATE)
                )
            }
        }

        fun conceal(context: Context) {
            val service = instance
            if (service != null) {
                service.applyConcealed()
            } else {
                MaskStore.save(context, MaskStore.CONCEALED)
            }
        }

        fun reveal(context: Context) {
            val service = instance
            if (service != null) {
                service.applyRevealed()
            } else {
                MaskStore.save(context, MaskStore.DEFAULT)
            }
        }
    }
}
