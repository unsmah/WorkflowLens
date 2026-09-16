package com.unsmah.workflowlens.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Component C: Material 3 dashboard — service toggle, timeline, light-box viewer. */
class MainActivity : ComponentActivity() {

    private val viewModel: TimelineViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            WorkflowLensTheme(mode = theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Dashboard(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus() // service may have been toggled in system settings
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Dashboard(viewModel: TimelineViewModel) {
    val events by viewModel.timeline.collectAsStateWithLifecycle()
    val enabled by viewModel.trackerEnabled.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val timeMode by viewModel.timeMode.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val testCaptureDone by viewModel.testCaptureDone.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) } // 0 = timeline, 1 = stats, 2 = settings
    var lightboxId by remember { mutableStateOf<Long?>(null) }
    var showLegacySheet by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    // Flash a confirmation when the test event lands.
    LaunchedEffect(testCaptureDone) {
        if (testCaptureDone) {
            snackbar.showSnackbar("Test event recorded — the pipeline works!")
            viewModel.resetTestCaptureFlag()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workflow Lens") },
                actions = {
                    if (selection.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.shareSelected(viewModel.getApp())
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share selected")
                        }
                        IconButton(onClick = {
                            viewModel.deleteSelected { n ->
                                scope.launch { snackbar.showSnackbar("Deleted $n events") }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                    IconButton(onClick = { viewModel.setPaused(!paused) }) {
                        Icon(
                            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (paused) "Resume tracking" else "Pause tracking"
                        )
                    }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Timeline") })
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("Stats") })
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when (tab) {
                0 -> {
                    TrackerCard(
                        enabled = enabled, paused = paused, storageBytes = storageBytes,
                        onToggle = { if (!enabled) viewModel.openAccessibilitySettings() }
                    )
                    TimelineTab(
                        viewModel = viewModel, enabled = enabled, timeMode = timeMode,
                        onOpenLightbox = { lightboxId = it },
                        onTest = { viewModel.testCapture() },
                        snackbar = snackbar
                    )
                }
                1 -> StatsScreen(viewModel)
                else -> SettingsTab(viewModel, onOpenLegacySheet = { showLegacySheet = true })
            }
        }
    }

    if (showLegacySheet) {
        SettingsSheet(viewModel = viewModel, onDismiss = { showLegacySheet = false })
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("Every event and screenshot will be deleted permanently. " +
                "This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                    scope.launch { snackbar.showSnackbar("History cleared") }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }

    lightboxId?.let { id ->
        val filtered = events.filter { it.imagePath.isNotBlank() }
        val startIndex = filtered.indexOfFirst { it.id == id }.coerceAtLeast(0)
        LightboxGalleryDialog(
            events = filtered,
            startIndex = startIndex,
            onDismiss = { lightboxId = null },
            onDelete = { event -> viewModel.deleteEvent(event.id) }
        )
    }
}

@Composable
internal fun TrackerCard(enabled: Boolean, paused: Boolean, storageBytes: Long, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Accessibility, contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    if (enabled) "Tracking is on" else "Tracking is off",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (!enabled) "Tap to open Accessibility settings"
                    else if (paused) "Paused — tap the play icon to resume"
                    else "Capturing clicks and screens",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Storage used: ${storageBytes / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
internal fun EmptyState(enabled: Boolean, onTest: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Nothing captured yet", style = MaterialTheme.typography.titleMedium)
        Text(
            if (enabled) "Interact with any app — events will appear here.\n" +
                    "If nothing shows up, record a test event below to verify the pipeline."
            else "Turn on tracking and interact with any app —\nevents will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (enabled) {
            OutlinedButton(onClick = onTest, modifier = Modifier.padding(top = 12.dp)) {
                Text("Record a test event")
            }
        }
    }
}
