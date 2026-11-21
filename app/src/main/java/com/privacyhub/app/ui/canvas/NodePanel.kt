package com.privacyhub.app.ui.canvas

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.domain.model.NodeStatus
import com.privacyhub.app.util.formatDuration

fun statusLabel(s: NodeStatus): String = when (s) {
    NodeStatus.NOT_STARTED -> "Not started"
    NodeStatus.IN_PROGRESS -> "In progress"
    NodeStatus.COMPLETED -> "Completed"
    NodeStatus.NEEDS_REVISION -> "Needs revision"
}
fun statusColor(s: NodeStatus): Color = when (s) {
    NodeStatus.NOT_STARTED -> Color(0xFF9E9E9E)
    NodeStatus.IN_PROGRESS -> Color(0xFF42A5F5)
    NodeStatus.COMPLETED -> Color(0xFF66BB6A)
    NodeStatus.NEEDS_REVISION -> Color(0xFFFFA726)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodePanel(vm: CanvasViewModel, node: NodeEntity, onEdit: () -> Unit, onStartFocus: () -> Unit, onHistory: () -> Unit, onRecall: () -> Unit, onReview: () -> Unit, onProgress: () -> Unit, onDetox: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(node.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), maxLines = 1)
                Text(statusLabel(node.status), style = MaterialTheme.typography.labelMedium, color = statusColor(node.status))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Est ${node.estimatedSessions} · Done ${node.completedSessions} · ${formatDuration(node.totalFocusSeconds)} focused",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (node.estimatedSessions > 0) {
                val frac = (node.completedSessions.toFloat() / node.estimatedSessions).coerceIn(0f, 1f)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onStartFocus, label = { Text("Start focus") })
                AssistChip(onClick = onRecall, label = { Text("Create Recall Question") })
                AssistChip(onClick = onReview, label = { Text("Review Questions") })
                AssistChip(onClick = onProgress, label = { Text("Learning Progress") })
                AssistChip(onClick = onDetox, label = { Text("Detox profile") })
                AssistChip(onClick = { vm.addChild(node.id) }, label = { Text("Child") })
                AssistChip(onClick = { vm.addSibling(node.id) }, label = { Text("Sibling") })
                AssistChip(onClick = onEdit, label = { Text("Edit") })
                AssistChip(onClick = onEdit, label = { Text("Notes") })
                AssistChip(onClick = onHistory, label = { Text("History") })
                AssistChip(onClick = { vm.toggleCollapse(node.id) }, label = { Text(if (node.collapsed) "Expand" else "Collapse") })
                AssistChip(onClick = { vm.deleteNode(node.id) }, label = { Text("Delete") })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditDialog(node: NodeEntity, onDismiss: () -> Unit, onSave: (NodeEntity) -> Unit) {
    var title by remember(node.id) { mutableStateOf(node.title) }
    var description by remember(node.id) { mutableStateOf(node.description) }
    var notes by remember(node.id) { mutableStateOf(node.notes) }
    var estimated by remember(node.id) { mutableStateOf(node.estimatedSessions.toString()) }
    var status by remember(node.id) { mutableStateOf(node.status) }
    var priority by remember(node.id) { mutableStateOf(node.priority) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit node") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Personal notes") }, minLines = 3)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = estimated, onValueChange = { estimated = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Estimated focus sessions") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                Text("Status", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NodeStatus.entries.forEach { st ->
                        FilterChip(selected = status == st, onClick = { status = st }, label = { Text(statusLabel(st)) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "None", 1 to "Low", 2 to "Med", 3 to "High").forEach { (v, lab) ->
                        FilterChip(selected = priority == v, onClick = { priority = v }, label = { Text(lab) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(node.copy(title = title.ifBlank { node.title }, description = description, notes = notes,
                    estimatedSessions = estimated.toIntOrNull() ?: 0, status = status, priority = priority))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
