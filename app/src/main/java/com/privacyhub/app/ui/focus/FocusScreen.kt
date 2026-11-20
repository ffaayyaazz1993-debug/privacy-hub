package com.privacyhub.app.ui.focus

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.privacyhub.app.privacy.MaskStore
import com.privacyhub.app.service.MaskService
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.domain.model.SessionType
import com.privacyhub.app.ui.repository
import com.privacyhub.app.util.formatClock
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(onGoMaps: () -> Unit) {
    val repo = repository()
    val vm: FocusViewModel = viewModel(factory = viewModelFactory { initializer { FocusViewModel(repo) } })
    val active by vm.active.collectAsStateWithLifecycle()
    val remaining by vm.remaining.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val project by vm.activeProject.collectAsStateWithLifecycle()
    val node by vm.activeNode.collectAsStateWithLifecycle()
    val latest by vm.latestCompletedFocus.collectAsStateWithLifecycle()

    val s = active
    if (s == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Focus", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Start a focus session from a mind-map node so your time is tied to a topic.",
                style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onGoMaps) { Text("Open mind maps") }
        }
        return
    }

    val isFocus = s.type == SessionType.FOCUS
    val kind = when (s.type) { SessionType.FOCUS -> "FOCUS"; SessionType.SHORT_BREAK -> "SHORT BREAK"; SessionType.LONG_BREAK -> "LONG BREAK" }
    var notes by remember(s.id) { mutableStateOf(s.notes) }
    LaunchedEffect(notes) { delay(400); if (notes != s.notes) vm.updateNotes(notes) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(project?.title ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (isFocus) (node?.title ?: "Focus") else kind, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 10.dp)
            Text(formatClock(remaining), fontSize = 44.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.status == SessionStatus.RUNNING) Button(onClick = { vm.pause() }) { Text("Pause") }
            else Button(onClick = { vm.resume() }) { Text("Resume") }
            OutlinedButton(onClick = { vm.restart() }) { Text("Restart") }
            OutlinedButton(onClick = { vm.skip() }) { Text("Skip") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { vm.complete() }) { Text("Complete") }
            OutlinedButton(onClick = { vm.stop() }) { Text("End session") }
        }
        val l = latest
        if (l != null && l.id != vm.reviewDismissedId && System.currentTimeMillis() - (l.endTimestamp ?: 0L) < 600_000L) {
            Spacer(Modifier.height(14.dp))
            LearningReviewCard(l, vm)
        }
        Spacer(Modifier.height(14.dp))
        FocusWindowRow()
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Session notes (optional)") }, minLines = 2)
    }
}


@Composable
private fun FocusWindowRow() {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var maskOn by remember(refresh) { mutableStateOf(MaskStore.isEnabled(context)) }
    val hasOverlay = remember(refresh) { Settings.canDrawOverlays(context) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Focus Window", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (hasOverlay) "Dim distractions around a viewing window while you work"
                    else "Overlay permission needed — tap the switch to grant",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = maskOn, onCheckedChange = { on ->
                if (on) {
                    if (!Settings.canDrawOverlays(context)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        return@Switch
                    }
                    MaskService.start(context)
                } else MaskService.stop(context)
                maskOn = on
                MaskStore.setEnabled(context, on)
            })
        }
    }
}


@Composable
private fun LearningReviewCard(session: FocusSessionEntity, vm: FocusViewModel) {
    var text by remember(session.id) { mutableStateOf(session.review ?: "") }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text("What did you accomplish in this session?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Quick summary (optional)") }, minLines = 2)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveReview(session.id, text, understood = true, revision = false) }) { Text("Understood") }
                OutlinedButton(onClick = { vm.saveReview(session.id, text, understood = false, revision = true) }) { Text("Needs study") }
                TextButton(onClick = { vm.dismissReview(session.id) }) { Text("Skip") }
            }
        }
    }
}
