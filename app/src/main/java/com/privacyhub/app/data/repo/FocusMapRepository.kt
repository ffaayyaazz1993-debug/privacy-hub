package com.privacyhub.app.data.repo

import com.privacyhub.app.data.db.FocusMapDatabase
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.data.db.UserSettingsEntity
import com.privacyhub.app.domain.model.NodeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Single facade over the DAOs; ViewModels depend only on this. */
class FocusMapRepository(private val db: FocusMapDatabase) {
    val projects = db.projectDao()
    val nodes = db.nodeDao()
    val sessions = db.sessionDao()
    val settings = db.settingsDao()

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
}
