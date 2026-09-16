package com.unsmah.workflowlens.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unsmah.workflowlens.ui.TimelineViewModel.TimelineRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Phase 2 timeline tab: search bar, filter chips (type / screenshots / day), day headers,
 * swipe-to-delete with undo, and long-press multi-select with a batch action bar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun TimelineTab(
    viewModel: TimelineViewModel,
    enabled: Boolean,
    timeMode: String,
    onOpenLightbox: (Long) -> Unit,
    onTest: () -> Unit,
    snackbar: SnackbarHostState
) {
    val rows by viewModel.visibleRows.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()
    val shotsOnly by viewModel.onlyWithShots.collectAsStateWithLifecycle()
    val dayFilter by viewModel.dayFilter.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var showFilters by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setSearch(it) },
            label = { Text("Search actions or apps") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotBlank()) IconButton(onClick = { viewModel.setSearch("") }) {
                    Icon(Icons.Default.Clear, "Clear search")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = typeFilter == "click",
                onClick = { viewModel.setTypeFilter(if (typeFilter == "click") null else "click") },
                label = { Text("Taps") })
            FilterChip(
                selected = typeFilter == "switch",
                onClick = { viewModel.setTypeFilter(if (typeFilter == "switch") null else "switch") },
                label = { Text("App switches") })
            FilterChip(
                selected = shotsOnly,
                onClick = { viewModel.setOnlyWithShots(!shotsOnly) },
                label = { Text("Screenshots") })
            IconButton(onClick = { showFilters = true }) {
                Icon(Icons.Default.FilterList, "More filters")
            }
            if (viewModel.hasActiveFilters()) {
                TextButton(onClick = { viewModel.clearFilters() }) { Text("Clear") }
            }
        }
        if (dayFilter != null) {
            Text(
                "Showing: " + SimpleDateFormat("EEE d MMM", Locale.getDefault())
                    .format(Date(dayFilter!!)) + " — tap Clear to reset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (selection.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = true, onCheckedChange = { viewModel.clearSelection() })
                Text("${selection.size} selected", modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.selectAllVisible() }) { Text("All") }
            }
        }
        if (rows.isEmpty()) {
            EmptyState(enabled = enabled, onTest = onTest)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(rows, key = { it.kind * 1_000_000_000L + (it.event?.id ?: it.dayStart) }) { row ->
                    if (row.kind == TimelineRow.HEADER) {
                        DayHeader(row.dayStart)
                    } else {
                        val e = row.event!!
                        val selected = e.id in selection
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { v ->
                                if (v != SwipeToDismissBoxValue.Settled) {
                                    viewModel.deleteEventWithUndo(e.id)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        val res = snackbar.showSnackbar("Event deleted", "Undo")
                                        if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                            viewModel.undoDelete()
                                        }
                                    }
                                }
                                false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {},
                            modifier = Modifier.animateItemPlacement()
                        ) {
                            SelectableEventCard(
                                event = e, viewModel = viewModel, selected = selected,
                                timeMode = timeMode,
                                onTap = {
                                    if (selection.isNotEmpty()) viewModel.toggleSelect(e.id)
                                    else if (e.imagePath.isNotBlank()) onOpenLightbox(e.id)
                                },
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleSelect(e.id)
                                },
                                onChecked = { viewModel.toggleSelect(e.id) }
                            )
                        }
                    }
                }
            }
        }
    }
    if (showFilters) {
        DayFilterSheet(viewModel, onDismiss = { showFilters = false })
    }
}

/** Sticky-style day separator: "Today · 24 events" etc. */
@Composable
private fun DayHeader(dayStart: Long) {
    val label = when (dayStart) {
        TimelineViewModel.startOfDay(System.currentTimeMillis()) -> "Today"
        TimelineViewModel.startOfDay(System.currentTimeMillis() - TimelineViewModel.DAY_MS) -> "Yesterday"
        else -> SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(dayStart))
    }
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Date-range picker for the timeline: today / yesterday / last 7 days / all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayFilterSheet(viewModel: TimelineViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Date range", style = MaterialTheme.typography.headlineSmall)
            val opts = listOf(
                "All time" to null,
                "Today" to TimelineViewModel.startOfDay(System.currentTimeMillis()),
                "Yesterday" to TimelineViewModel.startOfDay(
                    System.currentTimeMillis() - TimelineViewModel.DAY_MS),
                "Last 7 days" to (System.currentTimeMillis() - 7 * TimelineViewModel.DAY_MS)
            )
            opts.forEach { (label, day) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, modifier = Modifier.weight(1f))
                    Switch(
                        checked = false, onCheckedChange = null,
                        modifier = Modifier.padding(0.dp))
                    TextButton(onClick = { viewModel.setDayFilter(day); onDismiss() }) {
                        Text("Show")
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Only with screenshots", modifier = Modifier.weight(1f))
                val shotsOnly by viewModel.onlyWithShots.collectAsStateWithLifecycle()
                Switch(checked = shotsOnly, onCheckedChange = { viewModel.setOnlyWithShots(it) })
            }
            androidx.compose.foundation.layout.Spacer(
                Modifier.padding(bottom = 32.dp))
        }
    }
}
