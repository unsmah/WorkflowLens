package com.unsmah.workflowlens.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves screenshots into the app's isolated storage ([Context.filesDir]/screenshots).
 *
 * v1.3: format (JPEG/WebP/PNG), quality, downscaling and the burned-in caption overlay
 * are all driven by [AppPrefs]. The overlay is painted with Canvas before compression, so
 * the caption becomes part of the image file itself (survives sharing/export).
 */
object ScreenshotStore {

    private const val DIR_NAME = "screenshots"

    fun directory(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Extension + compress format for the user's chosen image format. */
    private fun codecFor(format: String): Pair<String, Bitmap.CompressFormat> = when (format) {
        "WEBP" -> "webp" to Bitmap.CompressFormat.WEBP_LOSSY
        "PNG" -> "png" to Bitmap.CompressFormat.PNG
        else -> "jpg" to Bitmap.CompressFormat.JPEG
    }

    /** Downscale so the largest side is at most [maxDim] px (0 = keep original). */
    private fun scaled(bitmap: Bitmap, maxDim: Int): Bitmap {
        if (maxDim <= 0) return bitmap
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    /** Paints the caption strip onto a copy of [bitmap] (no mutation of the original). */
    fun applyOverlay(bitmap: Bitmap, config: OverlayConfig): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textSizePx = (bitmap.width / 22f) * (config.textScalePercent / 100f)
        paint.textSize = textSizePx
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        val parts = buildList {
            if (config.showTime) add(config.timeText)
            if (config.showApp) add(config.appLabel)
            if (config.showAction) add(config.actionText)
        }
        if (parts.isEmpty()) return bitmap
        val line = parts.joinToString("  ·  ")
        val textWidth = paint.measureText(line)
        val padH = textSizePx * 0.7f
        val padV = textSizePx * 0.55f
        val stripH = textSizePx + padV * 2
        val stripW = textWidth + padH * 2
        val margin = textSizePx * 0.6f

        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val left = ((bitmap.width - stripW) / 2f).coerceAtLeast(margin)
        val top = if (config.positionBottom) bitmap.height - stripH - margin else margin

        // Translucent pill behind the caption so it stays readable on any background.
        paint.color = Color.argb(150, 0, 0, 0)
        canvas.drawRoundRect(
            RectF(left, top, left + stripW, top + stripH),
            stripH / 3f, stripH / 3f, paint
        )
        paint.color = Color.WHITE
        canvas.drawText(line, left + padH, top + padV + paint.textSize * 0.92f, paint)
        return out
    }

    /**
     * Writes [bitmap] using the user's format/quality/downscale prefs, optionally with the
     * caption overlay burned in. Returns the absolute path, or null when the write failed
     * (e.g. disk full) — the caller then stores the event without an image.
     */
    fun save(
        context: Context,
        bitmap: Bitmap,
        format: String = "JPEG",
        quality: Int = AppPrefs.DEFAULT_QUALITY,
        maxDimension: Int = 0,
        overlay: OverlayConfig? = null
    ): String? {
        val (ext, codec) = codecFor(format)
        val prepared = scaled(bitmap, maxDimension)
        val withCaption = if (overlay != null) applyOverlay(prepared, overlay) else prepared
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(directory(context), "shot_$stamp.$ext")
        return runCatching {
            file.outputStream().use { out -> withCaption.compress(codec, quality, out) }
            file.absolutePath
        }.getOrNull()
    }

    /** Re-encodes an existing screenshot with a fresh overlay; returns the new path. */
    fun rewriteWithOverlay(
        context: Context,
        path: String,
        config: OverlayConfig,
        format: String,
        quality: Int
    ): String? {
        val bmp = BitmapFactory.decodeFile(path) ?: return null
        return save(context, bmp, format, quality, maxDimension = 0, overlay = config)
    }


    /** Deletes the file behind [path] if it exists; returns true when something was removed. */
    fun delete(path: String): Boolean {
        if (path.isBlank()) return false
        return File(path).takeIf { it.exists() }?.delete() ?: false
    }

    /** Sum of all screenshot bytes, shown on the dashboard. */
    fun totalBytes(context: Context): Long =
        directory(context).listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * Storage quota enforcement: when the screenshots folder exceeds [quotaMb] (0 =
     * unlimited), delete files oldest-first until back under the cap. Returns freed bytes.
     * Files on disk are authoritative — each one is referenced by a DB row, but a missing
     * file only costs that row its thumbnail.
     */
    fun enforceQuota(context: Context, quotaMb: Int): Long {
        if (quotaMb <= 0) return 0L
        val cap = quotaMb.toLong() * 1024 * 1024
        val files = directory(context).listFiles()?.toMutableList() ?: return 0L
        var total = files.sumOf { it.length() }
        if (total <= cap) return 0L

        var freed = 0L
        files.sortBy { it.lastModified() }
        for (f in files) {
            if (total <= cap) break
            val len = f.length()
            if (f.delete()) {
                total -= len
                freed += len
            }
        }
        return freed
    }
}

/**
 * Everything the caption overlay needs. Built by the service from prefs + event data right
 * before saving; kept as a plain data class so it is trivially testable.
 */
data class OverlayConfig(
    val timeText: String,
    val appLabel: String,
    val actionText: String,
    val showTime: Boolean,
    val showApp: Boolean,
    val showAction: Boolean,
    val positionBottom: Boolean,
    val textScalePercent: Int
)

