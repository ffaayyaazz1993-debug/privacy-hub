package com.privacyhub.app.domain.pomodoro

import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.domain.model.SessionStatus

/** Timestamp-based timer math: accurate across background / process death. */
object Pomodoro {
    fun elapsedSeconds(s: FocusSessionEntity, now: Long): Long {
        val running = if (s.status == SessionStatus.RUNNING && s.resumeTimestamp != null)
            (now - s.resumeTimestamp) / 1000 else 0L
        return (s.accumulatedSeconds + running).coerceAtLeast(0)
    }
    fun remainingSeconds(s: FocusSessionEntity, now: Long): Long =
        (s.plannedDurationSeconds - elapsedSeconds(s, now)).coerceAtLeast(0)
    fun progress(s: FocusSessionEntity, now: Long): Float {
        if (s.plannedDurationSeconds <= 0) return 0f
        return (elapsedSeconds(s, now).toFloat() / s.plannedDurationSeconds).coerceIn(0f, 1f)
    }
}
