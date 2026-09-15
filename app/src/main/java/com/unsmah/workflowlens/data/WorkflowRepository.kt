package com.unsmah.workflowlens.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository: the single seam between the tracker service / UI and storage.
 *
 * Everything is a suspend function on Dispatchers.IO — the service's screenshot callback
 * (which runs on its own executor thread) simply hands the bitmap over and never blocks.
 */
class WorkflowRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = WorkflowDatabase.get(appContext).workflowEventDao()

    /** Live timeline for the dashboard. */
    val timeline: kotlinx.coroutines.flow.Flow<List<WorkflowEvent>> = dao.observeTimeline()

    /**
     * Persists one captured action. Returns the row id, or null when storage failed.
     */
    suspend fun record(
        timestamp: Long,
        packageName: String,
        actionDescription: String,
        bitmap: Bitmap?,
        tracked: Boolean
    ): Long? = withContext(Dispatchers.IO) {
        // 1) Blob first — if the image cannot be written we still keep the metadata.
        val imagePath = bitmap?.let { ScreenshotStore.save(appContext, it) }.orEmpty()
        // 2) Metadata row second — one Room transaction, safe & synchronous on this dispatcher.
        val id = dao.insert(
            WorkflowEvent(
                timestamp = timestamp,
                packageName = packageName,
                actionDescription = actionDescription,
                imagePath = imagePath,
                tracked = if (tracked) 1 else 0
            )
        )
        // 3) Retention piggy-backs on every record call so no scheduler is needed.
        RetentionManager.enforce(appContext, dao)
        id
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
        ScreenshotStore.directory(appContext).listFiles()?.forEach { it.delete() }
    }

    companion object {
        @Volatile
        private var instance: WorkflowRepository? = null

        fun get(context: Context): WorkflowRepository =
            instance ?: synchronized(this) {
                instance ?: WorkflowRepository(context).also { instance = it }
            }
    }
}
