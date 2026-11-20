package com.privacyhub.app

import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.data.export.BackupManager
import com.privacyhub.app.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {
    private fun node(id: Long, parent: Long?, pid: Long = 10) =
        NodeEntity(id = id, projectId = pid, parentId = parent, title = "N$id", notes = "note $id")
    private fun session(id: Long, nodeId: Long, pid: Long = 10) =
        FocusSessionEntity(id = id, projectId = pid, nodeId = nodeId, startTimestamp = 1000,
            plannedDurationSeconds = 1500, accumulatedSeconds = 1500, status = SessionStatus.COMPLETED,
            endTimestamp = 2500, actualDurationSeconds = 1500, review = "good")

    @Test fun serializeDeserializeRoundTrip() {
        val backup = BackupManager.Backup(
            projects = listOf(ProjectEntity(id = 10, title = "Learn Macro", category = "Education")),
            nodes = listOf(node(1, null), node(2, 1)),
            sessions = listOf(session(1, 2)),
        )
        val json = BackupManager.serialize(backup)
        val back = BackupManager.deserialize(json)!!
        assertEquals(1, back.projects.size)
        assertEquals("Learn Macro", back.projects[0].title)
        assertEquals(2, back.nodes.size)
        assertEquals(2L, back.sessions[0].nodeId)
        assertEquals("good", back.sessions[0].review)
        assertTrue(json.contains("Learn Macro"))
    }

    @Test fun deserializeRejectsGarbage() {
        assertNull(BackupManager.deserialize("not json {"))
    }
    @Test fun importRejectsGarbageAndNewerVersion() {
        val back = BackupManager.deserialize("{\"version\":99,\"projects\":[]}")!!
        assert(back.version > 1)
        assertNull(BackupManager.deserialize("garbage"))
    }

    @Test fun remapNodesPreservesHierarchy() {
        val nodes = listOf(node(1, null), node(2, 1), node(3, 1), node(4, 2))
        val rebuilt = BackupManager.remapNodes(nodes, newProjectId = 99)
        assertEquals(4, rebuilt.size)
        assertEquals(99L, rebuilt[0].first.projectId)
        assertNull(rebuilt[0].first.parentId)
        // child of root 1 now points at remapped id of 1
        val rootNewId = rebuilt[0].first.id
        val child2 = rebuilt.first { it.second == 2L }.first
        val child4 = rebuilt.first { it.second == 4L }.first
        assertEquals(rootNewId, child2.parentId)
        val child2NewId = child2.id
        assertEquals(child2NewId, child4.parentId)
    }

    @Test fun remapNodesHandlesMultipleRoots() {
        val nodes = listOf(node(1, null), node(2, null), node(3, 1))
        val rebuilt = BackupManager.remapNodes(nodes, newProjectId = 55)
        assertEquals(3, rebuilt.size)
        assertTrue(rebuilt.all { it.first.projectId == 55L })
        assertEquals(2, rebuilt.count { it.first.parentId == null })
    }
}
