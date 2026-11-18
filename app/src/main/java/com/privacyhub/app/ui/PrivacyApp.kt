package com.privacyhub.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privacyhub.app.BuildConfig
import com.privacyhub.app.privacy.TiltMath
import com.privacyhub.app.service.OverlayService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyHubApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("privacy_hub_prefs", Context.MODE_PRIVATE)
    }

    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var armed by remember { mutableStateOf(prefs.getBoolean("armed", false)) }
    var sensitivity by remember {
        mutableFloatStateOf(prefs.getFloat("sensitivity", 1.0f))
    }
    var threshold by remember {
        mutableFloatStateOf(prefs.getFloat("threshold", 10.0f))
    }
    var simulateAngle by remember { mutableFloatStateOf(0f) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlay = Settings.canDrawOverlays(context)
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun setArmed(on: Boolean) {
        armed = on
        prefs.edit().putBoolean("armed", on).apply()
        if (on) OverlayService.start(context) else OverlayService.stop(context)
    }

    fun openOverlaySettings() {
        overlayLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)
            )
        )
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Privacy Hub", fontWeight = FontWeight.Bold) }
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
                StatusCard(
                    hasOverlay = hasOverlay,
                    armed = armed,
                    onToggle = { on ->
                        if (on && !hasOverlay) openOverlaySettings() else setArmed(on)
                    }
                )

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
                                "Privacy Hub draws a dim layer over other apps. " +
                                    "Grant “Display over other apps” to enable it.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { openOverlaySettings() }) {
                                Text("Grant overlay permission")
                            }
                        }
                    }
                }

                ControlCard(
                    title = "Sensitivity",
                    subtitle = "Higher values dim at smaller tilts.",
                    valueLabel = String.format("%.1fx", sensitivity),
                    value = sensitivity,
                    range = 0.5f..2.0f,
                    onChange = {
                        sensitivity = it
                        prefs.edit().putFloat("sensitivity", it).apply()
                        OverlayService.refresh(context)
                    }
                )

                ControlCard(
                    title = "Tilt trigger",
                    subtitle = "Tilt needed before dimming starts.",
                    valueLabel = String.format("%.0f degrees", threshold),
                    value = threshold,
                    range = 5f..25f,
                    onChange = {
                        threshold = it
                        prefs.edit().putFloat("threshold", it).apply()
                        OverlayService.refresh(context)
                    }
                )

                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "How it works",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Straight-on viewing stays clear. Tilt the phone " +
                                "left or right and a dark layer fades in over " +
                                "every app. Straighten the phone and it fades out.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (BuildConfig.DEBUG) {
                    DebugCard(
                        angle = simulateAngle,
                        onChange = {
                            simulateAngle = it
                            TiltMath.simulatedAngleDeg = it.toDouble()
                            OverlayService.refresh(context)
                        },
                        onReset = {
                            simulateAngle = 0f
                            TiltMath.simulatedAngleDeg = null
                            OverlayService.refresh(context)
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(
    hasOverlay: Boolean,
    armed: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Privacy screen",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (armed) "Active - dims when tilted sideways"
                    else "Stopped - screen is never dimmed",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (hasOverlay) "Overlay permission: granted"
                    else "Overlay permission: not granted",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = armed, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ControlCard(
    title: String,
    subtitle: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(valueLabel, style = MaterialTheme.typography.titleSmall)
            Slider(value = value, onValueChange = onChange, valueRange = range)
        }
    }
}

@Composable
private fun DebugCard(
    angle: Float,
    onChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Simulate tilt (debug)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Used on emulators or phones without a usable rotation sensor.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(String.format("%.0f degrees", angle))
            Slider(
                value = angle,
                onValueChange = onChange,
                valueRange = 0f..45f
            )
            Button(onClick = onReset) { Text("Stop simulation") }
        }
    }
}
