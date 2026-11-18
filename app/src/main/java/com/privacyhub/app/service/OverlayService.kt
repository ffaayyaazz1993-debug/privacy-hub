package com.privacyhub.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.privacyhub.app.MainActivity
import com.privacyhub.app.privacy.TiltMath

class OverlayService : Service(), SensorEventListener {

    private var windowManager: WindowManager? = null
    private var dimView: View? = null
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    private var lastAngleDeg = 0.0
    private var currentAlpha = 0f
    private var running = false

    private val handler = Handler(Looper.getMainLooper())
    private val fadeTick = object : Runnable {
        override fun run() {
            updateAlpha()
            if (running) handler.postDelayed(this, 24L)
        }
    }

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
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                updateAlpha()
                return START_STICKY
            }
            else -> startPrivacyScreen()
        }
        return START_STICKY
    }

    private fun startPrivacyScreen() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        addOverlayView()
        registerSensor()
        running = true
        handler.removeCallbacks(fadeTick)
        handler.post(fadeTick)
        instance = this
        isRunning = true
    }

    private fun addOverlayView() {
        if (dimView != null) return
        val view = View(this)
        view.setBackgroundColor(Color.BLACK)
        view.alpha = 0f
        view.isClickable = false
        view.isFocusable = false
        view.isLongClickable = false

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            // Window-level alpha stays 1; the black view's alpha is animated.
            alpha = 1f
        }

        windowManager?.addView(view, params)
        dimView = view
    }

    private fun registerSensor() {
        val manager = sensorManager ?: return
        val sensor = rotationSensor ?: return
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
        lastAngleDeg = TiltMath.lateralAngleDeg(event.values)
        updateAlpha()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateAlpha() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val neutral = prefs.getFloat(KEY_NEUTRAL, 0f).toDouble()
        val sensitivity = prefs.getFloat(KEY_SENSITIVITY, 1.0f).toDouble()
        val threshold = prefs.getFloat(KEY_THRESHOLD, 10.0f).toDouble()

        val angle = TiltMath.simulatedAngleDeg ?: lastAngleDeg
        val target = TiltMath.alphaFor(
            angleDeg = angle,
            neutralDeg = neutral,
            sensitivity = sensitivity,
            thresholdDeg = threshold
        ).toFloat()

        // Time-ish smoothing: sensor events arrive often enough for this to be
        // stable without a separate animator.
        currentAlpha += (target - currentAlpha) * 0.22f
        if (kotlin.math.abs(target - currentAlpha) < 0.002f) currentAlpha = target
        dimView?.alpha = currentAlpha
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Privacy screen",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the anti-peek overlay running"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
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
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Privacy Hub is active")
            .setContentText("Anti-peek dimming is running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun cleanup() {
        running = false
        handler.removeCallbacks(fadeTick)
        sensorManager?.unregisterListener(this)
        dimView?.let { runCatching { windowManager?.removeView(it) } }
        dimView = null
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
        const val ACTION_REFRESH = "com.privacyhub.app.action.REFRESH"
        const val PREFS = "privacy_hub_prefs"
        const val KEY_NEUTRAL = "neutral"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_THRESHOLD = "threshold"
        private const val CHANNEL_ID = "privacy_screen"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var instance: OverlayService? = null

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            )
        }

        fun refresh(context: Context) {
            val service = instance
            if (service != null) {
                service.updateAlpha()
            } else {
                context.startService(
                    Intent(context, OverlayService::class.java).setAction(ACTION_REFRESH)
                )
            }
        }
    }
}
