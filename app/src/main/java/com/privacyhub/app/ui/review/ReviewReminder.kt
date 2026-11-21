package com.privacyhub.app.ui.review

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.privacyhub.app.FocusMapApplication
import com.privacyhub.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

object ReviewReminder {
    private const val CHANNEL = "review_reminder"
    private const val REQUEST = 9001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Review reminders",
                NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** One exact-time daily alarm, replaced on every save — never a stacking reminder. */
    fun schedule(context: Context, hour: Int) {
        ensureChannel(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(context, REQUEST, Intent(context, ReviewReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23)); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(context, REQUEST, Intent(context, ReviewReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
    }
}

class ReviewReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = (context.applicationContext as FocusMapApplication).repository
                val cfg = repo.schedulerSettings.get()
                if (cfg?.reminderEnabled == true) {
                    ReviewReminder.schedule(context, cfg.preferredReviewHour)
                    val due = repo.cards.due(System.currentTimeMillis()).size
                    if (due > 0 && ReviewReminder.canPost(context) &&
                        NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        ReviewReminder.ensureChannel(context)
                        val open = PendingIntent.getActivity(context, 9002,
                            Intent(context, MainActivity::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        NotificationCompat.Builder(context, "review_reminder")
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle("FocusMap review")
                            .setContentText("$due card(s) due today")
                            .setContentIntent(open)
                            .setAutoCancel(true)
                            .build().let { NotificationManagerCompat.from(context).notify(9003, it) }
                    }
                }
            } catch (_: Exception) { } finally { pending.finish() }
        }
    }
}
