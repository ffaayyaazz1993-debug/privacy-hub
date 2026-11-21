package com.privacyhub.app.data.repo

import androidx.room.withTransaction
import com.privacyhub.app.data.db.DetoxActivationEntity
import com.privacyhub.app.data.db.DetoxAppEntity
import com.privacyhub.app.data.db.DetoxProfileEntity
import com.privacyhub.app.data.db.DetoxScheduleEntity
import com.privacyhub.app.data.db.FocusMapDatabase
import com.privacyhub.app.data.db.NodeDetoxEntity
import com.privacyhub.app.domain.detox.DetoxCoordinator
import com.privacyhub.app.domain.detox.DetoxSource
import com.privacyhub.app.domain.export.ExportProject
import com.privacyhub.app.domain.export.OutlineNode
import com.privacyhub.app.data.db.PlannedSessionEntity
import com.privacyhub.app.data.db.ReferenceEntity
import com.privacyhub.app.data.db.RoutineEntity
import com.privacyhub.app.data.db.WeeklyReflectionEntity
import com.privacyhub.app.data.db.FlashcardEntity
import com.privacyhub.app.data.db.ReviewLogEntity
import com.privacyhub.app.data.db.SchedulerSettingsEntity
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.data.db.UserSettingsEntity
import com.privacyhub.app.domain.model.NodeStatus
import com.privacyhub.app.domain.spaced.CardSchedule
import com.privacyhub.app.domain.spaced.LearnState
import com.privacyhub.app.domain.spaced.RecallRating
import com.privacyhub.app.domain.spaced.ReviewScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar
import kotlinx.coroutines.flow.map

/** Single facade over the DAOs; ViewModels depend only on this. */
class FocusMapRepository(private val db: FocusMapDatabase) {
    val projects = db.projectDao()
    val nodes = db.nodeDao()
    val sessions = db.sessionDao()
    val settings = db.settingsDao()
    val cards = db.flashcardDao()
    val reviewLogs = db.reviewLogDao()
    val schedulerSettings = db.schedulerSettingsDao()
    val references = db.referenceDao()
    val routines = db.routineDao()
    val planned = db.plannedSessionDao()
    val reflections = db.weeklyReflectionDao()
    val detox = db.detoxDao()

    fun observeSchedulerSettings(): Flow<SchedulerSettingsEntity> =
        schedulerSettings.observe().map { it ?: SchedulerSettingsEntity() }

    suspend fun ensureSchedulerSettings() {
        if (schedulerSettings.get() == null) schedulerSettings.upsert(SchedulerSettingsEntity())
    }

    suspend fun createCard(projectId: Long, nodeId: Long, question: String, answer: String,
                           hint: String = "", explanation: String = ""): Long {
        val now = System.currentTimeMillis()
        return cards.insert(FlashcardEntity(projectId = projectId, nodeId = nodeId,
            question = question, answer = answer, hint = hint, explanation = explanation,
            createdAt = now, updatedAt = now))
    }

    suspend fun duplicateCard(id: Long): Long? {
        val c = cards.get(id) ?: return null
        val now = System.currentTimeMillis()
        return cards.insert(c.copy(id = 0, question = c.question + " (copy)", createdAt = now,
            updatedAt = now, dueTimestamp = now, lastReviewTimestamp = 0L, stability = 0.0,
            difficulty = 0.0, repetitions = 0, lapses = 0, learningState = "NEW",
            learningStep = 0, intervalDays = 0, schedulerVersion = 0, reviewStatus = "NEW"))
    }

    /** Atomic: reschedule + log + card update. A rating is recorded at most once. */
    suspend fun submitReview(card: FlashcardEntity, rating: RecallRating, now: Long,
                             durationSeconds: Int): CardSchedule = db.withTransaction {
        val cfg = schedulerSettings.get() ?: SchedulerSettingsEntity()
        val elapsedDays = if (card.lastReviewTimestamp <= 0L) 0.0 else
            (now - card.lastReviewTimestamp).toDouble() / ReviewScheduler.DAY_MS
        val result = ReviewScheduler.schedule(
            now = now, lastReviewAt = card.lastReviewTimestamp,
            stateName = card.learningState, step = card.learningStep,
            stability = card.stability, difficulty = card.difficulty,
            repetitions = card.repetitions, lapses = card.lapses, rating = rating,
            desiredRetention = cfg.desiredRetention, maximumInterval = cfg.maximumInterval,
        )
        reviewLogs.insert(ReviewLogEntity(
            flashcardId = card.id, reviewedAt = now, rating = rating.name,
            previousDueTimestamp = card.dueTimestamp, nextDueTimestamp = result.dueAt,
            elapsedDays = elapsedDays, previousStability = card.stability,
            newStability = result.stability, previousDifficulty = card.difficulty,
            newDifficulty = result.difficulty, elapsedDurationSeconds = durationSeconds,
            schedulerVersion = ReviewScheduler.VERSION,
        ))
        cards.update(card.copy(
            dueTimestamp = result.dueAt, lastReviewTimestamp = now,
            stability = result.stability, difficulty = result.difficulty,
            repetitions = result.repetitions, lapses = result.lapses,
            learningState = result.state.name, learningStep = result.step,
            intervalDays = result.intervalDays, schedulerVersion = ReviewScheduler.VERSION,
            reviewStatus = result.state.name, updatedAt = now,
        ))
        result
    }

