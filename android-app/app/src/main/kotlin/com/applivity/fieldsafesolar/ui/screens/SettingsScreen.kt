package com.applivity.fieldsafesolar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.applivity.fieldsafesolar.data.model.AppSettings
import com.applivity.fieldsafesolar.di.ServiceProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val repo = remember { ServiceProvider.getAppSettingsRepository() }
    var settings by remember { mutableStateOf(repo.getSettings()) }

    fun save(updated: AppSettings) {
        settings = updated
        repo.saveSettings(updated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection("Recording") {
                SliderSetting(
                    label = "Max recording time",
                    value = settings.maxRecordingSeconds.toFloat(),
                    range = 5f..30f,
                    steps = 24,
                    unit = "s",
                    onValueChange = { save(settings.copy(maxRecordingSeconds = it.toInt())) }
                )
                SliderSetting(
                    label = "Silence timeout",
                    value = settings.silenceTimeoutSeconds.toFloat(),
                    range = 2f..10f,
                    steps = 7,
                    unit = "s",
                    onValueChange = { save(settings.copy(silenceTimeoutSeconds = it.toInt())) }
                )
            }

            SettingsSection("Silence Detection") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mode:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = settings.silenceMode == AppSettings.SilenceMode.AUTO_CALIBRATE,
                        onClick = { save(settings.copy(silenceMode = AppSettings.SilenceMode.AUTO_CALIBRATE)) },
                        label = { Text("Auto-calibrate") }
                    )
                    FilterChip(
                        selected = settings.silenceMode == AppSettings.SilenceMode.MANUAL,
                        onClick = { save(settings.copy(silenceMode = AppSettings.SilenceMode.MANUAL)) },
                        label = { Text("Manual") }
                    )
                }
                if (settings.silenceMode == AppSettings.SilenceMode.MANUAL) {
                    SliderSetting(
                        label = "Silence threshold",
                        value = settings.manualSilenceThreshold.toFloat(),
                        range = 500f..8000f,
                        steps = 74,
                        unit = "",
                        onValueChange = { save(settings.copy(manualSilenceThreshold = it.toInt())) }
                    )
                    Text(
                        text = "Low = sensitive (quiet sites)  •  High = less sensitive (noisy field)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Samples first 0.5s of each recording to set threshold automatically.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsSection("Voice") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-read questions", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "AI speaks each checklist question aloud",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.ttsAutoReadQuestion,
                        onCheckedChange = { save(settings.copy(ttsAutoReadQuestion = it)) }
                    )
                }
            }

            SettingsSection("Scan") {
                SliderSetting(
                    label = "Scan duration",
                    value = settings.scanDurationSeconds.toFloat(),
                    range = 3f..10f,
                    steps = 6,
                    unit = "s",
                    onValueChange = { save(settings.copy(scanDurationSeconds = it.toInt())) }
                )
                Text(
                    text = "Time ML Kit scans the work area before advancing to voice check.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${value.toInt()}$unit",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
