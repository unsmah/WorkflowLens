package com.unsmah.workflowlens.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.unsmah.workflowlens.data.WorkflowEvent
import java.io.File
import java.util.Locale

/**
 * Full-screen gallery viewer: swipe left/right between the timeline's screenshots, pinch to
 * zoom, double-tap to toggle 2.5x. Header shows the position counter, relative time and the
 * action text; per-image actions are Share and Delete.
 */
@Composable
internal fun LightboxGalleryDialog(
    events: List<WorkflowEvent>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onDelete: (WorkflowEvent) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val context = LocalContext.current
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, (events.size - 1).coerceAtLeast(0))
        ) { events.size.coerceAtLeast(1) }
        var pendingDelete by remember { mutableStateOf<WorkflowEvent?>(null) }
        val current = events.getOrNull(pagerState.currentPage)

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val event = events.getOrNull(page)
                ZoomableImage(event?.imagePath)
            }

            // --- Top bar: close, position counter, share, delete -------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.7f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
                Text(
                    "${pagerState.currentPage + 1} / ${events.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    current?.imagePath?.takeIf { it.isNotBlank() }?.let { path ->
                        shareImage(context, path)
                    }
                }) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                IconButton(onClick = { current?.let { pendingDelete = it } }) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                }
            }

            // --- Bottom caption: time + app + action --------------------------------
            current?.let { event ->
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.85f))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        relativeTime(event.timestamp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        event.actionDescription,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        pendingDelete?.let { event ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete this event?") },
                text = { Text("The event and its screenshot will be removed permanently.") },
                confirmButton = {
                    TextButton(onClick = { pendingDelete = null; onDelete(event) }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}

/** Shares a screenshot file via the system share sheet (through a FileProvider URI). */
private fun shareImage(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share screenshot"
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** One pager page: decodes the file scaled to screen size and supports pinch-zoom. */
@Composable
private fun ZoomableImage(path: String?) {
    val bmp = remember(path) {
        path?.takeIf { it.isNotBlank() }?.let {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(it, opts)
                var sample = 1
                while (opts.outWidth / (sample * 2) >= 2048) sample *= 2
                BitmapFactory.decodeFile(
                    it, BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }.getOrNull()
        }
    }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bmp == null) {
            Text("Screenshot no longer available", color = Color.White)
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(bmp) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(bmp) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
            )
        }
    }
}

/** "2 mins ago" style timestamp, shared by the timeline cards and the gallery. */
internal fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60} min${if (seconds / 60 == 1L) "" else "s"} ago"
        seconds < 86400 -> "${seconds / 3600} hr${if (seconds / 3600 == 1L) "" else "s"} ago"
        else -> java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}

/** Drawable -> Bitmap helper (app icons arrive as arbitrary drawables). */
internal fun android.graphics.drawable.Drawable.toBitmap(width: Int, height: Int): android.graphics.Bitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(
        width, height, android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap
}
