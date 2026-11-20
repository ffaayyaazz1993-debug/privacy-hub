package com.privacyhub.app.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.ui.repository
import com.privacyhub.app.util.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsScreen() {
    val repo = repository()
    val vm: AnalyticsViewModel = viewModel(factory = viewModelFactory { initializer { AnalyticsViewModel(repo) } })
    val today by vm.todaySeconds.collectAsStateWithLifecycle()
    val week by vm.weekSeconds.collectAsStateWithLifecycle()
    val count by vm.totalCompleted.collectAsStateWithLifecycle()
    val daily by vm.daily.collectAsStateWithLifecycle()
    val byProject by vm.byProject.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val byNode by vm.byNode.collectAsStateWithLifecycle()
    val projectProgress by vm.projectProgress.collectAsStateWithLifecycle()
    val maxDay = daily.maxOfOrNull { it.seconds } ?: 0L

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item { Text("Analytics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Today", formatDuration(today), Modifier.weight(1f))
                StatCard("This week", formatDuration(week), Modifier.weight(1f))
                StatCard("Sessions", count.toString(), Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        daily.forEach { d ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                val frac = if (maxDay > 0) d.seconds.toFloat() / maxDay else 0f
                                Box(Modifier.fillMaxWidth().height((90 * frac).coerceAtLeast(2f).dp)
                                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
                                Spacer(Modifier.height(4.dp))
                                Text(d.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item { Text("Time by project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (byProject.isEmpty()) {
            item { Text("No focus recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            val maxP = byProject.maxOfOrNull { it.seconds } ?: 1L
            items(byProject, key = { "p" + it.project.id }) { pf ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(pf.project.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(formatDuration(pf.seconds), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("${pf.sessions} session" + (if (pf.sessions == 1) "" else "s"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { pf.seconds.toFloat() / maxP }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item { Text("Time by node", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (byNode.isEmpty()) {
            item { Text("Complete a focus session on a node to see its time here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            val maxN = byNode.maxOfOrNull { it.seconds } ?: 1L
            items(byNode, key = { "n" + it.nodeId }) { nf ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(nf.nodeTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(formatDuration(nf.seconds), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("${nf.projectTitle} · ${nf.sessions} session" + (if (nf.sessions == 1) "" else "s"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { nf.seconds.toFloat() / maxN }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item { Text("Project completion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (projectProgress.isEmpty()) {
            item { Text("Create a project to track completion.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(projectProgress, key = { "c" + it.project.id }) { pp ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(pp.project.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${pp.completionPct}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("${pp.completedNodes}/${pp.totalNodes} nodes complete · ${pp.inProgress} in progress · ${pp.needsRevision} need revision",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { pp.completionPct / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item { Text("Recent activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (history.isEmpty()) {
            item { Text("Sessions you complete will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(history, key = { "h" + it.session.id }) { h ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(h.nodeTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(formatDuration(h.session.actualDurationSeconds.toLong()), style = MaterialTheme.typography.bodyMedium)
                        }
                        val date = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(h.session.startTimestamp))
                        Text("${h.projectTitle} · $date · " + (if (h.session.status == SessionStatus.COMPLETED) "completed" else "ended early"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
