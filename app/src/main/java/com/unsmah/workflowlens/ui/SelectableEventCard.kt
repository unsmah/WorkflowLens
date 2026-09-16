package com.unsmah.workflowlens.ui

import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unsmah.workflowlens.data.WorkflowEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timeline card v2: checkbox when multi-selecting, long-press to select, tap to open the
 * gallery. Timestamp follows the user's relative/exact preference.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SelectableEventCard(
    event: WorkflowEvent,
    viewModel: TimelineViewModel,
    selected: Boolean,
    timeMode: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onChecked: () -> Unit
) {
    var icon by remember(event.packageName) { mutableStateOf<Drawable?>(null) }
    var appLabel by remember(event.packageName) { mutableStateOf(event.packageName) }
    LaunchedEffect(event.packageName) {
        icon = viewModel.iconFor(event.packageName)
        appLabel = viewModel.labelFor(event.packageName)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        shape = RoundedCornerShape(16.dp),
        colors = if (selected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected || viewModel.selection.value.isNotEmpty()) {
                Checkbox(checked = selected, onCheckedChange = { onChecked() })
            }
            Thumbnail(event.imagePath) { onTap() }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(event.actionDescription, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (event.imagePath.isBlank() && event.failureNote != null) {
                    Text("No screenshot: ${event.failureNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        val bmp = remember(it) { runCatching { it.toBitmap(40, 40) }.getOrNull() }
                        bmp?.let { b ->
                            Image(b.asImageBitmap(), appLabel,
                                modifier = Modifier.size(18.dp).clip(CircleShape))
                        }
                    }
                    Text(
                        " $appLabel \u00b7 ${formatTime(event.timestamp, timeMode)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Timestamp honoring the user's relative/exact preference. */
internal fun formatTime(timestamp: Long, mode: String): String {
    if (mode == "exact") {
        return SimpleDateFormat("d MMM, HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }
    return relativeTime(timestamp)
}
