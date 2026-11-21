package com.privacyhub.app.ui.detox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacyhub.app.data.db.NodeDetoxEntity
import com.privacyhub.app.ui.repository
import kotlinx.coroutines.launch

/** Per-node profile override plus automatic activation switch. */
@Composable
fun NodeDetoxDialog(nodeId: Long, nodeTitle: String, onDismiss: () -> Unit) {
    val repo = repository()
    val scope = rememberCoroutineScope()
    val profiles by repo.observeProfiles().collectAsStateWithLifecycle(emptyList())
    var chosen by remember { mutableStateOf<Long?>(null) }
    var auto by remember { mutableStateOf(true) }
    LaunchedEffect(nodeId) { repo.nodeDetox(nodeId)?.let { chosen = it.profileId; auto = it.autoActivate } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Detox for $nodeTitle") },
        text = {
            Column {
                Text("No profile means this node uses the project or global default.",
                    style = MaterialTheme.typography.bodySmall)
                profiles.forEach { p ->
                    FilterChip(selected = chosen == p.id, onClick = { chosen = if (chosen == p.id) null else p.id },
                        label = { Text(p.name) }, modifier = Modifier.padding(vertical = 2.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-activate Detox in Pomodoro", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { scope.launch { repo.setNodeDetox(nodeId, chosen, auto); onDismiss() } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
