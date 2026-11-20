package com.privacyhub.app.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import com.privacyhub.app.domain.layout.MindMapLayout
import com.privacyhub.app.domain.model.NodeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CanvasViewModel(private val repo: FocusMapRepository, val projectId: Long) : ViewModel() {
    val project: StateFlow<ProjectEntity?> = repo.projects.observe(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val projectSessions: StateFlow<List<FocusSessionEntity>> = repo.sessions.observeByProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _nodes = MutableStateFlow<List<NodeEntity>>(emptyList())
    val nodes: StateFlow<List<NodeEntity>> = _nodes

    var offset by mutableStateOf(Offset.Zero)
    var scale by mutableStateOf(1f)
    var selectedId by mutableStateOf<Long?>(null)
    var loaded by mutableStateOf(false)
    var viewportSize by mutableStateOf(IntSize.Zero)
    var undoCount by mutableStateOf(0)
    var redoCount by mutableStateOf(0)

    private val undoStack = ArrayDeque<List<NodeEntity>>()
    private val redoStack = ArrayDeque<List<NodeEntity>>()

    init {
        viewModelScope.launch {
            _nodes.value = repo.nodes.getByProject(projectId)
            loaded = true
        }
    }

    val selectedNode: NodeEntity? get() = _nodes.value.find { it.id == selectedId }
    fun childrenOf(id: Long): List<NodeEntity> = _nodes.value.filter { it.parentId == id }
    fun isCollapsedWithChildren(n: NodeEntity): Boolean = n.collapsed && childrenOf(n.id).isNotEmpty()

    private fun syncCounts() { undoCount = undoStack.size; redoCount = redoStack.size }
    private fun pushUndo() {
        undoStack.addLast(_nodes.value.map { it.copy() })
        if (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear(); syncCounts()
    }
    private suspend fun reload() { _nodes.value = repo.nodes.getByProject(projectId) }

    fun addChild(parentId: Long) {
        val parent = _nodes.value.find { it.id == parentId } ?: return
        pushUndo()
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val sibCount = childrenOf(parentId).size
            val dir = if (parent.positionX < 0) -1f else 1f
            val id = repo.nodes.insert(
                NodeEntity(
                    projectId = projectId, parentId = parentId, title = "New idea",
                    positionX = parent.positionX + dir * MindMapLayout.H_GAP,
                    positionY = parent.positionY + (sibCount - 1) * 55f,
                    createdAt = now, updatedAt = now,
                )
            )
            if (parent.collapsed) repo.nodes.update(parent.copy(collapsed = false))
            reload(); selectedId = id
        }
    }

    fun addSibling(nodeId: Long) {
        val n = _nodes.value.find { it.id == nodeId } ?: return
        if (n.parentId == null) { addChild(n.id); return }
        pushUndo()
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = repo.nodes.insert(
                NodeEntity(
                    projectId = projectId, parentId = n.parentId, title = "New idea",
                    positionX = n.positionX, positionY = n.positionY + MindMapLayout.NODE_H + MindMapLayout.V_GAP,
                    createdAt = now, updatedAt = now,
                )
            )
            reload(); selectedId = id
        }
    }

    fun addBranch() {
        val target = selectedId ?: _nodes.value.firstOrNull { it.parentId == null }?.id ?: return
        addChild(target)
    }

    fun deleteNode(nodeId: Long) {
        pushUndo()
        viewModelScope.launch {
            repo.deleteNodeSubtree(nodeId)
            if (selectedId == nodeId) selectedId = null
            reload()
        }
    }

    fun renameNode(nodeId: Long, title: String) {
        val n = _nodes.value.find { it.id == nodeId } ?: return
        if (title.isBlank() || title == n.title) return
        pushUndo()
        viewModelScope.launch { repo.nodes.update(n.copy(title = title.trim(), updatedAt = System.currentTimeMillis())); reload() }
    }

    fun updateNode(updated: NodeEntity) {
        pushUndo()
        viewModelScope.launch { repo.nodes.update(updated.copy(updatedAt = System.currentTimeMillis())); reload() }
    }

    fun toggleCollapse(nodeId: Long) {
        val n = _nodes.value.find { it.id == nodeId } ?: return
        pushUndo()
        viewModelScope.launch { repo.nodes.update(n.copy(collapsed = !n.collapsed)); reload() }
    }

    // ---- drag (undo snapshot once at start; persist at end) ----
    fun beginMove() { pushUndo() }
    fun moveNode(nodeId: Long, worldDelta: Offset) {
        _nodes.value = _nodes.value.map {
            if (it.id == nodeId) it.copy(positionX = it.positionX + worldDelta.x, positionY = it.positionY + worldDelta.y) else it
        }
    }
    fun endMove(nodeId: Long) {
        val n = _nodes.value.find { it.id == nodeId } ?: return
        viewModelScope.launch { repo.nodes.update(n.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun autoArrange() {
        pushUndo()
        val pos = MindMapLayout.arrange(_nodes.value)
        viewModelScope.launch {
            _nodes.value.forEach { n -> pos[n.id]?.let { repo.nodes.update(n.copy(positionX = it.x, positionY = it.y)) } }
            reload()
        }
    }

    fun requestFit() { fitToScreen(viewportSize) }

    fun zoomBy(factor: Float) {
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val old = scale
        val newScale = (old * factor).coerceIn(0.2f, 3f)
        val actual = newScale / old
        offset = center - (center - offset) * actual
        scale = newScale
    }
    fun zoomIn() { zoomBy(1.25f) }
    fun zoomOut() { zoomBy(0.8f) }

    fun fitToScreen(viewport: IntSize) {
        val b = MindMapLayout.bounds(_nodes.value) ?: return
        if (viewport.width == 0 || viewport.height == 0) return
        val pad = 80f
        val sx = viewport.width / (b.width + pad)
        val sy = viewport.height / (b.height + pad)
        scale = minOf(sx, sy).coerceIn(0.2f, 1.6f)
        val center = Offset(b.left + b.width / 2, b.top + b.height / 2)
        offset = Offset(viewport.width / 2f, viewport.height / 2f) - center * scale
    }

    private suspend fun applySnapshot(snap: List<NodeEntity>) {
        val current = repo.nodes.getByProject(projectId)
        val snapIds = snap.map { it.id }.toSet()
        val curIds = current.map { it.id }.toSet()
        current.filter { it.id !in snapIds }.forEach { repo.nodes.deleteById(it.id) }
        snap.forEach { s -> if (s.id in curIds) repo.nodes.update(s) else repo.nodes.insert(s) }
        reload()
    }

    fun undo() {
        val snap = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_nodes.value.map { it.copy() })
        viewModelScope.launch { applySnapshot(snap); syncCounts() }
    }
    fun redo() {
        val snap = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_nodes.value.map { it.copy() })
        viewModelScope.launch { applySnapshot(snap); syncCounts() }
    }

    fun setStatus(nodeId: Long, status: NodeStatus) {
        val n = _nodes.value.find { it.id == nodeId } ?: return
        updateNode(n.copy(status = status))
    }
}
