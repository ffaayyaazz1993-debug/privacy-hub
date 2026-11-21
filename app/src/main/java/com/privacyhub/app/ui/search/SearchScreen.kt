package com.privacyhub.app.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacyhub.app.ui.repository

/** Local-only grouped search over projects, nodes, notes, references and flashcards. */
@Composable
fun SearchScreen(onOpenProject: (Long) -> Unit, onOpenNode: (Long, Long) -> Unit) {
    val repo = repository()
    var query by remember { mutableStateOf("") }
    val projects by repo.projects.search(query).collectAsStateWithLifecycle(emptyList())
    val nodes by repo.nodes.search(query).collectAsStateWithLifecycle(emptyList())
    val refs by repo.references.search(query).collectAsStateWithLifecycle(emptyList())
    val cards by repo.cards.search(query).collectAsStateWithLifecycle(emptyList())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Projects, topics, notes, references, questions") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        if (query.isBlank()) { Text("Type to search everything stored locally."); return@Column }
        Section("Projects (${projects.size})") {
            projects.forEach { p -> TextButton(onClick = { onOpenProject(p.id) }) { Text(p.title) } }
        }
        Section("Nodes and notes (${nodes.size})") {
            nodes.forEach { n -> TextButton(onClick = { onOpenNode(n.projectId, n.id) }) { Text("${n.title} · ${n.description}") } }
        }
        Section("References (${refs.size})") {
            refs.forEach { r -> TextButton(onClick = { onOpenNode(r.projectId, r.nodeId) }) { Text(r.title) } }
        }
        Section("Recall questions (${cards.size})") {
            cards.forEach { c -> TextButton(onClick = { onOpenNode(c.projectId, c.nodeId) }) { Text(c.question) } }
        }
        if (projects.isEmpty() && nodes.isEmpty() && refs.isEmpty() && cards.isEmpty()) Text("No local matches.")
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.padding(4.dp)) { content() } }
    Spacer(Modifier.height(8.dp))
}
