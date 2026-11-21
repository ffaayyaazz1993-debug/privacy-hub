package com.privacyhub.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.privacyhub.app.FocusMapApplication
import com.privacyhub.app.MainActivity
import com.privacyhub.app.R
import com.privacyhub.app.domain.model.SessionType
import com.privacyhub.app.domain.pomodoro.FocusSessionManager
import com.privacyhub.app.domain.pomodoro.Pomodoro
import com.privacyhub.app.domain.spaced.ReviewScheduler
import com.privacyhub.app.service.FocusSessionService
import com.privacyhub.app.util.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseWidgetProvider(private val kind: String) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob()).launch {
            try { ids.forEach { updateOne(context, manager, it) } } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_START || action == ACTION_PAUSE || action == ACTION_RESUME) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob()).launch {
                try {
                    val repo = (context.applicationContext as FocusMapApplication).repository
                    val m = FocusSessionManager(repo)
                    val s = m.active()
                    when (action) {
                        ACTION_START -> if (s == null) {
                            val node = repo.nodes.observeRecent().firstOrNull()
                            if (node != null) {
                                val cfg = repo.settings.get()
                                m.start(node.projectId, node.id, SessionType.FOCUS, cfg?.focusDurationSeconds ?: 1500)
                            }
                        }
                        ACTION_PAUSE -> s?.let { m.pause(it) }
                        ACTION_RESUME -> s?.let { m.resume(it) }
                    }
                    try { FocusSessionService.start(context) } catch (_: Exception) { }
                } catch (_: Exception) { } finally {
                    refresh(context, kind); pending.finish()
                }
            }
        }
    }

    private suspend fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
        val repo = (context.applicationContext as FocusMapApplication).repository
        val data = withContext(Dispatchers.IO) {
            val s = repo.sessions.getActive()
            val node = s?.let { repo.nodes.get(it.nodeId) }
            val project = s?.let { repo.projects.get(it.projectId) }
            val now = System.currentTimeMillis()
            val day = now - now % ReviewScheduler.DAY_MS
            val todayFocus = repo.sessions.focusSecondsBetween(day, now).firstOrNull() ?: 0L
            val todayCount = repo.sessions.completedFocusCount()
            val planned = repo.planned.observeBetween(now, now + ReviewScheduler.DAY_MS).firstOrNull().orEmpty()
            val due = repo.cards.due(now).size
            WidgetData(node?.title ?: "No topic", project?.title ?: "No project",
                if (s != null) formatDuration(Pomodoro.remainingSeconds(s, now) * 1000) else "25:00 ready",
                if (s != null) "Status: ${s.status.name}" else "Open a mind-map node to start",
                todayFocus, todayCount, planned.size, due)
        }
        val views = RemoteViews(context.packageName, R.layout.widget)
        views.setTextViewText(R.id.widget_title, when (kind) {
            "focus" -> "Focus timer"; "plan" -> "Today's study plan"; else -> "Daily progress" })
        views.setTextViewText(R.id.widget_sub, "${data.project} · ${data.node}")
        views.setTextViewText(R.id.widget_line3, when (kind) {
            "focus" -> data.time
            "plan" -> "${data.planned} planned block(s) today · ${data.due} reviews due"
            else -> "Focus ${formatDuration(data.focusSeconds)} · ${data.sessions} sessions"
        })
        views.setTextViewText(R.id.widget_line4, data.status)
        views.setOnClickPendingIntent(R.id.widget_start, actionPending(context, ACTION_START))
        views.setOnClickPendingIntent(R.id.widget_pause, actionPending(context, ACTION_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_resume, actionPending(context, ACTION_RESUME))
        views.setOnClickPendingIntent(R.id.widget_open, PendingIntent.getActivity(context, 41,
            Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        manager.updateAppWidget(id, views)
    }

    private data class WidgetData(val node: String, val project: String, val time: String,
                                  val status: String, val focusSeconds: Long, val sessions: Int,
                                  val planned: Int, val due: Int)

    companion object {
        const val ACTION_START = "com.privacyhub.app.widget.START"
        const val ACTION_PAUSE = "com.privacyhub.app.widget.PAUSE"
        const val ACTION_RESUME = "com.privacyhub.app.widget.RESUME"

        fun actionPending(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(context, action.hashCode(),
                Intent(context, FocusTimerWidget::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun refresh(context: Context, target: String) {
            val mgr = AppWidgetManager.getInstance(context)
            listOf(FocusTimerWidget::class.java to "focus", StudyPlanWidget::class.java to "plan",
                DailyProgressWidget::class.java to "progress").forEach { (cls, k) ->
                val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
                if (ids.isEmpty() || (target != "all" && target != k)) return@forEach
                val provider = when (cls) {
                    FocusTimerWidget::class.java -> FocusTimerWidget()
                    StudyPlanWidget::class.java -> StudyPlanWidget()
                    else -> DailyProgressWidget()
                }
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    ids.forEach { try { provider.updateOne(context, mgr, it) } catch (_: Exception) { } }
                }
            }
        }
    }
}

class FocusTimerWidget : BaseWidgetProvider("focus")
class StudyPlanWidget : BaseWidgetProvider("plan")
class DailyProgressWidget : BaseWidgetProvider("progress")
