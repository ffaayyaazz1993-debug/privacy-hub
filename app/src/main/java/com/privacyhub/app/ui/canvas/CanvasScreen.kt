package com.privacyhub.app.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.ui.repository
import com.privacyhub.app.ui.review.NodeLearningDialog
import com.privacyhub.app.ui.detox.NodeDetoxDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(projectId: Long, onBack: () -> Unit, onStartFocus: (NodeEntity) -> Unit,
                  onRecall: (NodeEntity) -> Unit, onReviewNode: (NodeEntity) -> Unit,
                  onDetoxNode: (NodeEntity) -> Unit = {}, onOpenDashboard: () -> Unit = {}) {
    val repo = repository()
    val vm: CanvasViewModel = viewModel(factory = viewModelFactory { initializer { CanvasViewModel(repo, projectId) } })
    val project by vm.project.collectAsStateWithLifecycle()
    val nodes by vm.nodes.collectAsStateWithLifecycle()
    val selected = nodes.find { it.id == vm.selectedId }
    var editing by remember { mutableStateOf<NodeEntity?>(null) }
    var showStats by remember { mutableStateOf(false) }
    var historyFor by remember { mutableStateOf<Long?>(null) }
    var progressFor by remember { mutableStateOf<com.privacyhub.app.data.db.NodeEntity?>(null) }
    var detoxFor by remember { mutableStateOf<com.privacyhub.app.data.db.NodeEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        MindMapCanvas(vm, Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Text(project?.title ?: "Mind map", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = { vm.addBranch() }) { Icon(Icons.Rounded.Add, contentDescription = "Add branch") }
                IconButton(onClick = { vm.zoomIn() }) { Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in") }
                IconButton(onClick = { vm.zoomOut() }) { Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out") }
                IconButton(onClick = { vm.autoArrange() }) { Icon(Icons.Rounded.AutoFixHigh, contentDescription = "Auto arrange") }
                IconButton(onClick = { vm.requestFit() }) { Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Fit to screen") }
                IconButton(onClick = onOpenDashboard) { Icon(Icons.Rounded.Dashboard, contentDescription = "Workspace dashboard") }
                IconButton(onClick = { showStats = true }) { Icon(Icons.Rounded.Info, contentDescription = "Project statistics") }
                IconButton(onClick = { vm.undo() }, enabled = vm.undoCount > 0) { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo") }
                IconButton(onClick = { vm.redo() }, enabled = vm.redoCount > 0) { Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo") }
            }
        }
        if (selected != null) {
            NodePanel(vm, selected, onEdit = { editing = selected }, onStartFocus = { onStartFocus(selected) },
                onHistory = { historyFor = selected.id }, onRecall = { onRecall(selected) },
                onReview = { onReviewNode(selected) }, onProgress = { progressFor = selected },
                onDetox = { detoxFor = selected }, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    editing?.let { n ->
        NodeEditDialog(node = n, onDismiss = { editing = null }, onSave = { vm.updateNode(it); editing = null })
    }
    if (showStats) {
        val ns by vm.nodes.collectAsStateWithLifecycle()
        val ss by vm.projectSessions.collectAsStateWithLifecycle()
        ProjectStatsDialog(nodes = ns, sessions = ss, onDismiss = { showStats = false })
    }
    historyFor?.let { id -> NodeHistoryDialog(nodeId = id, onDismiss = { historyFor = null }) }
    progressFor?.let { n -> NodeLearningDialog(nodeId = n.id, nodeTitle = n.title,
        focusSeconds = n.totalFocusSeconds, sessions = n.completedSessions,
        onDismiss = { progressFor = null }) }
    detoxFor?.let { n -> NodeDetoxDialog(nodeId = n.id, nodeTitle = n.title, onDismiss = { detoxFor = null }) }
}
