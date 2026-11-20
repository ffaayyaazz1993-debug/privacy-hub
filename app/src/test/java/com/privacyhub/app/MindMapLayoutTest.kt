package com.privacyhub.app

import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.domain.layout.MindMapLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MindMapLayoutTest {
    private fun node(id: Long, parentId: Long?, x: Float = 0f, y: Float = 0f, collapsed: Boolean = false) =
        NodeEntity(id = id, projectId = 1, parentId = parentId, title = "N$id", positionX = x, positionY = y, collapsed = collapsed)

    @Test fun rootAtOrigin() {
        val pos = MindMapLayout.arrange(listOf(node(1, null)))
        assertEquals(0f, pos[1]!!.x, 0.001f)
        assertEquals(0f, pos[1]!!.y, 0.001f)
    }
    @Test fun childrenAlternateLeftRight() {
        val pos = MindMapLayout.arrange(listOf(node(1, null), node(2, 1), node(3, 1), node(4, 1)))
        assertTrue(pos[2]!!.x > 0)
        assertTrue(pos[3]!!.x < 0)
        assertTrue(pos[4]!!.x > 0)
    }
    @Test fun collapsedBranchOmitsDescendants() {
        val pos = MindMapLayout.arrange(listOf(node(1, null, collapsed = true), node(2, 1)))
        assertNull(pos[2])
    }
    @Test fun nestedChainExtendsOutward() {
        val pos = MindMapLayout.arrange(listOf(node(1, null), node(2, 1), node(3, 2)))
        assertTrue(pos[3]!!.x > pos[2]!!.x)
    }
    @Test fun boundsCoverNodes() {
        val b = MindMapLayout.bounds(listOf(node(1, null, 0f, 0f), node(2, 1, 500f, 0f)))
        assertNotNull(b)
        assertTrue(b!!.right > 500f)
    }
}
