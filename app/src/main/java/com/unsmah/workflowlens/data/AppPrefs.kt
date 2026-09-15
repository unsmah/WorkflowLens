package com.unsmah.workflowlens.data

import android.content.Context

/**
 * Tiny settings store shared by the service (reads) and the dashboard (writes).
 * Lives in the same process, so SharedPreferences are always consistent.
 */
object AppPrefs {
    const val FILE = "workflow_lens_settings"
    const val KEY_ALLOWED = "allowed_packages"
    const val KEY_RETENTION_DAYS = "retention_days"
    const val DEFAULT_RETENTION_DAYS = 7

    // Capture behaviour (v1.3)
    const val KEY_RECORD_CLICKS = "record_clicks"
    const val KEY_RECORD_WINDOW = "record_window"
    const val KEY_WINDOW_DELAY_MS = "window_delay_ms"
    const val DEFAULT_WINDOW_DELAY_MS = 400

    // Image output (v1.3)
    const val KEY_FORMAT = "image_format"         // JPEG | WEBP | PNG
    const val KEY_QUALITY = "image_quality"       // 50..100
    const val KEY_MAX_DIMENSION = "max_dimension" // 0 = original
    const val DEFAULT_QUALITY = 80

    // Overlay (v1.3) — text burned into the screenshot
    const val KEY_OVERLAY_ENABLED = "overlay_enabled"
    const val KEY_OVERLAY_POSITION = "overlay_position" // bottom | top
    const val KEY_OVERLAY_SHOW_TIME = "overlay_show_time"
    const val KEY_OVERLAY_SHOW_APP = "overlay_show_app"
    const val KEY_OVERLAY_SHOW_ACTION = "overlay_show_action"
    const val KEY_OVERLAY_TEXT_SCALE = "overlay_text_scale" // 60..140 (%)

    // Storage quota (v1.3) — LRU pruning; 0 = unlimited
    const val KEY_QUOTA_MB = "quota_mb"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // --- Read helpers ---------------------------------------------------------

    /** Empty set = track everything. */
    fun allowed(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALLOWED, null) ?: emptySet()

    fun retentionDays(context: Context): Int =
        prefs(context).getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)

    fun recordClicks(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RECORD_CLICKS, true)

    fun recordWindow(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RECORD_WINDOW, true)

    fun windowDelayMs(context: Context): Int =
        prefs(context).getInt(KEY_WINDOW_DELAY_MS, DEFAULT_WINDOW_DELAY_MS)

    fun imageFormat(context: Context): String =
        prefs(context).getString(KEY_FORMAT, "JPEG") ?: "JPEG"

    fun imageQuality(context: Context): Int =
        prefs(context).getInt(KEY_QUALITY, DEFAULT_QUALITY)

    /** 0 = original resolution, otherwise the largest side in px (720/1080/1440). */
    fun maxDimension(context: Context): Int =
        prefs(context).getInt(KEY_MAX_DIMENSION, 0)

    fun overlayEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_ENABLED, true)

    fun overlayPosition(context: Context): String =
        prefs(context).getString(KEY_OVERLAY_POSITION, "bottom") ?: "bottom"

    fun overlayShowTime(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_SHOW_TIME, true)

    fun overlayShowApp(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_SHOW_APP, true)

    fun overlayShowAction(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_SHOW_ACTION, true)

    fun overlayTextScale(context: Context): Int =
        prefs(context).getInt(KEY_OVERLAY_TEXT_SCALE, 100)

    /** 0 = unlimited, otherwise the cap in megabytes. */
    fun quotaMb(context: Context): Int =
        prefs(context).getInt(KEY_QUOTA_MB, 0)

    // --- Write helpers --------------------------------------------------------

    fun setAllowed(context: Context, pkgs: Set<String>) =
        prefs(context).edit().putStringSet(KEY_ALLOWED, pkgs).apply()

    fun setRetentionDays(context: Context, days: Int) =
        prefs(context).edit().putInt(KEY_RETENTION_DAYS, days).apply()

    fun setRecordClicks(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_RECORD_CLICKS, value).apply()

    fun setRecordWindow(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_RECORD_WINDOW, value).apply()

    fun setWindowDelayMs(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_WINDOW_DELAY_MS, value).apply()

    fun setImageFormat(context: Context, value: String) =
        prefs(context).edit().putString(KEY_FORMAT, value).apply()

    fun setImageQuality(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_QUALITY, value.coerceIn(50, 100)).apply()

    fun setMaxDimension(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_MAX_DIMENSION, value).apply()

    fun setOverlayEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    fun setOverlayPosition(context: Context, value: String) =
        prefs(context).edit().putString(KEY_OVERLAY_POSITION, value).apply()

    fun setOverlayShowTime(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_OVERLAY_SHOW_TIME, value).apply()

    fun setOverlayShowApp(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_OVERLAY_SHOW_APP, value).apply()

    fun setOverlayShowAction(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_OVERLAY_SHOW_ACTION, value).apply()

    fun setOverlayTextScale(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_OVERLAY_TEXT_SCALE, value.coerceIn(60, 140)).apply()

    fun setQuotaMb(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_QUOTA_MB, value).apply()
}
