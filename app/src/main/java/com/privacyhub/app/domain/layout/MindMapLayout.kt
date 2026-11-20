package com.privacyhub.app.domain.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.privacyhub.app.data.db.NodeEntity

/** Tidy two-sided mind-map layout: root centred, branches alternate left/right. */
object MindMapLayout {
    const val NODE_H = 46f
    const val H_GAP = 235f
    const val V_GAP = 22f

    fun nodeWidth(title: String): Float = (title.length * 8f + 30f).coerceIn(96f, 260f)

    fun nodeRect(n: NodeEntity): Rect {
        val w = nodeWidth(n.title)
        return Rect(n.positionX - w / 2, n.positionY - NODE_H / 2, n.positionX + w / 2, n.positionY + NODE_H / 2)
    }

    fun arrange(nodes: List<NodeEntity>): Map<Long, Offset> {
        if (nodes.isEmpty()) return emptyMap()
        val byId = nodes.associateBy { it.id }
        val byParent = nodes.groupBy { it.parentId }
        val root = nodes.firstOrNull { it.parentId == null } ?: nodes.first()
        val pos = HashMap<Long, Offset>()

        fun height(id: Long): Float {
            val n = byId[id] ?: return NODE_H + V_GAP
            val kids = byParent[id].orEmpty()
            if (kids.isEmpty() || n.collapsed) return NODE_H + V_GAP
            return kids.sumOf { height(it.id).toDouble() }.toFloat()
        }
        fun place(id: Long, x: Float, cy: Float, dir: Int) {
            pos[id] = Offset(x, cy)
            val n = byId[id] ?: return
            if (n.collapsed) return
            val kids = byParent[id].orEmpty()
            val total = kids.sumOf { height(it.id).toDouble() }.toFloat()
            var y = cy - total / 2
            for (k in kids) {
                val h = height(k.id)
                place(k.id, x + dir * H_GAP, y + h / 2, dir)
                y += h
            }
        }

        pos[root.id] = Offset(0f, 0f)
        if (!root.collapsed) {
            val kids = byParent[root.id].orEmpty()
            val right = kids.filterIndexed { i, _ -> i % 2 == 0 }
            val left = kids.filterIndexed { i, _ -> i % 2 == 1 }
            var y = -right.sumOf { height(it.id).toDouble() }.toFloat() / 2
            for (k in right) { val h = height(k.id); place(k.id, H_GAP, y + h / 2, +1); y += h }
            y = -left.sumOf { height(it.id).toDouble() }.toFloat() / 2
            for (k in left) { val h = height(k.id); place(k.id, -H_GAP, y + h / 2, -1); y += h }
        }
        return pos
    }

    fun bounds(nodes: List<NodeEntity>): Rect? {
        if (nodes.isEmpty()) return null
        var r = nodeRect(nodes.first())
        for (n in nodes.drop(1)) {
            val nr = nodeRect(n)
            r = Rect(minOf(r.left, nr.left), minOf(r.top, nr.top), maxOf(r.right, nr.right), maxOf(r.bottom, nr.bottom))
        }
        return r
    }
}
