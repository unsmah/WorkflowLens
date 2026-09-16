package com.unsmah.workflowlens.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row of the timeline: a single tracked user action plus a pointer to the screenshot that
 * was captured at the moment it happened. The image itself never enters the database — only
 * the path of the file inside the app's private [Context.filesDir] does.
 */
@Entity(tableName = "workflow_events")
data class WorkflowEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Milliseconds since epoch, taken at capture time. */
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    /** Package that hosted the action, e.g. com.android.chrome. */
    @ColumnInfo(name = "packageName") val packageName: String,
    /** Human readable sentence, e.g. "Clicked 'Submit' in Chrome". */
    @ColumnInfo(name = "actionDescription") val actionDescription: String,
    /** Absolute path of the stored screenshot file; empty if the capture failed. */
    @ColumnInfo(name = "imagePath") val imagePath: String,
    /** 1 when the user's global filter allowed this package, 0 otherwise (kept dimmed). */
    @ColumnInfo(name = "tracked") val tracked: Int = 1,
    /** Why the screenshot is missing, if it is; null when the capture succeeded. */
    @ColumnInfo(name = "failureNote") val failureNote: String? = null,
    /** "click" or "switch" — drives the click/switch filter chips. */
    @ColumnInfo(name = "eventType", defaultValue = "click") val eventType: String = "click",
    /** Encoded screenshot bytes on disk; feeds the per-app storage breakdown. */
    @ColumnInfo(name = "imageSizeBytes", defaultValue = "0") val imageSizeBytes: Long = 0L
)
