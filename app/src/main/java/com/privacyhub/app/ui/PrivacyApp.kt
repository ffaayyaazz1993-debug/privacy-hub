package com.privacyhub.app.ui

import android.content.Intent
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPrivacyApp() {
    val context = LocalContext.current
    var config by remember { mutableStateOf(MaskStore.load(context)) }

    fun update(transform: (MaskConfig) -> MaskConfig) {
        val next = MaskStore.normalize(transform(config))
        config = next
        MaskStore.save(context, next)
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Private Reader", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Read web pages through a small viewing window while the rest " +
                                "of the screen is blacked out. Drag the round handle to move " +
                                "the window, the square corner to resize it.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            context.startActivity(Intent(context, ReaderActivity::class.java))
                        }) { Text("Open Private Reader") }
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
                            "This works inside the app's own reader. Android blocks tap-through " +
                                "on overlays drawn over OTHER apps, so a system-wide interactive " +
                                "mask is not possible. This reduces visible information; it does " +
                                "not physically block side-view light.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
