package com.privacyhub.app.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.privacyhub.app.ui.repository
import com.privacyhub.app.util.formatDuration

/** Concept-level learning progress. Kept distinct from focus time and node status. */
@Composable
fun NodeLearningDialog(nodeId: Long, nodeTitle: String, focusSeconds: Long, sessions: Int, onDismiss: () -> Unit) {
    val repo = repository()
    val vm: ReviewViewModel = viewModel(factory = viewModelFactory { initializer { ReviewViewModel(repo) } })
    val cards by vm.cards.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val mine = cards.filter { it.nodeId == nodeId }
    val ids = mine.map { it.id }.toSet()
    val myLogs = logs.filter { it.flashcardId in ids }
    val now = System.currentTimeMillis()
    val due = mine.count { it.dueTimestamp <= now }
    val mastered = mine.count { it.learningState == "REVIEW" && it.intervalDays >= 21 }
    val remembered = myLogs.count { it.rating == "GOOD" || it.rating == "EASY" }
    val accuracy = if (myLogs.isEmpty()) 0 else (remembered * 100) / myLogs.size
    val last = mine.mapNotNull { it.lastReviewTimestamp.takeIf { t -> t > 0 } }.maxOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Learning progress") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(nodeTitle, style = MaterialTheme.typography.titleMedium)
                Text("Focus time: ${formatDuration(focusSeconds)} · Sessions: $sessions", style = MaterialTheme.typography.bodySmall)
                Text("Flashcards: ${mine.size} · Due: $due · Mastered (interval ≥21d): $mastered")
                Text("Reviews: ${myLogs.size} · Recall accuracy: $accuracy%")
                Text("Last reviewed: ${if (last == null) "never" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last))}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
