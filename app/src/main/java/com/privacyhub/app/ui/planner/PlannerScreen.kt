package com.privacyhub.app.ui.planner

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacyhub.app.data.db.PlannedSessionEntity
import com.privacyhub.app.data.db.RoutineEntity
import com.privacyhub.app.data.db.WeeklyReflectionEntity
import com.privacyhub.app.domain.export.CalendarEvent
import com.privacyhub.app.domain.export.ExportProject
import com.privacyhub.app.domain.export.OutlineNode
import com.privacyhub.app.domain.export.TextExports
import com.privacyhub.app.domain.spaced.ReviewScheduler
import com.privacyhub.app.ui.export.PdfExporter
import com.privacyhub.app.ui.repository
import com.privacyhub.app.util.formatDuration
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlannerScreen() {
    val repo = repository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val routines by repo.routines.observeAll().collectAsStateWithLifecycle(emptyList())
    val planned by repo.planned.observeAll().collectAsStateWithLifecycle(emptyList())
    val projects by repo.projects.observeAll().collectAsStateWithLifecycle(emptyList())
    val nodes by repo.nodes.observeAll().collectAsStateWithLifecycle(emptyList())
    val sessions by repo.sessions.observeAll().collectAsStateWithLifecycle(emptyList())
    val refs by repo.references.observeAll().collectAsStateWithLifecycle(emptyList())
    var section by remember { mutableStateOf("ROUTINES") }
    var pending by remember { mutableStateOf<Pair<String, suspend (java.io.OutputStream) -> Unit>?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                TextExports.parseOpml(text).forEach { repo.importOpmlProject(it) }
            } catch (_: Exception) { }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val p = pending ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            try { context.contentResolver.openOutputStream(uri)?.use { p.second(it) } } catch (_: Exception) { }
            pending = null
        }
    }
    LaunchedEffect(Unit) { repo.markMissed(System.currentTimeMillis()) }

    fun exportProjectTree(): List<ExportProject> = projects.map { p ->
        val byParent = nodes.filter { it.projectId == p.id }.groupBy { it.parentId }
        fun build(parentId: Long?): List<OutlineNode> = (byParent[parentId].orEmpty()).map { n ->
            val extra = listOfNotNull(
                n.description.takeIf { it.isNotBlank() },
                n.notes.takeIf { it.isNotBlank() },
                refs.filter { it.nodeId == n.id }.joinToString("; ") { it.title }.takeIf { it.isNotBlank() },
                sessions.filter { it.nodeId == n.id && it.status.name == "COMPLETED" }
                    .takeIf { it.isNotEmpty() }?.let { "Focus: ${formatDuration(it.sumOf { s -> s.actualDurationSeconds.toLong() })} · ${it.size} sessions" },
            ).joinToString(" · ")
            OutlineNode(n.title, extra, build(n.id))
        }
        ExportProject(p.title, p.description, build(null))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Planner", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ROUTINES" to "Routines", "PLAN" to "Plan", "WEEK" to "Weekly review", "EXPORT" to "Export").forEach { (key, label) ->
                FilterChip(selected = section == key, onClick = { section = key }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))

        if (section == "ROUTINES") {
            RoutineEditor(projects, nodes, onAdd = { title, pid, nid, days, dur, target, rem ->
                scope.launch {
                    val id = repo.saveRoutine(RoutineEntity(title = title, projectId = pid, nodeId = nid,
                        weekdays = days, durationMinutes = dur, weeklyTarget = target, reminderMinutes = rem))
                    repo.routines.get(id)?.let { repo.generatePlanned(it, System.currentTimeMillis()) }
                }
            })
            Spacer(Modifier.height(10.dp))
            routines.forEach { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Switch(checked = r.active, onCheckedChange = { scope.launch { repo.saveRoutine(r.copy(active = it)) } })
                        }
                        Text("${projects.firstOrNull { it.id == r.projectId }?.title ?: "?"} · ${r.weekdays} · ${r.durationMinutes}m · target ${r.weeklyTarget}/wk",
                            style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { scope.launch { repo.generatePlanned(r, System.currentTimeMillis()) } }) { Text("Generate") }
                            TextButton(onClick = { scope.launch { repo.deleteRoutine(r.id) } }) { Text("Delete") }
                        }
                    }
                }
            }
        }

        if (section == "PLAN") {
            val fmt = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
            if (planned.isEmpty()) Text("No planned activities yet. Create a routine and generate its schedule.")
            planned.forEach { p ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(nodes.firstOrNull { it.id == p.nodeId }?.title ?: "Node ${p.nodeId}",
                                style = MaterialTheme.typography.titleSmall)
                            Text("${fmt.format(Date(p.scheduledAt))} · ${p.durationMinutes}m · ${p.status}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { scope.launch { repo.reschedulePlanned(p.id, p.scheduledAt + ReviewScheduler.DAY_MS) } }) { Text("+1 day") }
                    }
                }
            }
        }

        if (section == "WEEK") {
            val start = repo.weekStart(System.currentTimeMillis())
            val end = start + 7 * ReviewScheduler.DAY_MS
            val week by repo.reflections.observe(start).collectAsStateWithLifecycle(null)
            val actual = sessions.filter { it.status.name == "COMPLETED" && it.startTimestamp in start until end }
            val plan = planned.filter { it.scheduledAt in start until end }
            Text("Planned: ${plan.sumOf { it.durationMinutes }}m · Actual: ${formatDuration(actual.sumOf { it.actualDurationSeconds.toLong() })}")
            Text("Completed sessions: ${actual.size} · Planned blocks: ${plan.size} · Unfinished: ${plan.count { it.status == "MISSED" || it.status == "PLANNED" }}")
            Text("Projects progressed: ${actual.map { it.projectId }.toSet().size}")
            var a by remember(start) { mutableStateOf(week?.accomplished ?: "") }
            var i by remember(start) { mutableStateOf(week?.incomplete ?: "") }
            var c by remember(start) { mutableStateOf(week?.concepts ?: "") }
            var n by remember(start) { mutableStateOf(week?.priority ?: "") }
            OutlinedTextField(value = a, onValueChange = { a = it }, label = { Text("What did I accomplish?") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = i, onValueChange = { i = it }, label = { Text("What remains incomplete?") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = c, onValueChange = { c = it }, label = { Text("Concepts needing attention") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("Next week's priority") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { scope.launch { repo.saveReflection(WeeklyReflectionEntity(start, a, i, c, n)) } }) { Text("Save reflection") }
        }

        if (section == "EXPORT") {
            val data = exportProjectTree()
            Button(onClick = {
                pending = "opml" to { out -> out.write(TextExports.toOpml(data).toByteArray()) }
                exportLauncher.launch("focusmap-mindmaps.opml")
            }, modifier = Modifier.fillMaxWidth()) { Text("Export mind maps as OPML") }
            Button(onClick = {
                pending = "md" to { out -> out.write(data.joinToString("\n\n") { TextExports.toMarkdown(it) }.toByteArray()) }
                exportLauncher.launch("focusmap-projects.md")
            }, modifier = Modifier.fillMaxWidth()) { Text("Export projects as Markdown") }
            Button(onClick = {
                pending = "pdf" to { out -> PdfExporter.write(data, out) }
                exportLauncher.launch("focusmap-projects.pdf")
            }, modifier = Modifier.fillMaxWidth()) { Text("Export projects as PDF") }
            Button(onClick = {
                val events = planned.mapIndexed { idx, p -> CalendarEvent(p.id, "FocusMap: ${nodes.firstOrNull { it.id == p.nodeId }?.title ?: "Study"}",
                    projects.firstOrNull { it.id == p.projectId }?.title ?: "", p.scheduledAt, p.durationMinutes, 15) }
                pending = "ics" to { out -> out.write(TextExports.toIcs(events).toByteArray()) }
                exportLauncher.launch("focusmap-study-plan.ics")
            }, modifier = Modifier.fillMaxWidth()) { Text("Export study plan as iCalendar (.ics)") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import OPML") }
            Text("JSON remains the primary full-fidelity backup in Settings. OPML keeps titles, descriptions and hierarchy only.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoutineEditor(projects: List<com.privacyhub.app.data.db.ProjectEntity>,
                          nodes: List<com.privacyhub.app.data.db.NodeEntity>,
                          onAdd: (String, Long, Long?, String, Int, Int, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var project by remember { mutableStateOf<Long?>(null) }
    var node by remember { mutableStateOf<Long?>(null) }
    var days by remember { mutableStateOf(setOf(0, 2, 4)) }
    var duration by remember { mutableStateOf("25") }
    var target by remember { mutableStateOf("3") }
    var reminder by remember { mutableStateOf("15") }
    Card {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Routine name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Minutes") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target/wk") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = reminder, onValueChange = { reminder = it }, label = { Text("Remind min") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Text("Days", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { idx, d ->
                    FilterChip(selected = idx in days, onClick = { days = if (idx in days) days - idx else days + idx }, label = { Text(d) })
                }
            }
            Text("Project", style = MaterialTheme.typography.labelMedium)
            projects.forEach { p -> FilterChip(selected = project == p.id, onClick = { project = p.id; node = null }, label = { Text(p.title) }) }
            Text("Node", style = MaterialTheme.typography.labelMedium)
            nodes.filter { project == null || it.projectId == project }.take(8).forEach { n ->
                FilterChip(selected = node == n.id, onClick = { node = n.id }, label = { Text(n.title) })
            }
            Button(onClick = {
                val pid = project ?: return@Button
                onAdd(title.ifBlank { "Study" }, pid, node, days.sorted().joinToString(","),
                    duration.toIntOrNull() ?: 25, target.toIntOrNull() ?: 3, reminder.toIntOrNull() ?: 0)
                title = ""
            }, enabled = project != null, modifier = Modifier.fillMaxWidth()) { Text("Create routine") }
        }
    }
}
