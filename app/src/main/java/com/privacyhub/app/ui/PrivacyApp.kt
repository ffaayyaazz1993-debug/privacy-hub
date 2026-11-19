package com.privacyhub.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privacyhub.app.ReaderActivity
import com.privacyhub.app.privacy.MaskConfig
import com.privacyhub.app.privacy.MaskStore
import com.privacyhub.app.service.MaskService

private const val MIN_SIZE = 0.06f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPrivacyApp() {
    val context = LocalContext.current
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var enabled by remember { mutableStateOf(MaskStore.isEnabled(context)) }
    var config by remember { mutableStateOf(MaskStore.load(context)) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlay = Settings.canDrawOverlays(context)
    }

    LaunchedEffect(enabled, hasOverlay) {
        if (enabled && hasOverlay) MaskService.start(context)
    }

    fun update(transform: (MaskConfig) -> MaskConfig) {
        val next = MaskStore.normalize(transform(config))
        config = next
        MaskStore.save(context, next)
        MaskService.update(context)
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        MaskStore.setEnabled(context, on)
        if (on) MaskService.start(context) else MaskService.stop(context)
    }

    fun openOverlaySettings() {
        overlayLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)
            )
        )
    }

    fun setWidth(value: Float) {
        val c = config
        val cx = (c.left + c.right) / 2f
        val half = (value / 2f).coerceAtMost(0.5f)
        var left = cx - half
        var right = cx + half
        if (left < 0f) {
            right -= left
            left = 0f
        }
        if (right > 1f) {
            left -= right - 1f
            right = 1f
        }
        update { it.copy(left = left.coerceIn(0f, 1f), right = right.coerceIn(0f, 1f)) }
    }

    fun setHeight(value: Float) {
        val c = config
        val cy = (c.top + c.bottom) / 2f
        val half = (value / 2f).coerceAtMost(0.5f)
        var top = cy - half
        var bottom = cy + half
        if (top < 0f) {
            bottom -= top
            top = 0f
        }
        if (bottom > 1f) {
            top -= bottom - 1f
            bottom = 1f
        }
        update { it.copy(top = top.coerceIn(0f, 1f), bottom = bottom.coerceIn(0f, 1f)) }
    }

    fun setCenterX(value: Float) {
        val c = config
        val w = (c.right - c.left).coerceAtLeast(MIN_SIZE)
        val left = value - w / 2f
        val right = left + w
        if (left < 0f) {
            update { it.copy(left = 0f, right = w) }
        } else if (right > 1f) {
            update { it.copy(left = 1f - w, right = 1f) }
        } else {
            update { it.copy(left = left, right = right) }
        }
    }

    fun setCenterY(value: Float) {
        val c = config
        val h = (c.bottom - c.top).coerceAtLeast(MIN_SIZE)
        val top = value - h / 2f
        val bottom = top + h
        if (top < 0f) {
            update { it.copy(top = 0f, bottom = h) }
        } else if (bottom > 1f) {
            update { it.copy(top = 1f - h, bottom = 1f) }
        } else {
            update { it.copy(top = top, bottom = bottom) }
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Smart Privacy Screen", fontWeight = FontWeight.Bold) }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Privacy mask",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (enabled) "Active over other apps"
                                else "Stopped",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { on ->
                                if (on && !hasOverlay) openOverlaySettings() else setEnabled(on)
                            }
                        )
                    }
                }

                if (!hasOverlay) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Overlay permission required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Smart Privacy Screen draws a blackout layer over other apps. " +
                                    "Grant Display over other apps to enable it.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { openOverlaySettings() }) {
                                Text("Grant overlay permission")
                            }
                        }
                    }
                }

                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Quick actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { MaskService.conceal(context) }) {
                                Text("Conceal")
                            }
                            OutlinedButton(onClick = { MaskService.reveal(context) }) {
                                Text("Reveal")
                            }
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(context, ReaderActivity::class.java))
                            }) {
                                Text("Private reader")
                            }
                        }
                    }
                }

                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Presets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                update {
                                    MaskConfig(
                                        dimAlpha = it.dimAlpha,
                                        cornerRadiusDp = 24f,
                                    )
                                }
                            }) { Text("Center") }
                            OutlinedButton(onClick = {
                                update {
                                    it.copy(
                                        left = 0.10f,
                                        top = 0.10f,
                                        right = 0.90f,
                                        bottom = 0.42f,
                                        cornerRadiusDp = 16f,
                                    )
                                }
                            }) { Text("Top strip") }
                            OutlinedButton(onClick = {
                                update {
                                    it.copy(
                                        left = 0.30f,
                                        top = 0.38f,
                                        right = 0.70f,
                                        bottom = 0.62f,
                                        cornerRadiusDp = 24f,
                                    )
                                }
                            }) { Text("Small") }
                        }
                    }
                }

                SliderCard(
                    title = "Darkness",
                    valueLabel = "${(config.dimAlpha * 100).toInt()}%",
                    value = config.dimAlpha,
                    range = 0.30f..0.98f,
                    onChange = { v -> update { it.copy(dimAlpha = v) } }
                )
                SliderCard(
                    title = "Window width",
                    valueLabel = "${((config.right - config.left) * 100).toInt()}%",
                    value = config.right - config.left,
                    range = MIN_SIZE..1.0f,
                    onChange = { setWidth(it) }
                )
                SliderCard(
                    title = "Window height",
                    valueLabel = "${((config.bottom - config.top) * 100).toInt()}%",
                    value = config.bottom - config.top,
                    range = MIN_SIZE..1.0f,
                    onChange = { setHeight(it) }
                )
                SliderCard(
                    title = "Horizontal position",
                    valueLabel = "${(((config.left + config.right) / 2f) * 100).toInt()}%",
                    value = (config.left + config.right) / 2f,
                    range = 0f..1f,
                    onChange = { setCenterX(it) }
                )
                SliderCard(
                    title = "Vertical position",
                    valueLabel = "${(((config.top + config.bottom) / 2f) * 100).toInt()}%",
                    value = (config.top + config.bottom) / 2f,
                    range = 0f..1f,
                    onChange = { setCenterY(it) }
                )
                SliderCard(
                    title = "Corner radius",
                    valueLabel = "${config.cornerRadiusDp.toInt()} dp",
                    value = config.cornerRadiusDp,
                    range = 0f..48f,
                    onChange = { v -> update { it.copy(cornerRadiusDp = v) } }
                )

                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Boundary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "This is a software privacy utility. It reduces how much " +
                                "information is visible; it does not physically block " +
                                "light from reaching side viewers.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderCard(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(valueLabel)
            }
            Spacer(Modifier.height(6.dp))
            Slider(value = value, onValueChange = onChange, valueRange = range)
        }
    }
}
