package com.privacyhub.app.detox

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.privacyhub.app.FocusMapApplication
import com.privacyhub.app.domain.detox.DetoxCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/** Inexact daily alarms; no exact-alarm permission requested. */
object DetoxScheduler {
    const val ACTION_START = "com.privacyhub.app.detox.SCHEDULE_START"
    const val ACTION_END = "com.privacyhub.app.detox.SCHEDULE_END"
    private fun pending(context: Context, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(context, (action + id).hashCode(),
            Intent(context, DetoxScheduleReceiver::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Recomputes one alarm pair per enabled schedule. Call after edits and boot. */
    fun scheduleAll(context: Context) {
        val repo = FocusMapApplication.instance.repository
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val am = context.getSystemService(AlarmManager::class.java) ?: return@launch
            repo.activeSchedules().forEach { s ->
                val days = s.weekdays.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
                fun at(minute: Int): Calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60)
                    set(Calendar.SECOND, 0); if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                // Pick the next matching weekday for each edge.
                var start = at(s.startMinute); var guard = 0
                while ((start.get(Calendar.DAY_OF_WEEK) + 5) % 7 !in days && guard++ < 8) start.add(Calendar.DAY_OF_YEAR, 1)
                var end = at(s.endMinute); guard = 0
                while (((end.get(Calendar.DAY_OF_WEEK) + 5) % 7 !in days || end.timeInMillis <= start.timeInMillis) && guard++ < 8)
                    end.add(Calendar.DAY_OF_YEAR, 1)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start.timeInMillis, pending(context, ACTION_START, s.id))
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end.timeInMillis, pending(context, ACTION_END, s.id))
            }
        }
    }

    fun cancelAll(context: Context) {
        val repo = FocusMapApplication.instance.repository
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val am = context.getSystemService(AlarmManager::class.java) ?: return@launch
            repo.observeSchedules().first().forEach { s ->
                am.cancel(pending(context, ACTION_START, s.id)); am.cancel(pending(context, ACTION_END, s.id))
            }
        }
    }
}

/** Optional DND: only applied with explicit notification-policy access. Restores the user's previous filter. */
object DndController {
    private var previousFilter: Int = -1
    fun canUse(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return !Build.VERSION_CODES.S.let { Build.VERSION.SDK_INT >= it } ||
            nm.isNotificationPolicyAccessGranted
    }

    fun applyAll(context: Context) {
        if (!canUse(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (previousFilter == -1) previousFilter = nm.currentInterruptionFilter
        runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
    }

    fun restore(context: Context) {
        if (!canUse(context) || previousFilter == -1) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.setInterruptionFilter(previousFilter) }; previousFilter = -1
    }
}

class DetoxScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val id = intent.getLongExtra("id", -1L)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = (context.applicationContext as FocusMapApplication).repository
                val s = repo.schedule(id) ?: return@launch
                when (intent.action) {
                    DetoxScheduler.ACTION_START -> {
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = System.currentTimeMillis()
                            set(Calendar.HOUR_OF_DAY, s.endMinute / 60); set(Calendar.MINUTE, s.endMinute % 60)
                            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                        }
                        repo.startScheduledDetox(s.id, s.profileId, cal.timeInMillis)
                    }
                    DetoxScheduler.ACTION_END -> {
                        val a = repo.currentActivations().firstOrNull { it.sourceType == "SCHEDULED" && it.sourceId == "schedule-${s.id}" }
                        if (a != null) repo.endManualDetox(a.id)
                    }
                }
                DetoxScheduler.scheduleAll(context)
            } catch (_: Exception) { } finally { pending.finish() }
        }
    }
}
