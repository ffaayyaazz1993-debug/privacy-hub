package com.privacyhub.app.ui.focus

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.privacyhub.app.data.db.FocusSessionEntity
import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.domain.model.NodeStatus
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.domain.detox.DetoxCoordinator
import com.privacyhub.app.domain.model.SessionType
import com.privacyhub.app.privacy.MaskStore
import com.privacyhub.app.service.MaskService
import com.privacyhub.app.ui.repository
import com.privacyhub.app.util.formatClock
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FocusScreen(onGoMaps: () -> Unit) {
    val repo = repository()
    val vm: FocusViewModel = viewModel(factory = viewModelFactory { initializer { FocusViewModel(repo) } })
    val active by vm.active.collectAsStateWithLifecycle()
    val remaining by vm.remaining.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val project by vm.activeProject.collectAsStateWithLifecycle()
    val node by vm.activeNode.collectAsStateWithLifecycle()
    val suggested by vm.suggestedNode.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val detoxActs by repo.observeActivations().collectAsStateWithLifecycle(emptyList())
    val detoxProfiles by repo.observeProfiles().collectAsStateWithLifecycle(emptyList())
    val latest by vm.latestCompletedFocus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    var showElapsed by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    var whiteNoiseOn by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val noise = remember { WhiteNoisePlayer() }
    DisposableEffect(Unit) { onDispose { noise.stop() } }

    var refresh by remember { mutableStateOf(0) }
    var maskOn by remember(refresh) { mutableStateOf(MaskStore.isEnabled(context)) }
    val hasOverlay = remember(refresh) { Settings.canDrawOverlays(context) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(fullScreen) {
        val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
        if (fullScreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val s = active
    val isFocus = s?.type == SessionType.FOCUS
    val kind = when (s?.type) {
        SessionType.FOCUS -> "FOCUS"; SessionType.SHORT_BREAK -> "SHORT BREAK"; SessionType.LONG_BREAK -> "LONG BREAK"; else -> "READY"
    }
    val elapsed = if (s != null) (s.plannedDurationSeconds - remaining).coerceAtLeast(0) else 0L
    val displaySeconds = if (showElapsed) elapsed else (s?.plannedDurationSeconds?.toLong() ?: settings.focusDurationSeconds.toLong()).let { planned ->
        if (s != null) (if (showElapsed) elapsed else remaining) else planned
    }
    var notes by remember(s?.id) { mutableStateOf(s?.notes ?: "") }
    LaunchedEffect(notes) { delay(400); if (s != null && notes != s.notes) vm.updateNotes(notes) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onGoMaps) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Back to mind maps") }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Restart session") }, leadingIcon = { Icon(Icons.Rounded.Replay, null) },
                        onClick = { menuOpen = false; if (s != null) vm.restart() })
                    DropdownMenuItem(text = { Text("Open mind maps") }, onClick = { menuOpen = false; onGoMaps() })
                }
            }
        }

        TaskCard(node = if (isFocus) node else null, projectTitle = project?.title, session = s,
            onToggleDone = { n -> vm.toggleNodeDone(n) },
            onDismiss = { if (s != null) vm.stop() else onGoMaps() },
            onPick = onGoMaps)

        val liveDetox = DetoxCoordinator.liveSources(detoxActs, System.currentTimeMillis())
        if (liveDetox.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Digital Detox active", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text(liveDetox.joinToString(" · ") { a ->
                        detoxProfiles.firstOrNull { it.id == a.profileId }?.name ?: "Detox"
                    }, style = MaterialTheme.typography.bodySmall)
                    Text("${liveDetox.size} source(s) protecting this session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        DottedTimerRing(progress = progress, seconds = displaySeconds, label = if (showElapsed) "ELAPSED" else kind)

        Spacer(Modifier.height(20.dp))

        when {
            s == null -> Button(onClick = { if (suggested != null) vm.startSuggested() else onGoMaps() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Start Focus")
            }
            s.status == SessionStatus.RUNNING -> Button(onClick = { vm.pause() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Pause, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Pause")
            }
            s.status == SessionStatus.PAUSED -> Button(onClick = { vm.resume() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Resume")
            }
            else -> Button(onClick = { if (suggested != null) vm.startSuggested() else onGoMaps() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Start Focus")
            }
        }

        if (s != null) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { vm.complete() }) { Icon(Icons.Rounded.Check, contentDescription = "Complete session") }
                IconButton(onClick = { vm.restart() }) { Icon(Icons.Rounded.Replay, contentDescription = "Restart session") }
                IconButton(onClick = { vm.skip() }) { Icon(Icons.Rounded.SkipNext, contentDescription = "Skip session") }
                IconButton(onClick = { vm.stop() }) { Icon(Icons.Rounded.Stop, contentDescription = "End session") }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ModeControl(Icons.Rounded.Lock, "Strict Mode", maskOn) {
                if (!maskOn && !hasOverlay) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                } else {
                    val next = !maskOn
                    if (next) MaskService.start(context) else MaskService.stop(context)
                    MaskStore.setEnabled(context, next); maskOn = next
                }
            }
            ModeControl(Icons.Rounded.Timer, "Timer Mode", showElapsed) { showElapsed = !showElapsed }
            ModeControl(Icons.Rounded.Fullscreen, "Full Screen", fullScreen) { fullScreen = !fullScreen }
            ModeControl(Icons.Rounded.VolumeUp, "White Noise", whiteNoiseOn) {
                whiteNoiseOn = !whiteNoiseOn
                if (whiteNoiseOn) noise.start() else noise.stop()
            }
        }

        val l = latest
        if (l != null && l.id != vm.reviewDismissedId && System.currentTimeMillis() - (l.endTimestamp ?: 0L) < 600_000L) {
            Spacer(Modifier.height(16.dp))
            LearningReviewCard(l, vm)
        }
        if (s != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Session notes (optional)") }, minLines = 2)
        }
    }
}

