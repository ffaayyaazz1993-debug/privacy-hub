package com.privacyhub.app.util

import java.util.Calendar
import java.util.concurrent.TimeUnit

fun startOfDay(ts: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return c.timeInMillis
}
fun startOfWeek(ts: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return c.timeInMillis
}
/** 90 -> "1m 30s"; 3675 -> "1h 01m". */
fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = TimeUnit.SECONDS.toHours(s)
    val m = TimeUnit.SECONDS.toMinutes(s) % 60
    val sec = s % 60
    return when {
        h > 0 -> "%dh %02dm".format(h, m)
        m > 0 -> "%dm %02ds".format(m, sec)
        else -> "%ds".format(sec)
    }
}
/** Countdown mm:ss (or h:mm:ss). */
fun formatClock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
