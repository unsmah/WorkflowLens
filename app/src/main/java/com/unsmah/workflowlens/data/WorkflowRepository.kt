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
     * The screenshot is encoded with the user's format/quality/size prefs and, when the
     * caption overlay is enabled, with the time/app/action burned into the pixels.
     */
    suspend fun record(
        timestamp: Long,
        packageName: String,
        actionDescription: String,
        bitmap: Bitmap?,
        tracked: Boolean,
        failureNote: String? = null
    ): Long? = withContext(Dispatchers.IO) {
        // 1) Blob first — if the image cannot be written we still keep the metadata.
        val imagePath = bitmap?.let {
            val format = AppPrefs.imageFormat(appContext)
            val quality = AppPrefs.imageQuality(appContext)
            val maxDim = AppPrefs.maxDimension(appContext)
            val overlay = if (AppPrefs.overlayEnabled(appContext)) {
                OverlayConfig(
                    timeText = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                        .format(Date(timestamp)),
                    appLabel = appLabelFor(packageName),
                    actionText = actionDescription,
                    showTime = AppPrefs.overlayShowTime(appContext),
                    showApp = AppPrefs.overlayShowApp(appContext),
                    showAction = AppPrefs.overlayShowAction(appContext),
                    positionBottom = AppPrefs.overlayPosition(appContext) == "bottom",
                    textScalePercent = AppPrefs.overlayTextScale(appContext)
                )
            } else null
            ScreenshotStore.save(appContext, it, format, quality, maxDim, overlay)
        }.orEmpty()
        // 2) Metadata row second — one Room transaction, safe & synchronous on this dispatcher.
        val id = dao.insert(
            WorkflowEvent(
                timestamp = timestamp,
                packageName = packageName,
                actionDescription = actionDescription,
                imagePath = imagePath,
                tracked = if (tracked) 1 else 0,
                failureNote = failureNote
            )
        )
        // 3) Retention + storage quota piggy-back on every record call (no scheduler needed).
        RetentionManager.enforce(appContext, dao, maxAgeDays = AppPrefs.retentionDays(appContext).toLong())
        ScreenshotStore.enforceQuota(appContext, AppPrefs.quotaMb(appContext))
        id
    }

    /** Best-effort app label for the caption overlay (falls back to the package name). */
    private fun appLabelFor(pkg: String): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
        ScreenshotStore.directory(appContext).listFiles()?.forEach { it.delete() }
    }

    /** Deletes one event (row + its screenshot file); returns true when the row existed. */
    suspend fun deleteEvent(id: Long): Boolean = withContext(Dispatchers.IO) {
        val event = dao.byId(id) ?: return@withContext false
        ScreenshotStore.delete(event.imagePath)
        dao.deleteById(id)
        true
    }

    /**
     * Re-encodes every stored screenshot with the user's CURRENT overlay settings so old
     * images match the new caption style. Rows without an image are skipped. Returns the
     * number of images re-rendered. The DB rows keep their timestamps — only pixels change.
     */
    suspend fun overlayAllWithCurrentOverlay(): Int = withContext(Dispatchers.IO) {
        if (!AppPrefs.overlayEnabled(appContext)) return@withContext 0
        val format = AppPrefs.imageFormat(appContext)
        val quality = AppPrefs.imageQuality(appContext)
        val maxDim = AppPrefs.maxDimension(appContext)
        var count = 0
        for (event in dao.observeTimelineSnapshot()) {
            val file = event.imagePath.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
            val decoded = file?.takeIf { it.exists() }?.let {
                runCatching { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }.getOrNull()
            } ?: continue

            val overlay = OverlayConfig(
                timeText = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                    .format(Date(event.timestamp)),
                appLabel = appLabelFor(event.packageName),
                actionText = event.actionDescription,
                showTime = AppPrefs.overlayShowTime(appContext),
                showApp = AppPrefs.overlayShowApp(appContext),
                showAction = AppPrefs.overlayShowAction(appContext),
                positionBottom = AppPrefs.overlayPosition(appContext) == "bottom",
                textScalePercent = AppPrefs.overlayTextScale(appContext)
            )
            val newPath = ScreenshotStore.save(appContext, decoded, format, quality, maxDim, overlay)
            decoded.recycle()
            if (newPath != null) {
                file?.delete()          // replace the old render
                dao.updateImagePath(event.id, newPath)
                count++
            }
        }
        count
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