    fun observeSettings(): Flow<UserSettingsEntity> =
        settings.observe().map { it ?: UserSettingsEntity() }

    suspend fun ensureSettings() {
        if (settings.get() == null) settings.upsert(UserSettingsEntity())
    }

    /** Create a project with a root node of the same title; returns the project id. */
    suspend fun createProject(title: String, description: String = "", category: String = ""): Long {
        val now = System.currentTimeMillis()
        val pid = projects.insert(ProjectEntity(title = title, description = description, category = category, createdAt = now, updatedAt = now))
        nodes.insert(NodeEntity(projectId = pid, parentId = null, title = title, positionX = 0f, positionY = 0f, createdAt = now, updatedAt = now))
        return pid
    }

    /** Duplicate a project and its full node tree (fresh ids, remapped parents). */
    suspend fun duplicateProject(id: Long): Long? {
        val p = projects.get(id) ?: return null
        val now = System.currentTimeMillis()
        val newPid = projects.insert(p.copy(id = 0, title = p.title + " (copy)", createdAt = now, updatedAt = now))
        val tree = nodes.getByProject(id)
        val idMap = HashMap<Long, Long>()
        val remaining = tree.toMutableList()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { it.parentId == null || idMap.containsKey(it.parentId) }
            if (ready.isEmpty()) break
            for (n in ready) {
                val newId = nodes.insert(n.copy(id = 0, projectId = newPid, parentId = n.parentId?.let { idMap[it] }))
                idMap[n.id] = newId
            }
            remaining.removeAll(ready.toSet())
        }
        return newPid
    }

    /** Delete a node and its whole subtree, plus their sessions. */
    suspend fun deleteNodeSubtree(nodeId: Long) {
        val node = nodes.get(nodeId) ?: return
        val all = nodes.getByProject(node.projectId)
        val byParent = all.groupBy { it.parentId }
        val doomed = ArrayDeque<Long>(); doomed.add(nodeId)
        val ids = mutableSetOf<Long>()
        while (doomed.isNotEmpty()) {
            val id = doomed.removeFirst()
            if (!ids.add(id)) continue
            byParent[id]?.forEach { doomed.add(it.id) }
        }
        ids.forEach { sessions.deleteByNode(it); nodes.deleteById(it) }
        cards.deleteByNodeIds(ids.toList())
    }

    /** Record a finished focus session and roll its time into the node. */
    suspend fun completeFocusSession(session: FocusSessionEntity) {
        val now = System.currentTimeMillis()
        sessions.update(session.copy(status = com.privacyhub.app.domain.model.SessionStatus.COMPLETED, endTimestamp = now))
        val node = nodes.get(session.nodeId) ?: return
        val completed = node.completedSessions + 1
        val seconds = node.totalFocusSeconds + session.actualDurationSeconds
        // Focus completion is NOT learning completion: only move NOT_STARTED -> IN_PROGRESS.
        val status = if (node.status == NodeStatus.NOT_STARTED) NodeStatus.IN_PROGRESS else node.status
        nodes.updateStats(node.id, completed, seconds, status.name, now)
        projects.get(session.projectId)?.let { projects.update(it.copy(updatedAt = now)) }
    }


    suspend fun createReference(projectId: Long, nodeId: Long, title: String, content: String,
                                url: String, mimeType: String, uriString: String): Long =
        references.insert(ReferenceEntity(projectId = projectId, nodeId = nodeId,
            title = title.ifBlank { url.ifBlank { "Captured note" } }, content = content,
            url = url, mimeType = mimeType, uriString = uriString))

    suspend fun saveRoutine(r: RoutineEntity): Long = if (r.id == 0L) routines.insert(r) else { routines.update(r); r.id }

    suspend fun deleteRoutine(id: Long) {
        routines.get(id)?.let { r ->
            planned.deleteByRoutine(r.id)
            routines.delete(r)
        }
    }

    /** Monday-first weekday index for a epoch-millis timestamp. */
    fun weekdayIndex(at: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = at }
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    fun weekStart(at: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = at
            set(Calendar.HOUR_OF_DAY, 0); clear(Calendar.MINUTE); clear(Calendar.SECOND); clear(Calendar.MILLISECOND)
        }
        val idx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        c.add(Calendar.DAY_OF_YEAR, -idx)
        return c.timeInMillis
    }

    /** Generates future activities; existing (routine, slot) pairs are never duplicated. */
    suspend fun generatePlanned(routine: RoutineEntity, now: Long, weeks: Int = 2): Int {
        if (!routine.active || routine.nodeId == null) return 0
        val days = routine.weekdays.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..6 }.toSet()
        if (days.isEmpty()) return 0
        var added = 0
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 9); clear(Calendar.MINUTE); clear(Calendar.SECOND); clear(Calendar.MILLISECOND)
        }
        for (offset in 0 until weeks * 7) {
            val at = (cal.timeInMillis) + offset * 86_400_000L
            if (weekdayIndex(at) !in days || at <= now) continue
            if (planned.find(routine.id, at) != null) continue
            planned.insert(PlannedSessionEntity(routineId = routine.id, projectId = routine.projectId,
                nodeId = routine.nodeId, scheduledAt = at, durationMinutes = routine.durationMinutes))
            added++
        }
        return added
    }

    suspend fun markMissed(now: Long) = planned.markMissed(now)

    suspend fun reschedulePlanned(id: Long, newAt: Long) {
        val p = planned.get(id) ?: return
        if (p.status != "COMPLETED") planned.update(p.copy(scheduledAt = newAt, status = "RESCHEDULED"))
    }

    /** OPML import creates a real project tree; only titles/descriptions survive the format. */
    suspend fun importOpmlProject(source: ExportProject): Long {
        val pid = createProject(source.title, source.description)
        val root = nodes.getByProject(pid).firstOrNull() ?: return pid
        suspend fun add(parentId: Long, children: List<OutlineNode>) {
            children.forEach { c ->
                val now = System.currentTimeMillis()
                val id = nodes.insert(com.privacyhub.app.data.db.NodeEntity(
                    projectId = pid, parentId = parentId, title = c.title,
                    description = c.description, createdAt = now, updatedAt = now))
                add(id, c.children)
            }
        }
        add(root.id, source.nodes)
        return pid
    }

    // ---- Digital Detox ----
    fun observeProfiles() = detox.observeProfiles()
    fun observeDetoxApps(pid: Long) = detox.observeApps(pid)
    fun observeSchedules() = detox.observeSchedules()
    fun observeActivations() = detox.observeActivations()
    fun observeDetoxHistory() = detox.observeHistory()

    suspend fun saveProfile(p: DetoxProfileEntity): Long {
        if (p.isDefault) detox.clearDefaults()
        return if (p.id == 0L) detox.insertProfile(p) else { detox.updateProfile(p); p.id }
    }

    suspend fun deleteProfile(id: Long) = detox.deleteProfile(id)

    suspend fun addBlockedApp(profileId: Long, packageName: String, label: String) =
        detox.insertApp(DetoxAppEntity(profileId = profileId, packageName = packageName, label = label))

    suspend fun removeBlockedApp(profileId: Long, packageName: String) = detox.removeApp(profileId, packageName)

    suspend fun duplicateProfile(id: Long): Long? {
        val src = detox.profile(id) ?: return null
        val apps = detox.observeApps(id)
        val newId = detox.insertProfile(src.copy(id = 0, name = src.name + " (copy)", isDefault = false))
        // copy selected apps deterministically
        apps.first().forEach { a -> detox.insertApp(a.copy(id = 0, profileId = newId)) }
        return newId
    }

    suspend fun setNodeDetox(nodeId: Long, profileId: Long?, autoActivate: Boolean = true) =
        detox.upsertNode(NodeDetoxEntity(nodeId, profileId, autoActivate))

    suspend fun nodeDetox(nodeId: Long): NodeDetoxEntity? = detox.nodeConfig(nodeId)

    suspend fun saveSchedule(s: DetoxScheduleEntity): Long =
        if (s.id == 0L) detox.insertSchedule(s) else { detox.updateSchedule(s); s.id }

    suspend fun deleteSchedule(id: Long) = detox.deleteSchedule(id)
    suspend fun schedule(id: Long): DetoxScheduleEntity? = detox.schedule(id)

    /** Profile resolution for a Pomodoro session, matching the documented priority. */
    suspend fun resolveDetoxProfile(projectId: Long, nodeId: Long, explicitId: Long? = null): DetoxProfileEntity? {
        val profiles = detox.observeProfiles().first()
        val nodeCfg = detox.nodeConfig(nodeId)
        return DetoxCoordinator.resolveProfile(explicitId, nodeId, projectId, profiles,
            mapOf(nodeId to nodeCfg?.profileId))
    }

    suspend fun activateForPomodoro(focusSessionId: Long, projectId: Long, nodeId: Long,
                                    plannedMillis: Long, explicitProfileId: Long? = null): DetoxActivationEntity? {
        val profile = resolveDetoxProfile(projectId, nodeId, explicitProfileId) ?: return null
        if (!profile.autoActivate) return null
        val now = System.currentTimeMillis()
        val existing = detox.activationForFocus(focusSessionId)
        if (existing != null) return existing
        val id = detox.insertActivation(DetoxActivationEntity(
            sourceType = DetoxSource.POMODORO.key, sourceId = focusSessionId.toString(),
            profileId = profile.id, startedAt = now, endsAt = now + plannedMillis,
            status = "ACTIVE", focusSessionId = focusSessionId))
        return detox.activationFor("POMODORO", focusSessionId.toString())
    }

    suspend fun releaseForPomodoro(focusSessionId: Long) {
        val a = detox.activationForFocus(focusSessionId) ?: return
        if (a.status == "ACTIVE") detox.updateActivation(a.copy(status = "RELEASED", endsAt = System.currentTimeMillis()))
    }

    suspend fun setPauseBehaviour(focusSessionId: Long, suspend: Boolean) {
        val a = detox.activationForFocus(focusSessionId) ?: return
        if (a.status != "ACTIVE") return
        detox.updateActivation(if (suspend) a.copy(status = "PAUSED") else a.copy(status = "ACTIVE"))
    }

    suspend fun startManualDetox(profileId: Long, durationMinutes: Int): DetoxActivationEntity {
        val now = System.currentTimeMillis()
        val id = detox.insertActivation(DetoxActivationEntity(sourceType = DetoxSource.MANUAL.key,
            sourceId = "manual-$now", profileId = profileId, startedAt = now,
            endsAt = if (durationMinutes <= 0) 0L else now + durationMinutes * 60_000L))
        return detox.activationFor("MANUAL", "manual-$now")!!
    }

    suspend fun endManualDetox(activationId: Long) {
        val a = detox.currentActivations().firstOrNull { it.id == activationId } ?: return
        detox.updateActivation(a.copy(status = "ENDED", endsAt = System.currentTimeMillis()))
    }

    suspend fun startScheduledDetox(scheduleId: Long, profileId: Long, endsAt: Long): DetoxActivationEntity? {
        val src = "schedule-$scheduleId"
        val existing = detox.activationFor("SCHEDULED", src)
        if (existing != null && DetoxCoordinator.isSourceLive(existing, System.currentTimeMillis())) return existing
        val id = detox.insertActivation(DetoxActivationEntity(sourceType = DetoxSource.SCHEDULED.key,
            sourceId = src, profileId = profileId, startedAt = System.currentTimeMillis(), endsAt = endsAt))
        return detox.activationFor("SCHEDULED", src)
    }

    suspend fun emergencyOverride(activationId: Long, minutes: Int) {
        val a = detox.currentActivations().firstOrNull { it.id == activationId } ?: return
        detox.updateActivation(a.copy(overrideUntil = System.currentTimeMillis() + minutes * 60_000L))
    }

    suspend fun currentActivations(): List<DetoxActivationEntity> = detox.currentActivations()
    suspend fun activeSchedules(): List<DetoxScheduleEntity> = detox.activeSchedules()
    suspend fun profileById(id: Long): DetoxProfileEntity? = detox.profile(id)
    suspend fun blockedPackages(profileIds: List<Long>): List<String> = detox.packagesFor(profileIds)

    suspend fun saveReflection(r: WeeklyReflectionEntity) = reflections.upsert(r.copy(updatedAt = System.currentTimeMillis()))
}
