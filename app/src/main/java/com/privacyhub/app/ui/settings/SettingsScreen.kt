package com.privacyhub.app.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.privacyhub.app.data.export.BackupManager
import com.privacyhub.app.privacy.MaskConfig
import com.privacyhub.app.privacy.MaskStore
import com.privacyhub.app.service.MaskService
import com.privacyhub.app.ui.repository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val repo = repository()
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(repo) } })
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Card { Column(Modifier.padding(16.dp)) {
            Text("Timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            DurationRow("Focus", s.focusDurationSeconds / 60, 5f, 90f) { m -> vm.update { it.copy(focusDurationSeconds = m * 60) } }
            DurationRow("Short break", s.shortBreakSeconds / 60, 1f, 15f) { m -> vm.update { it.copy(shortBreakSeconds = m * 60) } }
            DurationRow("Long break", s.longBreakSeconds / 60, 5f, 30f) { m -> vm.update { it.copy(longBreakSeconds = m * 60) } }
            DurationRow("Long break after", s.longBreakInterval, 2f, 8f, unit = "sessions") { n -> vm.update { it.copy(longBreakInterval = n) } }
        } }

        Card { Column(Modifier.padding(16.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                    FilterChip(selected = s.theme == value, onClick = { vm.update { it.copy(theme = value) } }, label = { Text(label) })
                }
            }
        } }

        Card { Column(Modifier.padding(16.dp)) {
            Text("Sound & notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SwitchRow("Sound", s.soundEnabled) { v -> vm.update { it.copy(soundEnabled = v) } }
            SwitchRow("Session notifications", s.notificationEnabled) { v -> vm.update { it.copy(notificationEnabled = v) } }
        } }

        FocusWindowCard()

        DataCard()

        Card { Column(Modifier.padding(16.dp)) {
            Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("FocusMap connects mind maps to focused work. Think → Organise → Focus → Execute → Measure → Review.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Local-first: no account, no internet required.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DurationRow(label: String, value: Int, min: Float, max: Float, unit: String = "min", onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("$label: $value $unit", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = min..max, steps = (max - min).toInt() - 1)
    }
}

/** Preserves the existing privacy overlay as the optional Focus Window. */
@Composable
private fun FocusWindowCard() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("smart_privacy_prefs", Context.MODE_PRIVATE) }
    var refresh by remember { mutableStateOf(0) }
    var maskOn by remember(refresh) { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var hasOverlay by remember(refresh) { mutableStateOf(Settings.canDrawOverlays(context)) }
    var config by remember(refresh) { mutableStateOf(MaskStore.load(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    fun setMask(on: Boolean) {
        if (on) {
            if (!Settings.canDrawOverlays(context)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                return
            }
            MaskService.start(context)
        } else MaskService.stop(context)
        maskOn = on
        prefs.edit().putBoolean("enabled", on).apply()
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Focus Window", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Distraction-reduction scrim with a viewing window", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = maskOn, onCheckedChange = { setMask(it) })
            }
            Spacer(Modifier.height(8.dp))
            Text("Overlay permission: " + (if (hasOverlay) "granted" else "NOT granted"), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }) { Text(if (hasOverlay) "Overlay ✓" else "Grant overlay") }
            Spacer(Modifier.height(8.dp))
            Text("Blackout darkness: ${(config.dimAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(value = config.dimAlpha, onValueChange = { v ->
                config = MaskStore.normalize(config.copy(dimAlpha = v)); MaskStore.save(context, config)
            }, valueRange = 0.30f..0.98f)
            Text("The viewing window stays uncovered, so touches pass through to the app beneath — no accessibility needed. " +
                "This reduces visible distraction; it is not an optical privacy filter or an app blocker.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}


@Composable
private fun DataCard() {
    val context = LocalContext.current
    val repo = repository()
    val scope = rememberCoroutineScope()
    val backup = remember { BackupManager(repo) }
    var msg by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = backup.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                msg = "Exported backup"
            }.onFailure { msg = "Export failed: ${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@launch
                val n = backup.importJson(json)
                msg = "Imported $n project(s)"
            }.onFailure { msg = "Import failed: ${it.message}" }
        }
    }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Data & backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Export projects, mind maps and session history as open JSON, or restore a backup. Import adds to your existing data.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("focusmap-backup.json") }) { Text("Export") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("Import") }
            }
            msg?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
