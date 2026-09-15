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

    @Query("DELETE FROM workflow_events")
    suspend fun clearAll()
}
