package com.unsmah.workflowlens.ui

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unsmah.workflowlens.data.WorkflowEvent
import com.unsmah.workflowlens.data.WorkflowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun refreshStatus() {
        _trackerEnabled.value = isServiceEnabled(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            _storageBytes.value =
                com.unsmah.workflowlens.data.ScreenshotStore.totalBytes(getApplication())
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

    private val iconCache = HashMap<String, Drawable?>()
    private val labelCache = HashMap<String, String>()

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
