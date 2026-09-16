package com.unsmah.workflowlens.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowEventDao {

    /** Newest first — the dashboard's timeline is exactly this query, observed as a Flow. */
    @Query("SELECT * FROM workflow_events ORDER BY timestamp DESC")
    fun observeTimeline(): Flow<List<WorkflowEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: WorkflowEvent): Long

    /** Used by the retention manager to find the cutoff before deleting rows + files. */
    @Query("SELECT * FROM workflow_events WHERE timestamp < :cutoff")
    suspend fun olderThan(cutoff: Long): List<WorkflowEvent>

    @Query("DELETE FROM workflow_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM workflow_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM workflow_events")
    suspend fun clearAll()

    /** Single row fetch (lightbox delete / overlay tools). */
    @Query("SELECT * FROM workflow_events WHERE id = :id")
    suspend fun byId(id: Long): WorkflowEvent?

    /** Overlay re-render: point the row at the newly encoded file. */
    @Query("UPDATE workflow_events SET imagePath = :path WHERE id = :id")
    suspend fun updateImagePath(id: Long, path: String)

    /** Keeps the bytes column honest after re-encodes. */
    @Query("UPDATE workflow_events SET imageSizeBytes = :bytes WHERE id = :id")
    suspend fun updateImageBytes(id: Long, bytes: Long)

    /** One-shot list read (overlay tool); the dashboard itself uses the Flow. */
    @Query("SELECT * FROM workflow_events ORDER BY timestamp DESC")
    suspend fun observeTimelineSnapshot(): List<WorkflowEvent>

    /** Timestamps only — the stats screen buckets them into day/hour histograms. */
    @Query("SELECT timestamp FROM workflow_events ORDER BY timestamp DESC")
    suspend fun allTimestamps(): List<Long>

    /** (package, count) pairs — top-apps chart and per-app storage. */
    @Query("SELECT packageName, COUNT(*) AS c FROM workflow_events GROUP BY packageName ORDER BY c DESC")
    suspend fun countsByPackage(): List<PackageCount>

    /** (package, bytes) pairs for the storage breakdown. */
    @Query("SELECT packageName, imagePath, imageSizeBytes FROM workflow_events")
    suspend fun storageRows(): List<StorageRow>

    /** Events in a window — day filter and share/export. */
    @Query("SELECT * FROM workflow_events WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun inWindow(from: Long, to: Long): List<WorkflowEvent>
}

/** COUNT(*) grouped by package — Room fills the fields by column name. */
data class PackageCount(
    val packageName: String,
    @androidx.room.ColumnInfo(name = "c") val count: Int
)

/** Minimal row for the per-app storage breakdown. */
data class StorageRow(
    val packageName: String,
    val imagePath: String,
    val imageSizeBytes: Long
)
