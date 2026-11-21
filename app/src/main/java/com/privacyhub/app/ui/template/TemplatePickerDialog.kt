package com.privacyhub.app.ui.template

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.privacyhub.app.domain.template.WorkspaceTemplate
import com.privacyhub.app.domain.template.WorkspaceTemplates

/** One selection screen for all templates; preview is a text tree from the engine. */
@Composable
fun TemplatePickerDialog(onDismiss: () -> Unit, onCreate: (WorkspaceTemplate, String, String) -> Unit) {
    var selected by remember { mutableStateOf(WorkspaceTemplates.ALL.first()) }
    var title by remember { mutableStateOf(WorkspaceTemplates.ALL.first().name) }
    var category by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New mind map — choose a workspace") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WorkspaceTemplates.ALL.forEach { t ->
                        FilterChip(selected = t.id == selected.id, onClick = {
                            val previousName = WorkspaceTemplates.ALL.firstOrNull { it.name == title }?.name
                            selected = t
                            if (title.isBlank() || previousName != null) title = t.name
                        }, label = { Text(t.name) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(selected.purpose, style = MaterialTheme.typography.titleSmall)
                Text(selected.description, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text("Workflow: ${selected.workflow}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text("Preview", style = MaterialTheme.typography.labelLarge)
                Text(WorkspaceTemplates.preview(selected), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Project name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category (optional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onCreate(selected, title.trim(), category.trim()); onDismiss() }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
