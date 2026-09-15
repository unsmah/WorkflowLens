package com.unsmah.workflowlens.ui

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unsmah.workflowlens.data.AppPrefs
import com.unsmah.workflowlens.data.WorkflowEvent
import com.unsmah.workflowlens.data.WorkflowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One launchable app shown in the filter sheet. */
data class AppEntry(val packageName: String, val label: String)

/**
 * MVVM: exposes the Room timeline as StateFlow, resolves app icons lazily and owns the
 * "is the tracker enabled" state, which is polled on resume because the system can flip it
 * from the Accessibility settings screen.
 */
class TimelineViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = WorkflowRepository.get(app)

    val timeline: StateFlow<List<WorkflowEvent>> = repository.timeline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _trackerEnabled = MutableStateFlow(false)
    val trackerEnabled: StateFlow<Boolean> = _trackerEnabled

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes

    /** Packages the user excluded from tracking (empty = track everything). */
    private val _excludedPackages = MutableStateFlow<Set<String>>(emptySet())
    val excludedPackages: StateFlow<Set<String>> = _excludedPackages

    /** User-facing list of installed launchable apps for the filter sheet. */
    private val _installedApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val installedApps: StateFlow<List<AppEntry>> = _installedApps

    /** Retention window in days (1–30). */
    private val _retentionDays = MutableStateFlow(AppPrefs.DEFAULT_RETENTION_DAYS)
    val retentionDays: StateFlow<Int> = _retentionDays

    /** True briefly after [testCapture] so the UI can flash a confirmation. */
    private val _testCaptureDone = MutableStateFlow(false)
    val testCaptureDone: StateFlow<Boolean> = _testCaptureDone

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        val app = getApplication<Application>()
        _trackerEnabled.value = isServiceEnabled(app)
        _excludedPackages.value = AppPrefs.allowed(app)
        _retentionDays.value = AppPrefs.retentionDays(app)
        viewModelScope.launch(Dispatchers.IO) {
            _storageBytes.value =
                com.unsmah.workflowlens.data.ScreenshotStore.totalBytes(app)
        }
    }

    /** Loads the launchable app list once for the filter sheet (label-sorted). */
    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            _installedApps.value = apps
        }
    }

    fun toggleExcluded(pkg: String) {
        val app = getApplication<Application>()
        val current = AppPrefs.allowed(app).toMutableSet()
        if (pkg in current) current.remove(pkg) else current.add(pkg)
        AppPrefs.setAllowed(app, current)
        _excludedPackages.value = current
        com.unsmah.workflowlens.service.TrackerService.notifyFiltersChanged()
    }

    fun setRetentionDays(days: Int) {
        val app = getApplication<Application>()
        val clamped = days.coerceIn(1, 30)
        AppPrefs.setRetentionDays(app, clamped)
        _retentionDays.value = clamped
    }

    /** Clears the one-shot test-capture flag after its snackbar showed. */
    fun resetTestCaptureFlag() {
        _testCaptureDone.value = false
    }

    /**
     * Writes one synthetic row (with a real screenshot of whatever is on screen is not
     * possible from the activity, so this records a text-only event) — proves the whole
     * pipeline from DB to UI works even before the service sees real events.
     */
    fun testCapture() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.record(
                timestamp = System.currentTimeMillis(),
                packageName = getApplication<Application>().packageName,
                actionDescription = "Test event — the pipeline works!",
                bitmap = null,
                tracked = true
            )
            _testCaptureDone.value = true
        }
    }

    /** Opens the system Accessibility settings so the user can switch the service on. */
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { repository.clearAll() }
    }

    /** Icon for a package, cached in memory; null when the app is gone/uninstalled. */
    suspend fun iconFor(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        iconCache[packageName] ?: runCatching {
            getApplication<Application>().packageManager
                .getApplicationIcon(packageName)
        }.getOrNull()?.also { iconCache[packageName] = it }
    }

    /** Short app label ("Chrome" instead of com.android.chrome). */
    suspend fun labelFor(packageName: String): String = withContext(Dispatchers.IO) {
        labelCache[packageName] ?: runCatching {
            val pm = getApplication<Application>().packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName).also { labelCache[packageName] = it }
    }

    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, Drawable?>()
    private val labelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    companion object {
        /** True when TrackerService is switched on in the system's accessibility list. */
        fun isServiceEnabled(context: Application): Boolean {
            val expected = android.content.ComponentName(context, com.unsmah.workflowlens.service.TrackerService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = android.text.TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            for (entry in splitter) {
                if (entry.equals(expected.flattenToString(), ignoreCase = true)) return true
            }
            return false
        }
    }
}
