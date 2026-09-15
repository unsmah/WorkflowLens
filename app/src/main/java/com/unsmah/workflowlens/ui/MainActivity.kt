package com.unsmah.workflowlens.ui

import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unsmah.workflowlens.data.WorkflowEvent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Component C: Material 3 dashboard — service toggle, timeline, light-box viewer. */
class MainActivity : ComponentActivity() {

    private val viewModel: TimelineViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkflowLensTheme {
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
    val testCaptureDone by viewModel.testCaptureDone.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var lightboxId by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }
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
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            TrackerCard(
                enabled = enabled, storageBytes = storageBytes,
                onToggle = { if (!enabled) viewModel.openAccessibilitySettings() }
            )

            Text(
                "Activity timeline",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            if (events.isEmpty()) {
                EmptyState(enabled = enabled, onTest = { viewModel.testCapture() })
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(events, key = { it.id }) { event ->
                        EventCard(event, viewModel) { lightboxId = event.id }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            viewModel = viewModel,
            onDismiss = { showSettings = false }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("Every event and screenshot will be deleted permanently. This cannot be undone.") },
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
        val startIndex = events.indexOfFirst { it.id == id }.coerceAtLeast(0)
        LightboxGalleryDialog(
            events = events.filter { it.imagePath.isNotBlank() },
            startIndex = startIndex,
            onDismiss = { lightboxId = null },
            onDelete = { event -> viewModel.deleteEvent(event.id) }
        )
    }
}

@Composable
internal fun TrackerCard(enabled: Boolean, storageBytes: Long, onToggle: () -> Unit) {
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
                    if (enabled) "Capturing clicks and screens"
                    else "Tap to open Accessibility settings",
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
