package com.privacyhub.app.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.domain.layout.MindMapLayout
import com.privacyhub.app.domain.layout.ZoomMath

private const val TOUCH_SLOP = 20f

private fun hitTest(screen: Offset, nodes: List<NodeEntity>, offset: Offset, scale: Float): Long? {
    val world = (screen - offset) / scale
    for (n in nodes.asReversed()) {
        if (MindMapLayout.nodeRect(n).contains(world)) return n.id
    }
    return null
}

@Composable
fun MindMapCanvas(vm: CanvasViewModel, modifier: Modifier = Modifier) {
    val nodes by vm.nodes.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()
    val nodeFill = MaterialTheme.colorScheme.surfaceVariant
    val nodeText = MaterialTheme.colorScheme.onSurface
    val border = MaterialTheme.colorScheme.outline
    val selection = MaterialTheme.colorScheme.primary
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val rootFill = MaterialTheme.colorScheme.primaryContainer
    val rootText = MaterialTheme.colorScheme.onPrimaryContainer

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableStateOf(false) }
    LaunchedEffect(vm.loaded, viewport) {
        if (vm.loaded && !fitted && viewport != IntSize.Zero && nodes.isNotEmpty()) {
            vm.fitToScreen(viewport); fitted = true
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { viewport = it; vm.viewportSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var draggingId: Long? = hitTest(down.position, vm.nodes.value, vm.offset, vm.scale)
                    var startedNodeMove = false
                    var isDragging = false
                    var totalMove = 0f
                    var lastSingle = down.position
                    val prev = HashMap<PointerId, Offset>()
                    prev[down.id] = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            val a = pressed[0]; val b = pressed[1]
                            val pa = prev[a.id]; val pb = prev[b.id]
                            if (pa != null && pb != null) {
                                val prevDist = (pb - pa).getDistance()
                                val curDist = (b.position - a.position).getDistance()
                                val zoom = if (prevDist > 1f) curDist / prevDist else 1f
                                val prevC = (pa + pb) / 2f
                                val curC = (a.position + b.position) / 2f
                                val old = vm.scale
                                val newScale = (old * zoom).coerceIn(0.2f, 3f)
                                val actual = newScale / old
                                vm.offset = ZoomMath.zoomOffset(curC, prevC, vm.offset, actual)
                                vm.scale = newScale
                            }
                            prev[a.id] = a.position; prev[b.id] = b.position
                            if (!startedNodeMove) draggingId = null
                            isDragging = true
                            pressed.forEach { it.consume() }
                        } else {
                            val c = pressed.first()
                            val delta = c.position - lastSingle
                            totalMove += delta.getDistance()
                            if (totalMove > TOUCH_SLOP) {
                                if (!isDragging) {
                                    isDragging = true
                                    if (draggingId != null) { vm.beginMove(); startedNodeMove = true }
                                }
                                val id = draggingId
                                if (id != null) vm.moveNode(id, delta / vm.scale)
                                else vm.offset = vm.offset + delta
                                c.consume()
                            }
                            lastSingle = c.position
                            prev[c.id] = c.position
                        }
                    }
                    if (startedNodeMove) draggingId?.let { vm.endMove(it) }
                    if (!isDragging) {
                        vm.selectedId = hitTest(down.position, vm.nodes.value, vm.offset, vm.scale)
                    }
                }
            }
    ) {
        val offset = vm.offset
        val scale = vm.scale
        val selectedId = vm.selectedId
        val byId = nodes.associateBy { it.id }
        val viewRect = Rect(0f, 0f, size.width, size.height)

        fun screenRect(n: NodeEntity): Rect {
            val r = MindMapLayout.nodeRect(n)
            return Rect(r.left * scale + offset.x, r.top * scale + offset.y, r.right * scale + offset.x, r.bottom * scale + offset.y)
        }
        fun visible(r: Rect): Boolean = r.overlaps(viewRect)

        val edgePath = Path()
        for (n in nodes) {
            val parent = n.parentId?.let { byId[it] } ?: continue
            if (parent.collapsed) continue
            val pr = screenRect(parent); val cr = screenRect(n)
            if (!visible(pr) && !visible(cr)) continue
            val dir = if (n.positionX >= parent.positionX) 1f else -1f
            val start = Offset(if (dir > 0) pr.right else pr.left, pr.center.y)
            val end = Offset(if (dir > 0) cr.left else cr.right, cr.center.y)
            val midX = (start.x + end.x) / 2
            edgePath.reset()
            edgePath.moveTo(start.x, start.y)
            edgePath.cubicTo(midX, start.y, midX, end.y, end.x, end.y)
            drawPath(edgePath, edgeColor, style = Stroke(width = (2f * scale).coerceAtLeast(1f)))
        }

        for (n in nodes) {
            val r = screenRect(n)
            if (!visible(r)) continue
            val isRoot = n.parentId == null
            val isSel = n.id == selectedId
            val corner = 10f * scale
            val sz = Size(r.width, r.height)
            val tl = Offset(r.left, r.top)
            drawRoundRect(color = if (isRoot) rootFill else nodeFill, topLeft = tl, size = sz, cornerRadius = CornerRadius(corner, corner))
            drawRoundRect(color = if (isSel) selection else border, topLeft = tl, size = sz,
                cornerRadius = CornerRadius(corner, corner), style = Stroke(width = if (isSel) 3f else 1.5f))
            drawCircle(statusColor(n.status), radius = 4f * scale, center = Offset(r.left + 10f * scale, r.center.y))
            val maxW = (r.width - 26f * scale).toInt().coerceAtLeast(1)
            val layout = textMeasurer.measure(
                AnnotatedString(n.title),
                style = TextStyle(color = if (isRoot) rootText else nodeText, fontSize = (13f * scale).sp,
                    fontWeight = if (isRoot) FontWeight.Bold else FontWeight.Normal),
                constraints = Constraints(maxWidth = maxW), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            drawText(layout, topLeft = Offset(r.center.x - layout.size.width / 2 + 4f * scale, r.center.y - layout.size.height / 2))
            if (n.collapsed && vm.childrenOf(n.id).isNotEmpty()) {
                val bx = if (n.positionX >= 0) r.right else r.left
                drawCircle(selection, radius = 8f * scale, center = Offset(bx, r.center.y))
            }
        }
    }
}
