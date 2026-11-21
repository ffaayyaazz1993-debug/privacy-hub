package com.privacyhub.app.ui.detox

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacyhub.app.ui.repository

private val PROTECTED = setOf("com.android.settings", "com.android.systemui")

/** Launchable-app picker using the narrow MAIN/LAUNCHER queries declaration. */
@Composable
fun AppPickerDialog(profileId: Long, onDismiss: () -> Unit,
                    onAdd: (String, String) -> Unit, onRemove: (String) -> Unit) {
    val context = LocalContext.current
    val repo = repository()
    val selected by repo.observeDetoxApps(profileId).collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    val apps = remember(query) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || pkg in PROTECTED || pkg.contains("launcher") ||
                    pkg.contains("systemui")) return@mapNotNull null
                Triple(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm))
            }
            .sortedBy { it.second.lowercase() }
            .filter { query.isBlank() || it.second.contains(query, true) || it.first.contains(query, true) }
    }
    val selectedPkgs = selected.map { it.packageName }.toSet()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Restrict apps (${selected.size} selected)") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search apps") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                LazyColumn(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    items(apps, key = { it.first }) { (pkg, label, icon) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Image(bitmap = remember(pkg) { icon.toBitmap(48, 48).asImageBitmap() },
                                contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(pkg, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            Checkbox(checked = pkg in selectedPkgs, onCheckedChange = {
                                if (pkg in selectedPkgs) onRemove(pkg) else onAdd(pkg, label)
                            })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
