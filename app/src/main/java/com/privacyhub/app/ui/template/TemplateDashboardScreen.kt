package com.privacyhub.app.ui.template

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacyhub.app.data.db.DetoxProfileEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.ProjectTemplateEntity
import com.privacyhub.app.domain.model.NodeStatus
import com.privacyhub.app.domain.template.WorkspaceTemplate
import com.privacyhub.app.domain.template.WorkspaceTemplates
import com.privacyhub.app.ui.repository
import com.privacyhub.app.util.formatDuration
import kotlinx.coroutines.launch

/**
 * Template-specific view over EXISTING projects/nodes/sessions. No second
 * analytics or timer engine: values are derived from the same Room rows.
 */
@Composable
fun TemplateDashboardScreen(projectId: Long, onBack: () -> Unit, onOpenCanvas: () -> Unit) {
    val repo = repository()
    val scope = rememberCoroutineScope()
    val project by repo.projects.observe(projectId).collectAsStateWithLifecycle(null)
    val nodes by repo.nodes.observeByProject(projectId).collectAsStateWithLifecycle(emptyList())
    val sessions by repo.sessions.observeByProject(projectId).collectAsStateWithLifecycle(emptyList())
    val meta by repo.observeProjectTemplate(projectId).collectAsStateWithLifecycle(null)

    val template: WorkspaceTemplate? = meta?.let { WorkspaceTemplates.ALL.firstOrNull { t -> t.id == it.templateId } }
    val roots = nodes.filter { it.parentId == null }
    val focusByNode = sessions.groupBy { it.nodeId }.mapValues { e -> e.value.sumOf { s -> s.actualDurationSeconds.toLong() } }
    fun descendants(id: Long): Set<Long> {
        val out = mutableSetOf(id)
        var changed = true
        while (changed) {
            changed = false
            nodes.forEach { if (it.parentId in out && out.add(it.id)) changed = true }
        }
        return out
    }
    val totalFocus = sessions.sumOf { it.actualDurationSeconds.toLong() }
    val completed = nodes.count { it.status == NodeStatus.COMPLETED }
    val inProgress = nodes.count { it.status == NodeStatus.IN_PROGRESS }
    val presets = meta?.pomodoroPresets.orEmpty().split(';').mapNotNull { part ->
        val bits = part.split('|'); if (bits.size == 2) bits[0] to (bits[1].toIntOrNull() ?: 0) else null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(project?.title ?: "Workspace", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Text(template?.purpose ?: "Custom project", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Total focus: ${formatDuration(totalFocus)}")
            Text("Nodes: ${nodes.size} · completed: $completed · in progress: $inProgress")
            Text("Sessions recorded: ${sessions.size}")
            if (template?.id == WorkspaceTemplates.INTERVIEW) {
                Text("Target role/company: ${roots.firstOrNull()?.title ?: "not set"}")
            }
            OutlinedButton(onClick = onOpenCanvas) { Text("Open mind map") }
        } }

        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text(when (template?.id) {
                WorkspaceTemplates.STUDY -> "Subject / chapter focus"
                WorkspaceTemplates.WORK -> "Workstream focus"
                WorkspaceTemplates.INTERVIEW -> "Preparation area focus"
                else -> "Branch focus"
            }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            roots.filter { it.parentId == null }.forEach { n ->
                val ids = descendants(n.id)
                val secs = ids.sumOf { focusByNode[it] ?: 0L }
                val childDone = ids.count { nodes.firstOrNull { x -> x.id == it }?.status == NodeStatus.COMPLETED }
                Text("${n.title}: ${formatDuration(secs)} · $childDone/${ids.size} completed",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (roots.isEmpty()) Text("No branches yet. Add nodes on the mind map.",
                style = MaterialTheme.typography.bodySmall)
        } }

        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("Pomodoro presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Suggested by the template; applying changes your global focus duration.",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { (label, minutes) ->
                    FilterChip(selected = false, onClick = {
                        scope.launch {
                            val current = repo.settings.get() ?: com.privacyhub.app.data.db.UserSettingsEntity()
                            repo.settings.upsert(current.copy(focusDurationSeconds = minutes * 60))
                        }
                    }, label = { Text("$label · ${minutes}m") })
                }
            }
        } }

        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("Detox suggestion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(meta?.detoxSuggestion?.ifBlank { "None" } ?: "None", style = MaterialTheme.typography.bodySmall)
            val suggestion = template?.detox
            if (suggestion != null) {
                Button(onClick = {
                    scope.launch {
                        repo.saveProfile(DetoxProfileEntity(name = suggestion.name, description = suggestion.description))
                    }
                }) { Text("Create suggested profile") }
            }
            Text("No apps are restricted until you select them in Detox.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}
