package com.privacyhub.app.data.export

import com.google.gson.Gson
import com.privacyhub.app.data.db.FlashcardEntity
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.ReviewLogEntity
import com.privacyhub.app.data.db.SchedulerSettingsEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.PlannedSessionEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.data.db.ReferenceEntity
import com.privacyhub.app.data.db.RoutineEntity
import com.privacyhub.app.data.db.WeeklyReflectionEntity
import com.privacyhub.app.data.db.UserSettingsEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import kotlinx.coroutines.flow.first

/** Open-JSON export/import so users are never locked in. Import merges (remaps ids). */
class BackupManager(private val repo: FocusMapRepository) {
    data class Backup(
        val version: Int = 3,
        val exportedAt: Long = System.currentTimeMillis(),
        val projects: List<ProjectEntity> = emptyList(),
        val nodes: List<NodeEntity> = emptyList(),
        val sessions: List<FocusSessionEntity> = emptyList(),
        val settings: UserSettingsEntity? = null,
        val flashcards: List<FlashcardEntity> = emptyList(),
        val reviewLogs: List<ReviewLogEntity> = emptyList(),
        val schedulerSettings: SchedulerSettingsEntity? = null,
        val references: List<ReferenceEntity> = emptyList(),
        val routines: List<RoutineEntity> = emptyList(),
        val plannedSessions: List<PlannedSessionEntity> = emptyList(),
        val weeklyReflections: List<WeeklyReflectionEntity> = emptyList(),
    )

    companion object {
        private val gson = Gson()
        fun serialize(backup: Backup): String = gson.toJson(backup)
        fun deserialize(json: String): Backup? = try { gson.fromJson(json, Backup::class.java) } catch (_: Exception) { null }

        /** Remap node ids into a fresh project. Returns (rebuilt node, originalId) in insert-safe parent order. */
        fun remapNodes(nodes: List<NodeEntity>, newProjectId: Long): List<Pair<NodeEntity, Long>> {
            val result = mutableListOf<Pair<NodeEntity, Long>>()
            val idMap = HashMap<Long, Long>()
            var nextId = 1L
            val remaining = nodes.toMutableList()
            while (remaining.isNotEmpty()) {
                val ready = remaining.filter { it.parentId == null || idMap.containsKey(it.parentId) }
                if (ready.isEmpty()) break
                for (n in ready) {
                    val newId = nextId++
                    result += (n.copy(id = newId, projectId = newProjectId, parentId = n.parentId?.let { idMap[it] }) to n.id)
                    idMap[n.id] = newId
                }
                remaining.removeAll(ready.toSet())
            }
            return result
        }
    }

    suspend fun exportJson(): String = serialize(Backup(
        projects = repo.projects.observeAll().first(),
        nodes = repo.nodes.observeAll().first(),
        sessions = repo.sessions.observeAll().first(),
        settings = repo.settings.get(),
        flashcards = repo.cards.observeAll().first(),
        reviewLogs = repo.reviewLogs.observeAll().first(),
        schedulerSettings = repo.schedulerSettings.get(),
        references = repo.references.observeAll().first(),
        routines = repo.routines.observeAll().first(),
        plannedSessions = repo.planned.observeAll().first(),
        weeklyReflections = repo.reflections.observeAll().first(),
    ))

    /** Returns the number of projects imported. Existing data is preserved. */
    suspend fun importJson(json: String): Int {
        val backup = deserialize(json) ?: throw IllegalArgumentException("Invalid or unsupported FocusMap backup file")
        if (backup.version > 3) throw IllegalArgumentException("Backup version ${backup.version} is newer than supported (3)")
        val projectsIn = backup.projects.orEmpty()
        val nodesIn = backup.nodes.orEmpty()
        val sessionsIn = backup.sessions.orEmpty()
        val cardsIn = backup.flashcards.orEmpty()
        val logsIn = backup.reviewLogs.orEmpty()
        val refsIn = backup.references.orEmpty()
        val routinesIn = backup.routines.orEmpty()
        val plannedIn = backup.plannedSessions.orEmpty()
        var count = 0
        for (p in projectsIn) {
            val now = System.currentTimeMillis()
            val newPid = repo.projects.insert(p.copy(id = 0, createdAt = now, updatedAt = now))
            val idMap = HashMap<Long, Long>()
            val remaining = nodesIn.filter { it.projectId == p.id }.toMutableList()
            while (remaining.isNotEmpty()) {
                val ready = remaining.filter { it.parentId == null || idMap.containsKey(it.parentId) }
                if (ready.isEmpty()) break
                for (n in ready) {
                    val newId = repo.nodes.insert(n.copy(id = 0, projectId = newPid,
                        parentId = n.parentId?.let { idMap[it] }, createdAt = now, updatedAt = now))
                    idMap[n.id] = newId
                }
                remaining.removeAll(ready.toSet())
            }
            for (sess in sessionsIn.filter { it.projectId == p.id }) {
                val newNode = idMap[sess.nodeId] ?: continue
                repo.sessions.insert(sess.copy(id = 0, projectId = newPid, nodeId = newNode))
            }
            val cardMap = HashMap<Long, Long>()
            for (c in cardsIn.filter { it.projectId == p.id }) {
                val newNode = idMap[c.nodeId] ?: continue
                val newId = repo.cards.insert(c.copy(id = 0, projectId = newPid, nodeId = newNode))
                cardMap[c.id] = newId
            }
            for (log in logsIn.filter { cardMap.containsKey(it.flashcardId) }) {
                repo.reviewLogs.insert(log.copy(id = 0, flashcardId = cardMap[log.flashcardId]!!))
            }
            for (ref in refsIn.filter { it.projectId == p.id }) {
                val nid = idMap[ref.nodeId] ?: continue
                repo.references.insert(ref.copy(id = 0, projectId = newPid, nodeId = nid))
            }
            val routineMap = HashMap<Long, Long>()
            for (r in routinesIn.filter { it.projectId == p.id }) {
                val nid = r.nodeId?.let { idMap[it] }
                val rid = repo.routines.insert(r.copy(id = 0, projectId = newPid, nodeId = nid))
                routineMap[r.id] = rid
            }
            for (ps in plannedIn.filter { routineMap.containsKey(it.routineId) }) {
                val nid = idMap[ps.nodeId] ?: continue
                repo.planned.insert(ps.copy(id = 0, routineId = routineMap[ps.routineId]!!,
                    projectId = newPid, nodeId = nid))
            }
            count++
        }
        backup.schedulerSettings?.let { repo.schedulerSettings.upsert(it.copy(id = 1)) }
        backup.weeklyReflections.orEmpty().forEach { repo.reflections.upsert(it) }
        return count
    }
}
