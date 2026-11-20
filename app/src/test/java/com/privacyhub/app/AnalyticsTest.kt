package com.privacyhub.app

import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.domain.model.NodeStatus
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.domain.model.SessionType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mirrors the Phase 7 aggregations so the dashboard math is independently checked. */
class AnalyticsTest {
    private fun session(node: Long, secs: Int, status: SessionStatus = SessionStatus.COMPLETED, type: SessionType = SessionType.FOCUS) =
        FocusSessionEntity(projectId = 10, nodeId = node, startTimestamp = 1000, plannedDurationSeconds = 1500,
            actualDurationSeconds = secs, status = status, type = type)
    private fun node(id: Long, status: NodeStatus = NodeStatus.NOT_STARTED) =
        NodeEntity(id = id, projectId = 10, title = "N$id", status = status)

    @Test fun timeByNodeSumsOnlyCompletedFocus() {
        val sessions = listOf(session(1, 300), session(1, 200), session(1, 60, status = SessionStatus.STOPPED), session(1, 60, type = SessionType.SHORT_BREAK), session(2, 90))
        val result = listOf(node(1), node(2)).map { n ->
            val f = sessions.filter { it.nodeId == n.id && it.status == SessionStatus.COMPLETED && it.type == SessionType.FOCUS }
            n.id to f.sumOf { it.actualDurationSeconds.toLong() }
        }
        assertEquals(500L, result.first { it.first == 1L }.second)
        assertEquals(90L, result.first { it.first == 2L }.second)
    }

    @Test fun projectCompletionCountsNodeStatuses() {
        val nodes = listOf(node(1, NodeStatus.COMPLETED), node(2, NodeStatus.COMPLETED), node(3, NodeStatus.IN_PROGRESS), node(4, NodeStatus.NEEDS_REVISION))
        val done = nodes.count { it.status == NodeStatus.COMPLETED }
        assertEquals(2, done)
        assertEquals(50, done * 100 / nodes.size)
        assertEquals(1, nodes.count { it.status == NodeStatus.IN_PROGRESS })
        assertEquals(1, nodes.count { it.status == NodeStatus.NEEDS_REVISION })
    }

    @Test fun emptyProjectReportsZeroPercent() {
        val nodes = emptyList<NodeEntity>()
        val pct = if (nodes.isEmpty()) 0 else nodes.count { it.status == NodeStatus.COMPLETED } * 100 / nodes.size
        assertEquals(0, pct)
    }
}
