package com.unsmah.workflowlens.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Retention policy: every capture cycle the repository prunes entries (and their image
 * files) older than the configured number of days (see [AppPrefs]). Running the cleanup
 * together with the insert keeps the store bounded without ever needing a scheduler.
 */
object RetentionManager {


    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Deletes rows older than [maxAgeDays] and their screenshot files; returns rows removed. */
    suspend fun enforce(
        context: Context,
        dao: WorkflowEventDao,
        now: Long = System.currentTimeMillis(),
        maxAgeDays: Long = AppPrefs.DEFAULT_RETENTION_DAYS.toLong()
    ): Int =
        withContext(Dispatchers.IO) {
            val cutoff = now - maxAgeDays * DAY_MS
            val stale = dao.olderThan(cutoff)
            if (stale.isEmpty()) return@withContext 0

            stale.forEach { ScreenshotStore.delete(it.imagePath) }
            dao.deleteByIds(stale.map { it.id })
            stale.size
        }
}
