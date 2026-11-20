package com.privacyhub.app.ui.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.domain.model.NodeStatus
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.domain.model.SessionType
import com.privacyhub.app.util.formatDuration

@Composable
fun ProjectStatsDialog(nodes: List<NodeEntity>, sessions: List<FocusSessionEntity>, onDismiss: () -> Unit) {
    val focus = sessions.filter { it.status == SessionStatus.COMPLETED && it.type == SessionType.FOCUS }
    val totalFocus = focus.sumOf { it.actualDurationSeconds.toLong() }
    val completed = nodes.count { it.status == NodeStatus.COMPLETED }
    val inProgress = nodes.count { it.status == NodeStatus.IN_PROGRESS }
    val revision = nodes.count { it.status == NodeStatus.NEEDS_REVISION }
    val notStarted = nodes.count { it.status == NodeStatus.NOT_STARTED }
    val completion = if (nodes.isNotEmpty()) completed * 100 / nodes.size else 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Project statistics") },
        text = {
            Column {
                StatRow("Total focus time", formatDuration(totalFocus))
                StatRow("Completed sessions", focus.size.toString())
                StatRow("Total nodes", nodes.size.toString())
                StatRow("Completed nodes", completed.toString())
                StatRow("In progress", inProgress.toString())
                StatRow("Needs revision", revision.toString())
                StatRow("Not started", notStarted.toString())
                StatRow("Node completion", "$completion%")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
