package com.unsmah.workflowlens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Settings bottom sheet: service status, retention window, and the per-app exclusion
 * filter ("track everything" by default; tick apps to skip).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    viewModel: TimelineViewModel,
    excluded: Set<String>,
    retentionDays: Int,
    onDismiss: () -> Unit
) {
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            Text(
                "Everything stays on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            // --- Service status ---------------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tracker service",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { viewModel.openAccessibilitySettings() }) {
                    Text(if (viewModel.trackerEnabled.value) "Manage" else "Enable")
                }
            }

            // --- Retention ------------------------------------------------------------
            Text(
                "Keep events for ${retentionDays} day${if (retentionDays == 1) "" else "s"}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp)
            )
            Slider(
                value = retentionDays.toFloat(),
                onValueChange = { viewModel.setRetentionDays(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // --- Capture notes -----------------------------------------------------
            Text(
                "About screenshots",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Some apps (banking, private browser tabs) mark their windows secure, " +
                        "which blocks any screenshot. Actions there are still logged — " +
                        "only the image is missing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // --- App filter -----------------------------------------------------------
            Text(
                if (excluded.isEmpty()) "Tracking all apps"
                else "Skipping ${excluded.size} app${if (excluded.size == 1) "" else "s"}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Tick an app to exclude it from tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                placeholder = { Text("Search apps") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(
                    apps.filter {
                        search.isBlank() || it.label.contains(search, ignoreCase = true)
                    },
                    key = { it.packageName }
                ) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                            maxLines = 1
                        )
                        Checkbox(
                            checked = app.packageName in excluded,
                            onCheckedChange = { viewModel.toggleExcluded(app.packageName) }
                        )
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 24.dp)
            ) { Text("Done") }
        }
    }
}
