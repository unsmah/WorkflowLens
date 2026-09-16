package com.unsmah.workflowlens.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar

/** Phase 2/3 stats tab: totals, top-5 apps, 24h activity heatmap, storage breakdown. */
@Composable
internal fun StatsScreen(viewModel: TimelineViewModel) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadStats() }
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVar = MaterialTheme.colorScheme.surfaceVariant

    val now = System.currentTimeMillis()
    val dayAgo = now - TimelineViewModel.DAY_MS
    val weekAgo = now - 7 * TimelineViewModel.DAY_MS
    val today = stats?.timestamps?.count { it >= TimelineViewModel.startOfDay(now) } ?: 0
    val week = stats?.timestamps?.count { it >= weekAgo } ?: 0
    val total = stats?.timestamps?.size ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Today", "$today", Modifier.weight(1f))
            StatCard("This week", "$week", Modifier.weight(1f))
            StatCard("Total", "$total", Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Top apps", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val top = (stats?.topApps ?: emptyList()).take(5)
                val max = (top.firstOrNull()?.count ?: 1).coerceAtLeast(1)
                if (top.isEmpty()) {
                    Text("No data yet.", style = MaterialTheme.typography.bodySmall)
                }
                top.forEach { row ->
                    val label = runCatching {
                        val pm = viewModel.getApp().packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(row.packageName, 0)).toString()
                    }.getOrDefault(row.packageName)
                    Text(label, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    Canvas(Modifier.fillMaxWidth().height(10.dp).padding(top = 2.dp)) {
                        drawRoundRect(surfaceVar, cornerRadius = CornerRadius(5f, 5f))
                        drawRoundRect(primary, cornerRadius = CornerRadius(5f, 5f),
                            size = Size(size.width * (row.count.toFloat() / max), size.height))
                    }
                    Text("${row.count} events", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Activity by hour (last 24h)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val buckets = IntArray(24)
                (stats?.timestamps ?: emptyList()).forEach { ts ->
                    if (ts >= dayAgo) {
                        val c = Calendar.getInstance().apply { timeInMillis = ts }
                        buckets[c.get(Calendar.HOUR_OF_DAY)]++
                    }
                }
                val peak = (buckets.maxOrNull() ?: 0).coerceAtLeast(1)
                Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                    val gap = 3.dp.toPx()
                    val bw = (size.width - gap * 23) / 24
                    buckets.forEachIndexed { h, v ->
                        val frac = v.toFloat() / peak
                        val bh = (size.height * 0.15f) + size.height * 0.85f * frac
                        drawRoundRect(
                            if (v == 0) surfaceVar else primary,
                            topLeft = androidx.compose.ui.geometry.Offset(h * (bw + gap), size.height - bh),
                            size = Size(bw, bh),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("00", style = MaterialTheme.typography.labelSmall)
                    Text("06", style = MaterialTheme.typography.labelSmall)
                    Text("12", style = MaterialTheme.typography.labelSmall)
                    Text("18", style = MaterialTheme.typography.labelSmall)
                    Text("23", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Storage by app", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val perApp = (stats?.storageRows ?: emptyList())
                    .groupBy { it.packageName }
                    .mapValues { (_, rows) ->
                        rows.sumOf { if (it.imageSizeBytes > 0) it.imageSizeBytes
                            else runCatching { java.io.File(it.imagePath).length() }.getOrDefault(0L) }
                    }
                    .toList().sortedByDescending { it.second }.take(5)
                val totalBytes = (stats?.storageRows ?: emptyList()).sumOf {
                    if (it.imageSizeBytes > 0) it.imageSizeBytes
                    else runCatching { java.io.File(it.imagePath).length() }.getOrDefault(0L)
                }.coerceAtLeast(1L)
                if (perApp.isEmpty()) {
                    Text("No screenshots stored.", style = MaterialTheme.typography.bodySmall)
                }
                perApp.forEach { (pkg, bytes) ->
                    val label = runCatching {
                        val pm = viewModel.getApp().packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f), maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Text("${bytes / 1024} KB", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Canvas(Modifier.fillMaxWidth().height(8.dp).padding(top = 2.dp)) {
                        drawRoundRect(surfaceVar, cornerRadius = CornerRadius(4f, 4f))
                        drawRoundRect(primary, cornerRadius = CornerRadius(4f, 4f),
                            size = Size(size.width * (bytes.toFloat() / totalBytes), size.height))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}
