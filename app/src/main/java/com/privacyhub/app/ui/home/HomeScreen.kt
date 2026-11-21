package com.privacyhub.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.privacyhub.app.ui.Routes
import com.privacyhub.app.ui.repository
import com.privacyhub.app.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenProject: (Long) -> Unit, onGoMaps: () -> Unit, onGoFocus: () -> Unit, onGoSearch: () -> Unit) {
    val repo = repository()
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(repo) } })
    val recent by vm.recentProjects.collectAsStateWithLifecycle()
    val todaySeconds by vm.todaySeconds.collectAsStateWithLifecycle()
    val todaySessions by vm.todaySessions.collectAsStateWithLifecycle()
    val active by vm.activeSession.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FocusMap", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onGoSearch) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
            }
            Text("Think → Organise → Focus → Execute → Measure → Review",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Today's focus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(formatDuration(todaySeconds), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("$todaySessions session" + (if (todaySessions == 1) "" else "s") + " completed",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onGoFocus) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text(if (active != null) "  Resume focus" else "  Start focus")
                    }
                }
            }
        }
        if (active != null) {
            item {
                Card { Column(Modifier.padding(16.dp)) {
                    Text("Session in progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Tap Start focus to return to your timer.", style = MaterialTheme.typography.bodySmall)
                } }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent mind maps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onGoMaps) { Text("See all") }
            }
        }
        if (recent.isEmpty()) {
            item {
                Card { Column(Modifier.padding(16.dp)) {
                    Text("No mind maps yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Create your first project to start organising what you want to learn.",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onGoMaps) { Text("Create a mind map") }
                } }
            }
        } else {
            items(recent, key = { it.id }) { p ->
                Card(onClick = { onOpenProject(p.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (p.category.isNotBlank()) Text(p.category, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
