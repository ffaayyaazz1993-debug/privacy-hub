package com.privacyhub.app

import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.domain.pomodoro.Pomodoro
import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroTest {
    private fun session(status: SessionStatus, planned: Int, accumulated: Long, resumeTs: Long?) =
        FocusSessionEntity(projectId = 1, nodeId = 1, startTimestamp = 0, plannedDurationSeconds = planned,
            status = status, accumulatedSeconds = accumulated, resumeTimestamp = resumeTs)

    @Test fun runningRemainingCountsCurrentSegment() {
        val now = 100_000L
        val s = session(SessionStatus.RUNNING, 1500, 0, now - 60_000)
        assertEquals(1440L, Pomodoro.remainingSeconds(s, now))
    }
    @Test fun pausedRemainingIgnoresWallClock() {
        val s = session(SessionStatus.PAUSED, 1500, 300, null)
        assertEquals(1200L, Pomodoro.remainingSeconds(s, 999_999_999L))
    }
    @Test fun runningWithBankedTime() {
        val now = 200_000L
        val s = session(SessionStatus.RUNNING, 1500, 300, now - 120_000)
        assertEquals(1080L, Pomodoro.remainingSeconds(s, now))
    }
    @Test fun remainingClampsAtZero() {
        val s = session(SessionStatus.RUNNING, 10, 0, 0)
        assertEquals(0L, Pomodoro.remainingSeconds(s, 999_999L))
    }
    @Test fun progressHalfway() {
        val now = 100_000L
        val s = session(SessionStatus.RUNNING, 100, 0, now - 50_000)
        assertEquals(0.5f, Pomodoro.progress(s, now), 0.001f)
    }
    @Test fun elapsedAcrossProcessDeath() {
        // resumeTimestamp far in the past: elapsed must still be computed from timestamps
        val s = session(SessionStatus.RUNNING, 1500, 100, 1_000L)
        assertEquals(100L + (500_000L - 1_000L) / 1000, Pomodoro.elapsedSeconds(s, 500_000L))
    }
}
