package com.privacyhub.app.ui.detox

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacyhub.app.data.db.DetoxProfileEntity
import com.privacyhub.app.data.db.DetoxScheduleEntity
import com.privacyhub.app.domain.detox.DetoxCoordinator
import com.privacyhub.app.detox.DndController
import com.privacyhub.app.detox.DetoxScheduler
import com.privacyhub.app.ui.repository
import kotlinx.coroutines.launch

@Composable
fun DetoxScreen() {
    val context = LocalContext.current
    val repo = repository()
    val scope = rememberCoroutineScope()
    val profiles by repo.observeProfiles().collectAsStateWithLifecycle(emptyList())
    val activations by repo.observeActivations().collectAsStateWithLifecycle(emptyList())
    val schedules by repo.observeSchedules().collectAsStateWithLifecycle(emptyList())
    var refresh by remember { mutableStateOf(0) }
    var restrictedCount by remember { mutableStateOf(0) }
    var pickerFor by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<DetoxProfileEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<DetoxScheduleEntity?>(null) }
    var creatingSchedule by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val live = DetoxCoordinator.liveSources(activations, now)
    LaunchedEffect(live) { restrictedCount = repo.blockedPackages(live.map { it.profileId }).size }
    val accessibilityOn = remember(refresh) {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(context.packageName) == true
    }
    val dndOn = remember(refresh) { DndController.canUse(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Digital Detox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(if (live.isEmpty()) "Detox is off" else "Detox active",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (live.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary)
                Text("Active sources: ${live.size} · Profiles: ${profiles.size} · Schedules: ${schedules.size}")
                Text("Restricted apps across active profiles: $restrictedCount")
                Text("Accessibility guard: ${if (accessibilityOn) "on" else "off — advisory only, apps are NOT blocked"}",
                    style = MaterialTheme.typography.bodySmall)
                Text("Do Not Disturb: ${if (dndOn) "available" else "not granted (optional)"}",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Enable guard") }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }) { Text("DND access") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Manual Detox session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(30 to "30 min", 60 to "60 min", 90 to "90 min").forEach { (min, label) ->
                OutlinedButton(enabled = profiles.isNotEmpty(), onClick = {
                    scope.launch { profiles.firstOrNull()?.let { repo.startManualDetox(it.id, min) } }
                }) { Text(label) }
            }
        }
        live.filter { it.sourceType == "MANUAL" }.forEach { a ->
            Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profiles.firstOrNull { it.id == a.profileId }?.name ?: "Detox")
                        Text("Ends ${java.text.DateFormat.getTimeInstance().format(java.util.Date(a.endsAt))}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { scope.launch { repo.endManualDetox(a.id) } }) { Text("End") }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Button(onClick = { creating = true }) { Text("New profile") }
        }
        if (!accessibilityOn) Text(
            "The guard is disabled. Detox will still record sessions, but apps cannot be restricted until accessibility is enabled.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        profiles.forEach { p -> ProfileCard(p, repo, scope, onApps = { pickerFor = p.id }, onEdit = { editing = p }) }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Schedules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Button(onClick = { creatingSchedule = true }, enabled = profiles.isNotEmpty()) { Text("New schedule") }
        }
        schedules.forEach { s ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                    Text("Days ${s.weekdays} · ${String.format("%02d:%02d", s.startMinute / 60, s.startMinute % 60)}–${String.format("%02d:%02d", s.endMinute / 60, s.endMinute % 60)}",
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { editingSchedule = s }) { Text("Edit") }
                        TextButton(onClick = { scope.launch { repo.deleteSchedule(s.id) } }) { Text("Delete") }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        ProfileEditorDialog(profile = editing, onDismiss = { creating = false; editing = null },
            onSave = { name, desc, pause, dnd, emergency, auto, pid ->
                val existing = editing
                scope.launch {
                    val id = repo.saveProfile((existing ?: DetoxProfileEntity(name = name, projectId = pid)).copy(
                        name = name, description = desc, pauseBehaviour = pause,
                        dndEnabled = dnd, emergencyEnabled = emergency, autoActivate = auto))
                    if (existing == null && id > 0) pickerFor = id
                    creating = false; editing = null
                }
            })
    }
    pickerFor?.let { pid ->
        AppPickerDialog(profileId = pid, onDismiss = { pickerFor = null },
            onAdd = { pkg, label -> scope.launch { repo.addBlockedApp(pid, pkg, label) } },
            onRemove = { pkg -> scope.launch { repo.removeBlockedApp(pid, pkg) } })
    }
    if (creatingSchedule || editingSchedule != null) {
        ScheduleEditorDialog(schedule = editingSchedule, profiles = profiles, onDismiss = { creatingSchedule = false; editingSchedule = null },
            onSave = { name, pid, days, start, end ->
                scope.launch {
                    repo.saveSchedule((editingSchedule ?: DetoxScheduleEntity(name = name, profileId = pid)).copy(
                        name = name, profileId = pid, weekdays = days, startMinute = start, endMinute = end))
                    DetoxScheduler.scheduleAll(context)
                    creatingSchedule = false; editingSchedule = null
                }
            })
    }
}

@Composable
private fun ProfileCard(p: DetoxProfileEntity, repo: com.privacyhub.app.data.repo.FocusMapRepository,
                        scope: kotlinx.coroutines.CoroutineScope, onApps: () -> Unit, onEdit: () -> Unit) {
    val apps by repo.observeDetoxApps(p.id).collectAsStateWithLifecycle(emptyList())
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (p.isDefault) Text("Default", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            Text("${p.description.ifBlank { "No description" }} · ${if (p.pauseBehaviour == "KEEP") "keeps Detox on pause" else "suspends on pause"} · DND ${if (p.dndEnabled) "on" else "off"}",
                style = MaterialTheme.typography.bodySmall)
            Text("${apps.size} restricted apps · auto ${if (p.autoActivate) "on" else "off"}",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onApps) { Text("Apps") }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = { scope.launch { repo.duplicateProfile(p.id) } }) { Text("Duplicate") }
                if (!p.isDefault) TextButton(onClick = { scope.launch { repo.saveProfile(p.copy(isDefault = true)) } }) { Text("Set default") }
                TextButton(onClick = { scope.launch { repo.deleteProfile(p.id) } }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(profile: DetoxProfileEntity?, onDismiss: () -> Unit,
                                onSave: (String, String, String, Boolean, Boolean, Boolean, Long?) -> Unit) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var desc by remember { mutableStateOf(profile?.description ?: "") }
    var keepOnPause by remember { mutableStateOf(profile?.pauseBehaviour != "SUSPEND") }
    var dnd by remember { mutableStateOf(profile?.dndEnabled ?: false) }
    var emergency by remember { mutableStateOf(profile?.emergencyEnabled ?: true) }
    var auto by remember { mutableStateOf(profile?.autoActivate ?: true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (profile == null) "New Detox profile" else "Edit profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                ToggleRow("Keep Detox active while Pomodoro is paused", keepOnPause) { keepOnPause = it }
                ToggleRow("Use Do Not Disturb during Detox", dnd) { dnd = it }
                ToggleRow("Allow emergency override", emergency) { emergency = it }
                ToggleRow("Auto-activate during Pomodoro", auto) { auto = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, desc, if (keepOnPause) "KEEP" else "SUSPEND", dnd, emergency, auto, profile?.projectId) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ScheduleEditorDialog(schedule: DetoxScheduleEntity?, profiles: List<DetoxProfileEntity>,
                                 onDismiss: () -> Unit,
                                 onSave: (String, Long, String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf(schedule?.name ?: "") }
    var profileId by remember { mutableStateOf(schedule?.profileId ?: profiles.firstOrNull()?.id ?: -1L) }
    var days by remember { mutableStateOf(schedule?.weekdays?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: setOf(0,1,2,3,4,5,6)) }
    var startH by remember { mutableStateOf(((schedule?.startMinute ?: 18*60) / 60).toString()) }
    var startM by remember { mutableStateOf(((schedule?.startMinute ?: 18*60) % 60).toString()) }
    var endH by remember { mutableStateOf(((schedule?.endMinute ?: 19*60) / 60).toString()) }
    var endM by remember { mutableStateOf(((schedule?.endMinute ?: 19*60) % 60).toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (schedule == null) "New Detox schedule" else "Edit schedule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Profile", style = MaterialTheme.typography.labelMedium)
                profiles.forEach { p -> FilterChip(selected = profileId == p.id, onClick = { profileId = p.id }, label = { Text(p.name) }) }
                Text("Days (Mon-first)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("M","T","W","T","F","S","S").forEachIndexed { i, d -> FilterChip(selected = i in days, onClick = { days = if (i in days) days - i else days + i }, label = { Text(d) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = startH, onValueChange = { startH = it }, label = { Text("Start h") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = startM, onValueChange = { startM = it }, label = { Text("m") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = endH, onValueChange = { endH = it }, label = { Text("End h") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = endM, onValueChange = { endM = it }, label = { Text("m") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && profileId > 0, onClick = {
                val sh = (startH.toIntOrNull() ?: 0).coerceIn(0,23); val sm = (startM.toIntOrNull() ?: 0).coerceIn(0,59)
                val eh = (endH.toIntOrNull() ?: 0).coerceIn(0,23); val em = (endM.toIntOrNull() ?: 0).coerceIn(0,59)
                onSave(name, profileId, days.sorted().joinToString(","), sh*60+sm, eh*60+em)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
