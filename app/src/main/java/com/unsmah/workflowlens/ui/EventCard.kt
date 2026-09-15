package com.unsmah.workflowlens.ui

import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
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

@Composable
internal fun EventCard(
    event: WorkflowEvent,
    viewModel: TimelineViewModel,
    onOpenLightbox: () -> Unit
) {
    var icon by remember(event.packageName) { mutableStateOf<Drawable?>(null) }
    var appLabel by remember(event.packageName) { mutableStateOf(event.packageName) }

    LaunchedEffect(event.packageName) {
        icon = viewModel.iconFor(event.packageName)
        appLabel = viewModel.labelFor(event.packageName)
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(event.imagePath, onOpenLightbox)

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    event.actionDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (event.imagePath.isBlank() && event.failureNote != null) {
                    Text(
                        "No screenshot: ${event.failureNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        val bmp = remember(it) {
                            runCatching { it.toBitmap(40, 40) }.getOrNull()
                        }
                        bmp?.let { b ->
                            Image(
                                bitmap = b.asImageBitmap(),
                                contentDescription = appLabel,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                    Text(
                        " $appLabel · ${relativeTime(event.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Small screenshot preview; tapping opens the full-screen light-box. */
@Composable
internal fun Thumbnail(path: String, onTap: () -> Unit) {
    val bmp = remember(path) {
        path.takeIf { it.isNotBlank() }?.let {
            runCatching {
                BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = 4 })
            }.getOrNull()
        }
    }
    if (bmp == null) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Screenshot",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onTap)
        )
    }
}
