package com.privacyhub.app.ui.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacyhub.app.FocusMapApplication
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.db.ProjectEntity
import com.privacyhub.app.ui.theme.FocusMapTheme
import kotlinx.coroutines.launch

/** Share target: stores a reference against a node the user explicitly approves. */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val uri: Uri? = intent?.clipData?.getItemAt(0)?.uri
            ?: (intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        val mime = intent?.type.orEmpty()
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
        }
        val repo = (application as FocusMapApplication).repository
        setContent {
            FocusMapTheme {
                CaptureScreen(repo, text, uri?.toString().orEmpty(), mime,
                    onDone = { finish() })
            }
        }
    }
}

@Composable
private fun CaptureScreen(repo: com.privacyhub.app.data.repo.FocusMapRepository,
                          incomingText: String, uriString: String, mime: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val projects by repo.projects.observeAll().collectAsStateWithLifecycle(emptyList())
    val nodes by repo.nodes.observeAll().collectAsStateWithLifecycle(emptyList())
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(incomingText) }
    var project by remember { mutableStateOf<Long?>(null) }
    var node by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Capture to FocusMap", style = MaterialTheme.typography.headlineMedium)
        Text("Shared content is saved locally. Nothing is uploaded.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        if (uriString.isNotBlank()) Text("Attachment: $mime", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Text / URL") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(Modifier.height(10.dp))
        Text("Project", style = MaterialTheme.typography.titleSmall)
        projects.forEach { p -> Choice(p.title, project == p.id) { project = p.id; node = null } }
        Spacer(Modifier.height(6.dp))
        Text("Node (approve destination)", style = MaterialTheme.typography.titleSmall)
        nodes.filter { project == null || it.projectId == project }.forEach { n -> Choice(n.title, node == n.id) { node = n.id } }
        error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = {
                val nid = node; val pid = project
                if (nid == null || pid == null) { error = "Select a project and a node first"; return@Button }
                scope.launch {
                    repo.createReference(pid, nid, title, note,
                        if (note.trim().startsWith("http")) note.trim() else "", mime, uriString)
                    onDone()
                }
            }, enabled = node != null && project != null) { Text("Save") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Cancel") }
        }
    }
}

@Composable
private fun Choice(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { if (it) onClick() })
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}