@Composable
private fun ModeControl(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Icon(icon, contentDescription = label, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DottedTimerRing(progress: Float, seconds: Long, label: String) {
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.outlineVariant
    val ticks = 60
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(264.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2
            val r = minOf(size.width, size.height) * 0.44f
            for (i in 0 until ticks) {
                val angle = (-90f + i * 360f / ticks) * (Math.PI / 180.0).toFloat()
                val active = i.toFloat() / ticks <= progress
                val outer = r
                val inner = r - if (i % 5 == 0) 14.dp.toPx() else 8.dp.toPx()
                drawLine(
                    color = if (active) primary else dim,
                    start = Offset(cx + inner * cos(angle), cy + inner * sin(angle)),
                    end = Offset(cx + outer * cos(angle), cy + outer * sin(angle)),
                    strokeWidth = if (i % 5 == 0) 3.dp.toPx() else 1.5.dp.toPx(),
                )
            }
            drawArc(color = primary, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false, topLeft = Offset(cx - r + 20.dp.toPx(), cy - r + 20.dp.toPx()),
                size = Size(2 * (r - 20.dp.toPx()), 2 * (r - 20.dp.toPx())), style = Stroke(width = 2.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatClock(seconds), fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskCard(node: NodeEntity?, projectTitle: String?, session: FocusSessionEntity?,
                     onToggleDone: (NodeEntity) -> Unit, onDismiss: () -> Unit, onPick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node != null) {
                    Checkbox(checked = node.status == NodeStatus.COMPLETED, onCheckedChange = { onToggleDone(node) })
                    Column(Modifier.weight(1f)) {
                        Text(node.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(projectTitle ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text("No topic selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Pick a mind-map node to link this session", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss") }
            }
            val done = node?.completedSessions ?: session?.let { 0 } ?: 0
            val planned = node?.estimatedSessions ?: 0
            val dots = if (planned > 0) minOf(planned, 8) else minOf(done, 8)
            if (dots > 0) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(dots) { i ->
                        Box(Modifier.size(10.dp).padding(1.dp),
                            contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(8.dp)) {
                                drawCircle(color = if (i < done) Color(0xFFE57373) else Color(0x55E57373))
                            }
                        }
                    }
                }
            }
            if (node == null) TextButton(onClick = onPick) { Text("Choose a node") }
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
