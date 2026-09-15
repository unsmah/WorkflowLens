package com.unsmah.workflowlens.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves screenshots into the app's isolated storage ([Context.filesDir]/screenshots).
 *
 * Files are compressed to JPEG at 80% quality: a full-screen capture shrinks from several
 * MB of raw pixels to ~100–300 KB, which keeps weeks of timeline affordable on flash.
 *
 * The directory is private to the app (no permissions involved) and is wiped automatically
 * by Android when the app is uninstalled — the "encrypted/isolated blobs" requirement.
 */
object ScreenshotStore {

    private const val DIR_NAME = "screenshots"
    private const val QUALITY = 80

    fun directory(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /**
     * Writes [bitmap] as a JPEG and returns the absolute path, or null when the write failed
     * (e.g. disk full) — the caller then stores the event without an image instead of
     * dropping the whole action.
     */
    fun save(context: Context, bitmap: Bitmap): String? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(directory(context), "shot_$stamp.jpg")
        return runCatching {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            file.absolutePath
        }.getOrNull()
    }

    /** Deletes the file behind [path] if it exists; returns true when something was removed. */
    fun delete(path: String): Boolean {
        if (path.isBlank()) return false
        return File(path).takeIf { it.exists() }?.delete() ?: false
    }

    /** Sum of all screenshot bytes, shown on the dashboard. */
    fun totalBytes(context: Context): Long = directory(context).listFiles()?.sumOf { it.length() } ?: 0L
}
