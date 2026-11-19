package com.privacyhub.app.ui

import android.content.Context
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.privacyhub.app.ReaderActivity
import com.privacyhub.app.privacy.MaskConfig
import com.privacyhub.app.privacy.MaskStore
import com.privacyhub.app.service.MaskService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPrivacyApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("smart_privacy_prefs", Context.MODE_PRIVATE) }

    var config by remember { mutableStateOf(MaskStore.load(context)) }
    var maskOn by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var refresh by remember { mutableStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasOverlay = remember(refresh) { Settings.canDrawOverlays(context) }

    fun update(transform: (MaskConfig) -> MaskConfig) {
        val next = MaskStore.normalize(transform(config))
        config = next
        MaskStore.save(context, next)
    }

    fun setMask(on: Boolean) {
        if (on) {
            if (!Settings.canDrawOverlays(context)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                return
            }
            MaskService.start(context)
        } else {
            MaskService.stop(context)
        }
        maskOn = on
        prefs.edit().putBoolean("enabled", on).apply()
    }

    LaunchedEffect(maskOn, hasOverlay) {
        if (maskOn && hasOverlay) MaskService.start(context)
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Smart Privacy Screen", fontWeight = FontWeight.Bold) }) }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // System-wide mask over other apps
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("System-wide mask", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Blackout over other apps with a viewing window", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = maskOn, onCheckedChange = { setMask(it) })
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Overlay permission: " + (if (hasOverlay) "granted" else "NOT granted"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        }) { Text(if (hasOverlay) "Overlay ✓" else "Grant overlay") }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The viewing window is left uncovered, so touches there go straight " +
                                "to the app underneath — no accessibility needed. Grant overlay, " +
                                "then switch the mask on.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // In-app private reader
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Private Reader (in-app)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Read web pages through the viewing window with smooth, direct " +
                                "touch. No accessibility needed.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { context.startActivity(Intent(context, ReaderActivity::class.java)) }) {
                            Text("Open Private Reader")
                        }
                    }
                }

                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Default darkness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${(config.dimAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = config.dimAlpha,
                            onValueChange = { v -> update { it.copy(dimAlpha = v) } },
                            valueRange = 0.30f..0.98f
                        )
                    }
                }

                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Window presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { update { MaskConfig(dimAlpha = it.dimAlpha) } }) { Text("Center") }
                            OutlinedButton(onClick = {
                                update { it.copy(left = 0.10f, top = 0.10f, right = 0.90f, bottom = 0.42f, cornerRadiusDp = 16f) }
                            }) { Text("Top strip") }
                            OutlinedButton(onClick = {
                                update { it.copy(left = 0.30f, top = 0.38f, right = 0.70f, bottom = 0.62f) }
                            }) { Text("Small") }
                        }
                    }
                }

                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Boundary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "System-wide tap-through works only via the accessibility relay and " +
                                "has a slight delay. This reduces visible information; it does not " +
                                "physically block side-view light.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
