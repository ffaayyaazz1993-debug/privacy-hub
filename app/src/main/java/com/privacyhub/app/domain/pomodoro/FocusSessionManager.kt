package com.privacyhub.app.domain.pomodoro

import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import com.privacyhub.app.domain.model.NodeStatus
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.domain.model.SessionType

/** All timer state transitions. The active session lives in Room, not memory. */
class FocusSessionManager(private val repo: FocusMapRepository) {

    suspend fun active(): FocusSessionEntity? = repo.sessions.getActive()

    suspend fun start(projectId: Long, nodeId: Long, type: SessionType, plannedSeconds: Int): Long {
        active()?.let { stop(it) }
        val now = System.currentTimeMillis()
        return repo.sessions.insert(
            FocusSessionEntity(
                projectId = projectId, nodeId = nodeId, type = type,
                startTimestamp = now, plannedDurationSeconds = plannedSeconds,
                status = SessionStatus.RUNNING, accumulatedSeconds = 0, resumeTimestamp = now,
            )
        )
    }

    suspend fun pause(s: FocusSessionEntity) {
        if (s.status != SessionStatus.RUNNING) return
        val now = System.currentTimeMillis()
        val banked = Pomodoro.elapsedSeconds(s, now)
        repo.sessions.update(s.copy(status = SessionStatus.PAUSED, accumulatedSeconds = banked, resumeTimestamp = null))
    }

    suspend fun resume(s: FocusSessionEntity) {
        if (s.status != SessionStatus.PAUSED) return
        repo.sessions.update(s.copy(status = SessionStatus.RUNNING, resumeTimestamp = System.currentTimeMillis()))
    }

    suspend fun restart(s: FocusSessionEntity) {
        val now = System.currentTimeMillis()
        repo.sessions.update(s.copy(status = SessionStatus.RUNNING, accumulatedSeconds = 0, resumeTimestamp = now, startTimestamp = now, endTimestamp = null))
    }

    /** Abandon: record partial time invested, but do not count a completed session. */
    suspend fun stop(s: FocusSessionEntity) {
        if (s.status == SessionStatus.COMPLETED || s.status == SessionStatus.STOPPED) return
        val now = System.currentTimeMillis()
        val actual = Pomodoro.elapsedSeconds(s, now).toInt()
        repo.sessions.update(s.copy(status = SessionStatus.STOPPED, endTimestamp = now, actualDurationSeconds = actual, resumeTimestamp = null))
        if (s.type == SessionType.FOCUS && actual > 0) recordPartialFocus(s.nodeId, actual.toLong())
    }

    suspend fun skip(s: FocusSessionEntity) {
        val now = System.currentTimeMillis()
        repo.sessions.update(s.copy(status = SessionStatus.SKIPPED, endTimestamp = now, actualDurationSeconds = Pomodoro.elapsedSeconds(s, now).toInt(), resumeTimestamp = null))
    }

    /** Finish: ranToEnd=true means the countdown reached zero (actual = planned). */
    suspend fun complete(s: FocusSessionEntity, ranToEnd: Boolean) {
        if (s.status == SessionStatus.COMPLETED) return
        val now = System.currentTimeMillis()
        val actual = if (ranToEnd) s.plannedDurationSeconds else Pomodoro.elapsedSeconds(s, now).toInt()
        val finished = s.copy(status = SessionStatus.COMPLETED, endTimestamp = now, actualDurationSeconds = actual, accumulatedSeconds = actual.toLong(), resumeTimestamp = null)
        if (s.type == SessionType.FOCUS) repo.completeFocusSession(finished) else repo.sessions.update(finished)
    }

    private suspend fun recordPartialFocus(nodeId: Long, seconds: Long) {
        val node = repo.nodes.get(nodeId) ?: return
        val status = if (node.status == NodeStatus.NOT_STARTED) NodeStatus.IN_PROGRESS else node.status
        repo.nodes.updateStats(node.id, node.completedSessions, node.totalFocusSeconds + seconds, status.name, System.currentTimeMillis())
    }

    /** Decide and start the break that follows a completed focus session. */
    suspend fun startFollowingBreak(focus: FocusSessionEntity) {
        val settings = repo.settings.get() ?: com.privacyhub.app.data.db.UserSettingsEntity()
        val count = repo.sessions.completedFocusCount()
        val long = settings.longBreakInterval > 0 && count % settings.longBreakInterval == 0
        val type = if (long) SessionType.LONG_BREAK else SessionType.SHORT_BREAK
        val dur = if (long) settings.longBreakSeconds else settings.shortBreakSeconds
        start(focus.projectId, focus.nodeId, type, dur)
    }
}
