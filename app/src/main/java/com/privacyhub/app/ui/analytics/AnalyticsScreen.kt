package com.privacyhub.app.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.privacyhub.app.ui.home.HomeViewModel
import com.privacyhub.app.ui.repository
import com.privacyhub.app.util.formatDuration
import com.privacyhub.app.util.startOfWeek

/** M1 placeholder: real today/week totals; the full dashboard lands in M5. */
@Composable
fun AnalyticsScreen() {
    val repo = repository()
    val vm: AnalyticsViewModel = viewModel(factory = viewModelFactory { initializer { AnalyticsViewModel(repo) } })
    val today by vm.todaySeconds.collectAsStateWithLifecycle()
    val week by vm.weekSeconds.collectAsStateWithLifecycle()
    val count by vm.totalCompleted.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item { Text("Analytics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { StatCard("Today's focus", formatDuration(today)) }
        item { StatCard("This week", formatDuration(week)) }
        item { StatCard("Completed sessions (all time)", count.toString()) }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
