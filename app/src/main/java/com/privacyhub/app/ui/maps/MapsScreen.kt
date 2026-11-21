package com.privacyhub.app.ui.maps

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.privacyhub.app.ui.repository
import com.privacyhub.app.ui.template.TemplatePickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(onOpenProject: (Long) -> Unit) {
    val repo = repository()
    val vm: MapsViewModel = viewModel(factory = viewModelFactory { initializer { MapsViewModel(repo) } })
    val projects by vm.projects.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var renameTarget by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null); Text("  New mind map")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = query, onValueChange = { query = it; vm.setQuery(it) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search projects") })
            Spacer(Modifier.height(12.dp))
            if (projects.isEmpty()) {
                Text("No projects. Tap “New mind map” to create one.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(projects, key = { it.id }) { p ->
                    Card(onClick = { onOpenProject(p.id) }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (p.category.isNotBlank()) Text(p.category, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { menuFor = p.id }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = menuFor == p.id, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuFor = null; renameTarget = p.id })
                                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuFor = null; vm.duplicate(p.id) })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuFor = null; confirmDelete = p.id })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        TemplatePickerDialog(onDismiss = { showCreate = false },
            onCreate = { template, title, category ->
                vm.createFromTemplate(title, template, category, onOpenProject)
            })
    }

    renameTarget?.let { id ->
        val current = projects.find { it.id == id }
        var title by remember(id) { mutableStateOf(current?.title ?: "") }
        AlertDialog(onDismissRequest = { renameTarget = null }, title = { Text("Rename project") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(id, title); renameTarget = null }, enabled = title.isNotBlank()) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } })
    }

    confirmDelete?.let { id ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Delete project?") },
            text = { Text("This deletes the mind map, its nodes and its session history. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.delete(id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } })
    }
}
