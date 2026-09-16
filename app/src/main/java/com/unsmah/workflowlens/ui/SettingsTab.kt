package com.unsmah.workflowlens.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unsmah.workflowlens.data.AppPrefs

/**
 * Phase 2 settings tab: everything the old bottom sheet had (kept as one heritage
 * section) plus pause, intelligence, theme, time mode, export and about.
 */
@Composable
internal fun SettingsTab(
    viewModel: TimelineViewModel,
    onOpenLegacySheet: () -> Unit
) {
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val timeMode by viewModel.timeMode.collectAsStateWithLifecycle()
    var showExport by remember { mutableStateOf(false) }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    val app = viewModel.getApp()
    var skipOff by remember { mutableStateOf(AppPrefs.skipScreenOff(app)) }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            val zip = viewModel.pendingBackupZip
            if (uri != null && zip != null && zip.exists()) {
                runCatching {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        zip.inputStream().use { it.copyTo(out) }
                    }
                    exportMsg = "Backup saved."
                }.onFailure { exportMsg = "Backup failed: ${it.message}" }
            } else exportMsg = "Backup failed: nothing to write."
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- Tracker control --------------------------------------------------
        SettingsSection("Tracker") {
            SettingsSwitch("Pause tracking",
                "Temporarily stop recording; the service stays enabled.", paused) {
                viewModel.setPaused(it)
            }
            SettingsSwitch("Skip while screen is off",
                "Ignore lock-screen and ambient-display events.", skipOff) {
                AppPrefs.setSkipScreenOff(app, it); skipOff = it
                com.unsmah.workflowlens.service.TrackerService.notifyFiltersChanged()
            }
            OutlinedButton(onClick = { viewModel.openAccessibilitySettings() },
                modifier = Modifier.fillMaxWidth()) {
                Text("Open Accessibility settings")
            }
            OutlinedButton(onClick = onOpenLegacySheet, modifier = Modifier.fillMaxWidth()) {
                Text("Capture, image & app filters…")
            }
        }
        // --- Appearance -------------------------------------------------------------
        SettingsSection("Appearance") {
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Auto", "light" to "Light",
                    "dark" to "Dark", "amoled" to "AMOLED").forEach { (v, label) ->
                    FilterChip(selected = theme == v,
                        onClick = { viewModel.setTheme(v) }, label = { Text(label) })
                }
            }
            Text("Timestamps", style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = timeMode == "relative",
                    onClick = { viewModel.setTimeMode("relative") },
                    label = { Text("Relative (2 min ago)") })
                FilterChip(selected = timeMode == "exact",
                    onClick = { viewModel.setTimeMode("exact") },
                    label = { Text("Exact (14:32:05)") })
            }
        }
        // --- Storage ------------------------------------------------------------------
        SettingsSection("Storage & data") {
            OutlinedButton(onClick = { showExport = true },
                modifier = Modifier.fillMaxWidth()) {
                Text("Export / backup…")
            }
            OutlinedButton(onClick = { viewModel.testCapture() },
                modifier = Modifier.fillMaxWidth()) {
                Text("Record a test event")
            }
        }
        // --- About -----------------------------------------------------------------------
        SettingsSection("About") {
            Text("Workflow Lens — private on-device activity timeline.",
                style = MaterialTheme.typography.bodyMedium)
            Text("No INTERNET permission: nothing you capture can leave this device. " +
                "Uninstalling wipes everything. Screenshots of banking and private " +
                "(FLAG_SECURE) screens are blocked by Android itself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("v2.0.0 · MIT-style personal build",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showExport) {
        ExportDialog(
            onDismiss = { showExport = false },
            onPickRange = { from, to ->
                showExport = false
                val zip = viewModel.buildBackupZip(from, to)
                if (zip == null) { exportMsg = "Nothing to export in that range."; return@ExportDialog }
                viewModel.pendingBackupZip = zip
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, "workflow-lens-backup.zip")
                }
                zipLauncher.launch(intent)
            }
        )
    }
    exportMsg?.let {
        AlertDialog(onDismissRequest = { exportMsg = null },
            confirmButton = { TextButton(onClick = { exportMsg = null }) { Text("OK") } },
            text = { Text(it) })
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean,
    onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider()
}

/** Range picker for export: today / last 7 days / last 30 days / everything. */
@Composable
private fun ExportDialog(onDismiss: () -> Unit, onPickRange: (Long, Long) -> Unit) {
    val now = System.currentTimeMillis()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("A ZIP with events.csv plus every screenshot in the range.")
                listOf(
                    "Today" to TimelineViewModel.startOfDay(now),
                    "Last 7 days" to (now - 7 * TimelineViewModel.DAY_MS),
                    "Last 30 days" to (now - 30 * TimelineViewModel.DAY_MS),
                    "Everything" to 0L
                ).forEach { (label, from) ->
                    OutlinedButton(onClick = { onPickRange(from, now) },
                        modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
