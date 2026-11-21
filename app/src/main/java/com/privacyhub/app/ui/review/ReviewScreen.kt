package com.privacyhub.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.privacyhub.app.data.db.FlashcardEntity
import com.privacyhub.app.data.db.SchedulerSettingsEntity
import com.privacyhub.app.domain.spaced.RecallRating
import com.privacyhub.app.domain.spaced.ReviewScheduler
import com.privacyhub.app.ui.repository
import com.privacyhub.app.util.formatDuration

@Composable
fun ReviewScreen(filterNodeId: Long? = null) {
    val repo = repository()
    val vm: ReviewViewModel = viewModel(factory = viewModelFactory { initializer { ReviewViewModel(repo) } })
    val cards by vm.cards.collectAsStateWithLifecycle()
    val due by vm.due.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val nodes by vm.nodes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    var mode by remember { mutableStateOf("DASHBOARD") }
    var queue by remember { mutableStateOf(emptyList<FlashcardEntity>()) }
    var index by remember { mutableStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var cardStart by remember { mutableStateOf(System.currentTimeMillis()) }
    val ratings = remember { mutableMapOf<String, Int>() }
    var overdueOnly by remember { mutableStateOf(false) }
    var newOnly by remember { mutableStateOf(false) }
    var projectFilter by remember { mutableStateOf<Long?>(null) }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<FlashcardEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun projectTitle(id: Long) = projects.firstOrNull { it.id == id }?.title ?: "Project $id"
    fun nodeTitle(id: Long) = nodes.firstOrNull { it.id == id }?.title ?: "Node $id"
    fun startSession() {
        queue = vm.dueFor(filterNodeId, projectFilter, overdueOnly, newOnly)
            .take(settings.reviewLimitPerDay.coerceAtLeast(1))
        index = 0; revealed = false; paused = false
        ratings.clear(); cardStart = System.currentTimeMillis()
        mode = if (queue.isEmpty()) "DASHBOARD" else "SESSION"
    }
    fun rate(rating: RecallRating) {
        val card = queue.getOrNull(index) ?: return
        ratings[rating.name] = (ratings[rating.name] ?: 0) + 1
        vm.rate(card, rating, cardStart)
        if (index + 1 >= queue.size) mode = "SUMMARY" else { index++; revealed = false; cardStart = System.currentTimeMillis() }
    }
    fun intervalLabel(card: FlashcardEntity, rating: RecallRating): String {
        val (due, days) = vm.preview(card, rating)
        val minutes = ((due - vm.now.value) / 60000L).toInt()
        return if (days > 0) "${days}d" else "${minutes.coerceAtLeast(1)}m"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Review", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Active recall, scheduled by FSRS-6", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (mode == "DASHBOARD") {
            Spacer(Modifier.height(12.dp))
            val t = vm.now.value
            val dayStart = t - (t % ReviewScheduler.DAY_MS)
            val dueCount = vm.dueFor(filterNodeId, projectFilter).size
            val overdue = vm.dueFor(filterNodeId, projectFilter, overdueOnly = true).size
            val newCount = vm.dueFor(filterNodeId, projectFilter, newOnly = true).size
            val today = logs.count { it.reviewedAt >= dayStart }
            val seconds = logs.filter { it.reviewedAt >= dayStart }.sumOf { it.elapsedDurationSeconds.toLong() }
            StatGrid(dueCount, overdue, newCount, today)
            Spacer(Modifier.height(10.dp))
            Text("Estimated review time: ${formatDuration((dueCount * 20L).coerceAtMost(3600L))}",
                style = MaterialTheme.typography.bodyMedium)
            val next = cards.filter { it.dueTimestamp > t }.minByOrNull { it.dueTimestamp }
            Text("Next review: ${if (next == null) "none scheduled" else nodeTitle(next.nodeId)}",
                style = MaterialTheme.typography.bodyMedium)
            if (filterNodeId != null) Text("Showing questions for: ${nodeTitle(filterNodeId)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = overdueOnly, onClick = { overdueOnly = !overdueOnly }, label = { Text("Overdue") })
                FilterChip(selected = newOnly, onClick = { newOnly = !newOnly }, label = { Text("New") })
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { startSession() }, modifier = Modifier.fillMaxWidth(),
                enabled = dueCount > 0) { Text("Start review (${dueCount.coerceAtMost(settings.reviewLimitPerDay)})") }
            OutlinedButton(onClick = { mode = "BROWSE" }, modifier = Modifier.fillMaxWidth()) { Text("Browse all cards (${cards.size})") }
            OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("Create recall question") }
            if (cards.isEmpty()) EmptyState("No flashcards yet", "Select a mind-map node and create your first recall question.")
            else if (dueCount == 0) EmptyState("All reviews completed", "Nothing is due right now. Come back when cards are scheduled.")
        }

        if (mode == "BROWSE") {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Search questions and answers") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            val list = cards.filter { c ->
                search.isBlank() || c.question.contains(search, true) || c.answer.contains(search, true)
            }.filter { filterNodeId == null || it.nodeId == filterNodeId }
            if (list.isEmpty()) EmptyState("No cards found", "Try another search or create a question.")
            list.forEach { c -> BrowseCard(c, ::projectTitle, ::nodeTitle, vm,
                onEdit = { editing = c }) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { mode = "DASHBOARD" }, modifier = Modifier.fillMaxWidth()) { Text("Back to dashboard") }
        }

        if (mode == "SESSION") {
            val card = queue.getOrNull(index)
            if (card == null) { mode = "SUMMARY" } else {
                Spacer(Modifier.height(12.dp))
                Text("Card ${index + 1} / ${queue.size}", style = MaterialTheme.typography.labelMedium)
                Text("${projectTitle(card.projectId)} · ${nodeTitle(card.nodeId)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Text(card.question, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (card.hint.isNotBlank() && !revealed) {
                    Spacer(Modifier.height(8.dp)); Text("Hint: ${card.hint}", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(18.dp))
                if (!paused && !revealed) {
                    Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Reveal answer") }
                } else if (!paused) {
                    Text(card.answer, style = MaterialTheme.typography.bodyLarge)
                    if (card.explanation.isNotBlank()) {
                        Spacer(Modifier.height(6.dp)); Text(card.explanation, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Next interval", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RatingButton("Again", intervalLabel(card, RecallRating.AGAIN)) { rate(RecallRating.AGAIN) }
                        RatingButton("Hard", intervalLabel(card, RecallRating.HARD)) { rate(RecallRating.HARD) }
                        RatingButton("Good", intervalLabel(card, RecallRating.GOOD)) { rate(RecallRating.GOOD) }
                        RatingButton("Easy", intervalLabel(card, RecallRating.EASY)) { rate(RecallRating.EASY) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { paused = !paused; cardStart = System.currentTimeMillis() },
                    modifier = Modifier.fillMaxWidth()) { Text(if (paused) "Resume" else "Pause session") }
                TextButton(onClick = { mode = "DASHBOARD" }) { Text("End session") }
            }
        }

        if (mode == "SUMMARY") {
            Spacer(Modifier.height(16.dp))
            Text("Session complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val remembered = (ratings[RecallRating.GOOD.name] ?: 0) + (ratings[RecallRating.EASY.name] ?: 0)
            val forgotten = ratings[RecallRating.AGAIN.name] ?: 0
            Text("Cards reviewed: ${queue.size}")
            Text("Remembered: $remembered")
            Text("Forgotten: $forgotten")
            Text("Duration: ${formatDuration(((System.currentTimeMillis() - cardStart) / 1000).coerceAtLeast(0))}")
            val next = cards.filter { it.dueTimestamp > System.currentTimeMillis() }.minByOrNull { it.dueTimestamp }
            Text("Next review: ${if (next == null) "none" else nodeTitle(next.nodeId)}")
            Spacer(Modifier.height(14.dp))
            Button(onClick = { mode = "DASHBOARD"; vm.refresh() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }

    if (creating || editing != null) {
        CardEditorDialog(
            card = editing,
            nodes = nodes,
            projects = projects,
            defaultNodeId = filterNodeId,
            onDismiss = { creating = false; editing = null },
            onSave = { q, a, h, ex, node ->
                if (editing != null) vm.update(editing!!.copy(question = q, answer = a, hint = h, explanation = ex))
                else vm.create(node.projectId, node.id, q, a, h, ex)
                creating = false; editing = null
            },
        )
    }
}

@Composable
private fun StatGrid(due: Int, overdue: Int, fresh: Int, today: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatCard("Due", due.toString())
        StatCard("Overdue", overdue.toString())
        StatCard("New", fresh.toString())
        StatCard("Today", today.toString())
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Spacer(Modifier.height(28.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun RatingButton(label: String, interval: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label); Text(interval, style = MaterialTheme.typography.labelSmall)
    } }
}

@Composable
private fun BrowseCard(c: FlashcardEntity, projectTitle: (Long) -> String, nodeTitle: (Long) -> String,
                       vm: ReviewViewModel, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(c.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${projectTitle(c.projectId)} · ${nodeTitle(c.nodeId)}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(c.answer, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Add, contentDescription = "Edit card") }
                IconButton(onClick = { vm.duplicate(c) }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate card") }
                IconButton(onClick = { vm.archive(c) }) { Icon(Icons.Rounded.Archive, contentDescription = "Archive card") }
                IconButton(onClick = { vm.delete(c) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete card") }
            }
        }
    }
}

@Composable
private fun CardEditorDialog(card: FlashcardEntity?, nodes: List<com.privacyhub.app.data.db.NodeEntity>,
                             projects: List<com.privacyhub.app.data.db.ProjectEntity>, defaultNodeId: Long?,
                             onDismiss: () -> Unit, onSave: (String, String, String, String, com.privacyhub.app.data.db.NodeEntity) -> Unit) {
    var q by remember { mutableStateOf(card?.question ?: "") }
    var a by remember { mutableStateOf(card?.answer ?: "") }
    var h by remember { mutableStateOf(card?.hint ?: "") }
    var ex by remember { mutableStateOf(card?.explanation ?: "") }
    var selectedNode by remember { mutableStateOf<Long?>(card?.nodeId ?: defaultNodeId) }
    val node = nodes.firstOrNull { it.id == selectedNode }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (card == null) "Create recall question" else "Edit recall question") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = a, onValueChange = { a = it }, label = { Text("Answer") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = h, onValueChange = { h = it }, label = { Text("Hint (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = ex, onValueChange = { ex = it }, label = { Text("Explanation (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Node", style = MaterialTheme.typography.labelMedium)
                nodes.forEach { n ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selectedNode == n.id, onCheckedChange = { if (it) selectedNode = n.id })
                        Text("${n.title} · ${projects.firstOrNull { p -> p.id == n.projectId }?.title ?: ""}",
                            style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (q.isNotBlank() && a.isNotBlank() && node != null) onSave(q, a, h, ex, node) },
            enabled = q.isNotBlank() && a.isNotBlank() && node != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
