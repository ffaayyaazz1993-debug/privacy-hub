package com.privacyhub.app.ui.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.privacyhub.app.ui.repository

/** M1 placeholder: lists the project's real nodes; the interactive canvas lands in M2. */
@Composable
fun CanvasScreen(projectId: Long, onBack: () -> Unit) {
    val repo = repository()
    val vm: CanvasViewModel = viewModel(factory = viewModelFactory { initializer { CanvasViewModel(repo, projectId) } })
    val project by vm.project.collectAsStateWithLifecycle()
    val nodes by vm.nodes.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(project?.title ?: "Mind map", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${nodes.size} node" + (if (nodes.size == 1) "" else "s") + " · interactive canvas coming next",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.padding(top = 12.dp)) {
            items(nodes, key = { it.id }) { n ->
                Text("• ${n.title}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
