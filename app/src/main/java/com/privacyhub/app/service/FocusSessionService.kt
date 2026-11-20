package com.privacyhub.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.privacyhub.app.FocusMapApplication
import com.privacyhub.app.MainActivity
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.domain.model.SessionType
import com.privacyhub.app.domain.pomodoro.FocusSessionManager
import com.privacyhub.app.domain.pomodoro.Pomodoro
import com.privacyhub.app.util.formatClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the active focus/break session alive, fires completion at the expected
 * end timestamp, and shows a countdown notification. All state lives in Room;
 * on process death START_STICKY + timestamp recalculation restores it.
 */
class FocusSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val repo get() = FocusMapApplication.instance.repository
    private val manager by lazy { FocusSessionManager(repo) }
    private var currentId = -1L
    private var completeRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_SYNC
        scope.launch { handle(action) }
        return START_STICKY
    }

    private suspend fun handle(action: String) {
        val s = manager.active()
        when (action) {
            ACTION_PAUSE -> s?.let { manager.pause(it) }
            ACTION_RESUME -> s?.let { manager.resume(it) }
            ACTION_RESTART -> s?.let { manager.restart(it) }
            ACTION_SKIP -> s?.let { manager.skip(it) }
            ACTION_COMPLETE -> s?.let { manager.complete(it, ranToEnd = false); afterComplete(it); return }
            ACTION_STOP -> { s?.let { manager.stop(it) }; shutdown(); return }
        }
        sync()
    }

    private suspend fun sync() {
        val s = manager.active()
        if (s == null) { shutdown(); return }
        currentId = s.id
        startForeground(NOTIF_ID, buildNotification(s))
        cancelCompletion()
        if (s.status == SessionStatus.RUNNING) {
            val rem = Pomodoro.remainingSeconds(s, System.currentTimeMillis())
            if (rem <= 0) onTimerEnd() else scheduleCompletion(rem)
        }
    }

    private fun scheduleCompletion(remSec: Long) {
        val r = Runnable { scope.launch { onTimerEnd() } }
        completeRunnable = r
        handler.postDelayed(r, remSec * 1000)
    }
    private fun cancelCompletion() { completeRunnable?.let { handler.removeCallbacks(it) }; completeRunnable = null }

    private suspend fun onTimerEnd() {
        val s = repo.sessions.get(currentId) ?: return
        if (s.status != SessionStatus.RUNNING) return
        val rem = Pomodoro.remainingSeconds(s, System.currentTimeMillis())
        if (rem > 0) { scheduleCompletion(rem); return }
        manager.complete(s, ranToEnd = true)
        afterComplete(s)
    }

    private suspend fun afterComplete(s: FocusSessionEntity) {
        notifyCompletion(s)
        if (s.type == SessionType.FOCUS) { manager.startFollowingBreak(s); sync() } else shutdown()
    }

    private fun shutdown() {
        cancelCompletion()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun buildNotification(s: FocusSessionEntity): Notification {
        val node = repo.nodes.get(s.nodeId)
        val project = repo.projects.get(s.projectId)
        val kind = when (s.type) { SessionType.FOCUS -> "Focus"; SessionType.SHORT_BREAK -> "Short break"; SessionType.LONG_BREAK -> "Long break" }
        val title = if (s.type == SessionType.FOCUS) "$kind · ${node?.title ?: ""}" else kind
        val now = System.currentTimeMillis()
        val rem = Pomodoro.remainingSeconds(s, now)
        val builder = Notification.Builder(this, FocusMapApplication.CHANNEL_FOCUS)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(project?.title ?: "")
            .setOngoing(true)
            .setContentIntent(activityIntent())
        if (s.status == SessionStatus.RUNNING) {
            builder.setWhen(now + rem * 1000).setUsesChronometer(true)
            if (Build.VERSION.SDK_INT >= 24) builder.setChronometerCountDown(true)
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", actionIntent(ACTION_PAUSE))
        } else {
            builder.setContentText("Paused · ${formatClock(rem)} left · ${project?.title ?: ""}")
            builder.addAction(android.R.drawable.ic_media_play, "Resume", actionIntent(ACTION_RESUME))
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", actionIntent(ACTION_STOP))
        return builder.build()
    }

    private fun notifyCompletion(s: FocusSessionEntity) {
        val kind = if (s.type == SessionType.FOCUS) "Focus session complete" else "Break over"
        val n = Notification.Builder(this, FocusMapApplication.CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(kind)
            .setContentText(if (s.type == SessionType.FOCUS) "Nice work. Take a break." else "Ready for the next focus session.")
            .setContentIntent(activityIntent())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID + 1, n)
    }

    private fun actionIntent(action: String): PendingIntent =
        PendingIntent.getService(this, action.hashCode(), Intent(this, FocusSessionService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun activityIntent(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    override fun onDestroy() { scope.cancel(); cancelCompletion(); super.onDestroy() }

    companion object {
        const val ACTION_SYNC = "com.privacyhub.app.focus.SYNC"
        const val ACTION_PAUSE = "com.privacyhub.app.focus.PAUSE"
        const val ACTION_RESUME = "com.privacyhub.app.focus.RESUME"
        const val ACTION_RESTART = "com.privacyhub.app.focus.RESTART"
        const val ACTION_SKIP = "com.privacyhub.app.focus.SKIP"
        const val ACTION_COMPLETE = "com.privacyhub.app.focus.COMPLETE"
        const val ACTION_STOP = "com.privacyhub.app.focus.STOP"
        private const val NOTIF_ID = 43

        fun send(context: Context, action: String) {
            context.startService(Intent(context, FocusSessionService::class.java).setAction(action))
        }
        fun start(context: Context) = send(context, ACTION_SYNC)
    }
}
