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

    /** Empty set = track everything. */
    fun allowed(context: Context): Set<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getStringSet(KEY_ALLOWED, null) ?: emptySet()

    fun retentionDays(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)

    fun setAllowed(context: Context, pkgs: Set<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_ALLOWED, pkgs).apply()
    }

    fun setRetentionDays(context: Context, days: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_RETENTION_DAYS, days).apply()
    }
}
