package com.privacyhub.app.data.export

import com.google.gson.Gson
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.data.db.UserSettingsEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import kotlinx.coroutines.flow.first

/** Open-JSON export/import so users are never locked in. Import merges (remaps ids). */
class BackupManager(private val repo: FocusMapRepository) {
    data class Backup(
        val version: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val projects: List<ProjectEntity> = emptyList(),
        val nodes: List<NodeEntity> = emptyList(),
        val sessions: List<FocusSessionEntity> = emptyList(),
        val settings: UserSettingsEntity? = null,
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
    ))

    /** Returns the number of projects imported. Existing data is preserved. */
    suspend fun importJson(json: String): Int {
        val backup = deserialize(json) ?: throw IllegalArgumentException("Invalid or unsupported FocusMap backup file")
        if (backup.version > 1) throw IllegalArgumentException("Backup version ${backup.version} is newer than supported (1)")
        var count = 0
        for (p in backup.projects) {
            val newPid = repo.projects.insert(p.copy(id = 0, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            val idMap = HashMap<Long, Long>()
            for ((node, originalId) in remapNodes(backup.nodes.filter { it.projectId == p.id }, newPid)) {
                val newId = repo.nodes.insert(node)
                idMap[originalId] = newId
            }
            for (s in backup.sessions.filter { it.projectId == p.id }) {
                val newNode = idMap[s.nodeId] ?: continue
                repo.sessions.insert(s.copy(id = 0, projectId = newPid, nodeId = newNode))
            }
            count++
        }
        return count
    }
}
