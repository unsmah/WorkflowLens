package com.unsmah.workflowlens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unsmah.workflowlens.data.AppPrefs

/** Section header helper. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
    )
}

/** One labelled switch row. */
@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** One labelled slider row with a live value label. */
@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value, onValueChange = { onChange(it.toInt()) },
            valueRange = range, steps = steps
        )
    }
}

/**
 * Settings sheet — service status, capture behaviour, image encoding, the caption overlay,
 * storage policy (retention + quota) and the per-app exclusion filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    viewModel: TimelineViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val excluded by viewModel.excludedPackages.collectAsStateWithLifecycle()
    val retentionDays by viewModel.retentionDays.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val trackerEnabled by viewModel.trackerEnabled.collectAsStateWithLifecycle()

    // Local mirrors of prefs-backed options; written through the ViewModel so the running
    // service picks changes up immediately.
    var recordClicks by remember { mutableStateOf(AppPrefs.recordClicks(context)) }
    var recordWindow by remember { mutableStateOf(AppPrefs.recordWindow(context)) }
    var windowDelay by remember { mutableStateOf(AppPrefs.windowDelayMs(context)) }
    var format by remember { mutableStateOf(AppPrefs.imageFormat(context)) }
    var quality by remember { mutableStateOf(AppPrefs.imageQuality(context)) }
    var maxDim by remember { mutableStateOf(AppPrefs.maxDimension(context)) }
    var overlayOn by remember { mutableStateOf(AppPrefs.overlayEnabled(context)) }
    var overlayBottom by remember { mutableStateOf(AppPrefs.overlayPosition(context) == "bottom") }
    var showTime by remember { mutableStateOf(AppPrefs.overlayShowTime(context)) }
    var showApp by remember { mutableStateOf(AppPrefs.overlayShowApp(context)) }
    var showAction by remember { mutableStateOf(AppPrefs.overlayShowAction(context)) }
    var textScale by remember { mutableStateOf(AppPrefs.overlayTextScale(context)) }
    var quotaMb by remember { mutableStateOf(AppPrefs.quotaMb(context)) }
    var search by remember { mutableStateOf("") }
    var overlayBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)

            SectionTitle("Tracker")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        if (trackerEnabled) "Service is running" else "Service is off",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Android · Accessibility",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { viewModel.openAccessibilitySettings() }) {
                    Text(if (trackerEnabled) "Manage" else "Enable")
                }
            }

            SectionTitle("Capture")
            SwitchRow(
                "Record taps", "Log button and item clicks", recordClicks,
                { viewModel.setRecordClicks(it); recordClicks = it })
            SwitchRow(
                "Record app switches", "Log when you open or change apps", recordWindow,
                { viewModel.setRecordWindow(it); recordWindow = it })
            if (recordWindow) {
                SliderRow(
                    "App-switch capture delay",
                    "${windowDelay} ms",
                    windowDelay.toFloat(), 0f..1000f, 19,
                    { viewModel.setWindowDelayMs(it); windowDelay = it })
                Text(
                    "Higher = the new app has time to render before the screenshot; " +
                        "lower = closer to the exact switch moment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionTitle("Image")
            Text("Format", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("JPEG", "WEBP", "PNG").forEach { f ->
                    FilterChip(
                        selected = format == f,
                        onClick = { viewModel.setImageFormat(f); format = f },
                        label = { Text(f) }
                    )
                }
            }
            if (format != "PNG") {
                SliderRow(
                    "Quality", "$quality%",
                    quality.toFloat(), 50f..100f, 9,
                    { viewModel.setImageQuality(it); quality = it })
            } else {
                Text(
                    "PNG is lossless — quality slider does not apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("Max resolution", style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val dims = listOf(0 to "Original", 720 to "720p", 1080 to "1080p", 1440 to "1440p")
                dims.forEach { (d, label) ->
                    FilterChip(
                        selected = maxDim == d,
                        onClick = { viewModel.setMaxDimension(d); maxDim = d },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                "Smaller resolutions save disk space; original keeps full detail.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionTitle("Caption burned into screenshots")
            SwitchRow(
                "Burn caption into new screenshots",
                "Time · app · action drawn onto the image itself", overlayOn,
                { viewModel.setOverlayEnabled(it); overlayOn = it })
            if (overlayOn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = overlayBottom,
                        onClick = { viewModel.setOverlayPosition(true); overlayBottom = true },
                        label = { Text("Bottom") }
                    )
                    FilterChip(
                        selected = !overlayBottom,
                        onClick = { viewModel.setOverlayPosition(false); overlayBottom = false },
                        label = { Text("Top") }
                    )
                }
                OverlayCheck("Show time", showTime) {
                    viewModel.setOverlayShowTime(it); showTime = it }
                OverlayCheck("Show app name", showApp) {
                    viewModel.setOverlayShowApp(it); showApp = it }
                OverlayCheck("Show action text", showAction) {
                    viewModel.setOverlayShowAction(it); showAction = it }
                SliderRow(
                    "Text size", "$textScale%",
                    textScale.toFloat(), 60f..140f, 7,
                    { viewModel.setOverlayTextScale(it); textScale = it })
                var overlayResult by remember { mutableStateOf<String?>(null) }
                OutlinedButton(
                    onClick = {
                        overlayBusy = true
                        overlayResult = null
                        viewModel.overlayHistory { n ->
                            overlayBusy = false
                            overlayResult = "Overlay applied to $n screenshots."
                        }
                    },
                    enabled = !overlayBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (overlayBusy) "Applying…" else "Apply caption to existing screenshots")
                }
                overlayResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            SectionTitle("Storage")
            Text(
                "Using ${storageBytes / 1024} KB of ${quotaMb} MB quota.",
                style = MaterialTheme.typography.bodyMedium
            )
            SliderRow(
                "Keep events for", "$retentionDays day${if (retentionDays == 1) "" else "s"}",
                retentionDays.toFloat(), 1f..30f, 28,
                { viewModel.setRetentionDays(it) })
            SliderRow(
                "Storage quota", "${quotaMb} MB",
                quotaMb.toFloat(), 100f..2048f, 0,
                { viewModel.setQuotaMb(it); quotaMb = it })
            Text(
                "When the quota is exceeded the oldest screenshots are pruned first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionTitle("Apps")
            Text(
                "Tick apps to exclude them from tracking. Empty = track everything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            val filtered = apps
                .filter {
                    search.isBlank() ||
                        it.label.contains(search, ignoreCase = true) ||
                        it.packageName.contains(search, ignoreCase = true)
                }
                .sortedByDescending { it.packageName in excluded }
                .take(120)
            Column(Modifier.fillMaxWidth()) {
                filtered.forEach { app ->
                    val checked = app.packageName !in excluded
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = !checked,
                            onCheckedChange = { viewModel.toggleExcluded(app.packageName) }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    Text("No apps match.", style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            OutlinedButton(
                onClick = { viewModel.testCapture() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Record a test event")
            }
            Spacer(Modifier.padding(bottom = 32.dp))
        }
    }
}

/** Checkbox row used by the caption-overlay section. */
@Composable
private fun OverlayCheck(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}
