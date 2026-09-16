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
import kotlinx.coroutines.flow.combine
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

    /** Application accessor for screens that need PackageManager labels. */
    fun getApp(): Application = getApplication()

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

    // --- Phase 2: search / filters / selection --------------------------------
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedApp = MutableStateFlow<String?>(null)
    val selectedApp: StateFlow<String?> = _selectedApp

    private val _typeFilter = MutableStateFlow<String?>(null) // null | click | switch
    val typeFilter: StateFlow<String?> = _typeFilter

    private val _onlyWithShots = MutableStateFlow(false)
    val onlyWithShots: StateFlow<Boolean> = _onlyWithShots

    private val _dayFilter = MutableStateFlow<Long?>(null) // midnight millis or null
    val dayFilter: StateFlow<Long?> = _dayFilter

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection

    data class TimelineRow(val kind: Int, val event: WorkflowEvent? = null, val dayStart: Long = 0L) {
        companion object { const val HEADER = 0; const val EVENT = 1 }
    }

    /** Timeline with search/filters applied, interleaved with day headers. */
    val visibleRows: StateFlow<List<TimelineRow>> = combine(
        timeline, _searchQuery, _selectedApp, _typeFilter, _onlyWithShots, _dayFilter
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val all = args[0] as List<WorkflowEvent>
        val q = (args[1] as String).trim().lowercase()
        val app = args[2] as String?
        val type = args[3] as String?
        val shotsOnly = args[4] as Boolean
        val day = args[5] as Long?
        var list = all
        if (app != null) list = list.filter { it.packageName == app }
        if (type != null) list = list.filter { it.eventType == type }
        if (shotsOnly) list = list.filter { it.imagePath.isNotBlank() }
        if (day != null) list = list.filter { it.timestamp >= day && it.timestamp < day + DAY_MS }
        if (q.isNotBlank()) {
            list = list.filter {
                it.actionDescription.lowercase().contains(q) ||
                    it.packageName.lowercase().contains(q)
            }
        }
        val rows = ArrayList<TimelineRow>(list.size + 4)
        var lastDay = -1L
        for (e in list) {
            val d = startOfDay(e.timestamp)
            if (d != lastDay) { rows.add(TimelineRow(TimelineRow.HEADER, dayStart = d)); lastDay = d }
            rows.add(TimelineRow(TimelineRow.EVENT, e))
        }
        rows
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Phase 3: pause / theme / time-mode / stats ----------------------------
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused

    private val _theme = MutableStateFlow("system")
    val theme: StateFlow<String> = _theme

    private val _timeMode = MutableStateFlow("relative")
    val timeMode: StateFlow<String> = _timeMode

    private val _stats = MutableStateFlow<com.unsmah.workflowlens.data.WorkflowStats?>(null)
    val stats: StateFlow<com.unsmah.workflowlens.data.WorkflowStats?> = _stats

    /** Undo buffer for the last swipe-delete (event + index-independent reinsert). */
    private var lastDeleted: WorkflowEvent? = null

    /** Zip staged by [buildBackupZip], written to the SAF Uri the user picks. */
    var pendingBackupZip: java.io.File? = null

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        val app = getApplication<Application>()
        _trackerEnabled.value = isServiceEnabled(app)
        _excludedPackages.value = AppPrefs.allowed(app)
        _retentionDays.value = AppPrefs.retentionDays(app)
        _paused.value = AppPrefs.paused(app)
        _theme.value = AppPrefs.theme(app)
        _timeMode.value = AppPrefs.timeMode(app)
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

    // --- v1.3 capture / image / overlay settings -----------------------------

    fun setRecordClicks(on: Boolean) {
        AppPrefs.setRecordClicks(getApplication(), on)
        com.unsmah.workflowlens.service.TrackerService.notifyFiltersChanged()
    }

    fun setRecordWindow(on: Boolean) {
        AppPrefs.setRecordWindow(getApplication(), on)
        com.unsmah.workflowlens.service.TrackerService.notifyFiltersChanged()
    }

    fun setWindowDelayMs(ms: Int) {
        AppPrefs.setWindowDelayMs(getApplication(), ms.coerceIn(0, 1000))
        com.unsmah.workflowlens.service.TrackerService.notifyFiltersChanged()
    }

    fun setImageFormat(format: String) {
        AppPrefs.setImageFormat(getApplication(), format)
    }

    fun setImageQuality(quality: Int) {
        AppPrefs.setImageQuality(getApplication(), quality)
    }

    fun setMaxDimension(value: Int) {
        AppPrefs.setMaxDimension(getApplication(), value)
    }

    fun setOverlayEnabled(on: Boolean) {
        AppPrefs.setOverlayEnabled(getApplication(), on)
    }

    fun setOverlayPosition(bottom: Boolean) {
        AppPrefs.setOverlayPosition(getApplication(), if (bottom) "bottom" else "top")
    }

    fun setOverlayShowTime(on: Boolean) = AppPrefs.setOverlayShowTime(getApplication(), on)

    fun setOverlayShowApp(on: Boolean) = AppPrefs.setOverlayShowApp(getApplication(), on)

    fun setOverlayShowAction(on: Boolean) = AppPrefs.setOverlayShowAction(getApplication(), on)

    fun setOverlayTextScale(percent: Int) {
        AppPrefs.setOverlayTextScale(getApplication(), percent)
    }

    fun setQuotaMb(mb: Int) {
        AppPrefs.setQuotaMb(getApplication(), mb)
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

    /** Deletes one event (row + its screenshot file); used by the lightbox delete action. */
    fun deleteEvent(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            lastDeleted = withContext(Dispatchers.IO) { repository.eventById(id) }
            repository.deleteEvent(id)
        }
    }

    /** Swipe-to-delete with an undo window: removes now, restores on [undoDelete]. */
    fun deleteEventWithUndo(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            lastDeleted = repository.eventById(id)
            repository.deleteEvent(id)
        }
    }

    /** Restores the row removed by the last swipe-delete (image file is already gone). */
    fun undoDelete(onDone: () -> Unit = {}) {
        val e = lastDeleted ?: run { onDone(); return }
        lastDeleted = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.reinsertWithoutImage(e)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    /** Deletes every selected row; clears the selection. Returns the count removed. */
    fun deleteSelected(onDone: (Int) -> Unit) {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) { onDone(0); return }
        viewModelScope.launch(Dispatchers.IO) {
            val n = repository.deleteEvents(ids)
            _selection.value = emptySet()
            withContext(Dispatchers.Main) { onDone(n) }
        }
    }

    // --- filter setters --------------------------------------------------------
    fun setSearch(q: String) { _searchQuery.value = q }
    fun setSelectedApp(pkg: String?) { _selectedApp.value = pkg }
    fun setTypeFilter(t: String?) { _typeFilter.value = t }
    fun setOnlyWithShots(v: Boolean) { _onlyWithShots.value = v }
    fun setDayFilter(dayStart: Long?) { _dayFilter.value = dayStart }
    fun clearFilters() {
        _searchQuery.value = ""; _selectedApp.value = null
        _typeFilter.value = null; _onlyWithShots.value = false; _dayFilter.value = null
    }
    fun hasActiveFilters(): Boolean =
        _searchQuery.value.isNotBlank() || _selectedApp.value != null ||
            _typeFilter.value != null || _onlyWithShots.value || _dayFilter.value != null

    // --- selection ---------------------------------------------------------------
    fun toggleSelect(id: Long) {
        val cur = _selection.value.toMutableSet()
        if (id in cur) cur.remove(id) else cur.add(id)
        _selection.value = cur
    }
    fun clearSelection() { _selection.value = emptySet() }
    fun selectAllVisible() {
        _selection.value = visibleRows.value
            .mapNotNull { it.event?.id }.toSet()
    }

    // --- pause / theme / time-mode --------------------------------------------------
    fun setPaused(p: Boolean) {
        AppPrefs.setPaused(getApplication(), p)
        _paused.value = p
        com.unsmah.workflowlens.service.TrackerService.notifyFiltersChanged()
    }

    fun setTheme(t: String) {
        AppPrefs.setTheme(getApplication(), t)
        _theme.value = t
    }

    fun setTimeMode(m: String) {
        AppPrefs.setTimeMode(getApplication(), m)
        _timeMode.value = m
    }

    // --- stats / share / export -----------------------------------------------------------
    fun loadStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _stats.value = repository.stats()
        }
    }

    /** Shares one event: FileProvider image + caption text via the system sheet. */
    fun shareEvent(context: android.content.Context, event: WorkflowEvent) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        val file = event.imagePath.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
        if (file != null && file.exists()) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file)
            send.type = "image/*"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val whenText = java.text.SimpleDateFormat("d MMM yyyy, HH:mm",
            java.util.Locale.getDefault()).format(java.util.Date(event.timestamp))
        send.putExtra(Intent.EXTRA_TEXT, "${event.actionDescription} — $whenText")
        context.startActivity(Intent.createChooser(send, "Share event"))
    }

    /** Shares many events: all attached images + a text summary. */
    fun shareSelected(context: android.content.Context) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val rows = repository.eventsInWindow(0L, Long.MAX_VALUE).filter { it.id in ids }
            val uris = rows.mapNotNull { e ->
                e.imagePath.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
                    ?.takeIf { it.exists() }?.let {
                        androidx.core.content.FileProvider.getUriForFile(
                            context, context.packageName + ".fileprovider", it)
                    }
            }
            withContext(Dispatchers.Main) {
                val send = Intent(
                    if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
                ).apply { type = if (uris.isEmpty()) "text/plain" else "image/*" }
                if (uris.size > 1) send.putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM, ArrayList(uris))
                else if (uris.size == 1) send.putExtra(Intent.EXTRA_STREAM, uris[0])
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.putExtra(Intent.EXTRA_TEXT,
                    rows.joinToString("
") { it.actionDescription }.take(4000))
                context.startActivity(Intent.createChooser(send, "Share ${rows.size} events"))
            }
        }
    }

    /**
     * Full backup: CSV of every row in the range + all referenced screenshots, zipped and
     * handed to the system file picker (SAF) via [onReady]. Call from an Activity result
     * launcher with the user-chosen Uri.
     */
    fun buildBackupZip(from: Long, to: Long): java.io.File? {
        val rows = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            repository.eventsInWindow(from, to)
        }
        if (rows.isEmpty()) return null
        val app = getApplication<Application>()
        val zip = java.io.File(app.cacheDir, "workflow-lens-backup.zip")
        runCatching { if (zip.exists()) zip.delete() }
        java.util.zip.ZipOutputStream(zip.outputStream().buffered()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("events.csv"))
            z.write("id,timestamp,package,action,type,image
".toByteArray())
            for (e in rows) {
                val q = { v: String -> """ + v.replace(""", """") + """ }
                z.write(("${e.id},${e.timestamp},${e.packageName}," +
                    "${q(e.actionDescription)},${e.eventType}," +
                    "${java.io.File(e.imagePath).name}
").toByteArray())
            }
            z.closeEntry()
            for (e in rows) {
                val f = e.imagePath.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
                if (f != null && f.exists()) {
                    z.putNextEntry(java.util.zip.ZipEntry("images/" + f.name))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        return zip
    }

    /**
     * Re-encodes every stored screenshot with the current overlay settings so old images
     * match the new caption style. Reports the number processed when finished.
     */
    fun overlayHistory(onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val n = repository.overlayAllWithCurrentOverlay()
            withContext(Dispatchers.Main) { onDone(n) }
        }
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
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** Midnight of the day containing [ts], in local time. */
        fun startOfDay(ts: Long): Long {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = ts
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

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
