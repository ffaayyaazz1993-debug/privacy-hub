package com.privacyhub.app.ui.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.repo.FocusMapRepository
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.ui.repository
import com.privacyhub.app.util.formatDuration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NodeHistoryViewModel(repo: FocusMapRepository, nodeId: Long) : ViewModel() {
    val sessions: StateFlow<List<FocusSessionEntity>> = repo.sessions.observeByNode(nodeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun NodeHistoryDialog(nodeId: Long, onDismiss: () -> Unit) {
    val repo = repository()
    val vm: NodeHistoryViewModel = viewModel(factory = viewModelFactory { initializer { NodeHistoryViewModel(repo, nodeId) } })
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Session history") },
        text = {
            if (sessions.isEmpty()) Text("No sessions for this node yet.")
            else LazyColumn {
                items(sessions, key = { it.id }) { s ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(s.startTimestamp)),
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(formatDuration(s.actualDurationSeconds.toLong()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(when (s.status) { SessionStatus.COMPLETED -> "Completed"; SessionStatus.STOPPED -> "Ended early"; SessionStatus.SKIPPED -> "Skipped"; else -> s.status.name },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!s.review.isNullOrBlank()) Text("“${s.review}”", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
