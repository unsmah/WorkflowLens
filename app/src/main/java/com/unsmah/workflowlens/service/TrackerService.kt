package com.unsmah.workflowlens.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.unsmah.workflowlens.R
import com.unsmah.workflowlens.data.AppPrefs
import com.unsmah.workflowlens.data.WorkflowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Component A. Listens for clicks and window transitions, builds a semantic sentence from
 * the event, captures the screen and stores both via [WorkflowRepository].
 *
 * Threading model:
 *  - onAccessibilityEvent runs on the system's binder thread: it only reads event fields,
 *    fires the async screenshot and returns (never blocks, never touches storage).
 *  - takeScreenshot delivers on [screenshotExecutor] (main looper). We copy the hardware
 *    buffer into an immutable bitmap there and hop to Dispatchers.IO for file + DB writes.
 *
 * Fail-safe policy: capture failures and bitmap conversion nulls still record the action —
 * only the image is missing, the timeline never loses the row.
 */
class TrackerService : AccessibilityService() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        /** Live instance (when the system has the service bound) for pushing filter updates. */
        @Volatile
        var instance: TrackerService? = null
            private set

        const val MAX_TEXT_CHARS = 60
        /** One retry for transient capture errors (mid-transition races). */
        private const val CAPTURE_RETRY_DELAY_MS = 350L

        /** Pushes fresh prefs into the running service; no-op when it is not bound. */
        fun notifyFiltersChanged() {
            runCatching { instance?.reloadFilters() }
        }
    }

    /** User-configurable package filter; empty set = track everything. */
    private val allowedPackages = HashSet<String>()

    /** Cached capture prefs (reloaded in [reloadFilters], never read per event). */
    @Volatile private var recordClicks = true
    @Volatile private var recordWindow = true
    @Volatile private var windowDelayMs = 400L
    @Volatile private var isPaused = false
    @Volatile private var skipScreenOff = true

    /** Dedupes rapid-fire window events (focus storms etc.) within a short window. */
    private val recentKeys = LinkedHashMap<String, Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var repository: WorkflowRepository? = null

    private val screenshotExecutor by lazy { HandlerExecutor(Handler(Looper.getMainLooper())) }

    /** Schedules delayed captures for window transitions on the main looper. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** True when the display is on/interactive; cheap, safe to call per event. */
    private fun isScreenOn(): Boolean = runCatching {
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        @Suppress("DEPRECATION") pm.isInteractive
    }.getOrDefault(true)

    //region Lifecycle
    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            repository = WorkflowRepository.get(this)
            reloadFilters()
            isRunning = true
            instance = this
        } catch (t: Throwable) {
            // A crash here would silently kill tracking; log and keep the service alive.
            android.util.Log.e("TrackerService", "onServiceConnected failed", t)
        }
    }

    override fun onDestroy() {
        isRunning = false
        instance = null
        repository = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        // Required override; we only listen, nothing to interrupt.
    }
    //endregion

    /** Re-reads all runtime settings (called on connect and whenever settings change). */
    fun reloadFilters() {
        allowedPackages.clear()
        allowedPackages.addAll(AppPrefs.allowed(this))
        recordClicks = AppPrefs.recordClicks(this)
        recordWindow = AppPrefs.recordWindow(this)
        windowDelayMs = AppPrefs.windowDelayMs(this).coerceIn(0, 2000).toLong()
        isPaused = AppPrefs.paused(this)
        skipScreenOff = AppPrefs.skipScreenOff(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || isPaused) return
        val e = event ?: return
        val repo = repository ?: return
        if (skipScreenOff && !isScreenOn()) return

        val isClick = e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        val isWindow = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!isClick && !isWindow) return
        // User-captured behaviour prefs (cached in reloadFilters, never read per event).
        if (isClick && !recordClicks) return
        if (isWindow && !recordWindow) return

        // 1) Package + filter. The tracker itself and system UI are never recorded.
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (allowedPackages.isNotEmpty() && pkg !in allowedPackages) return

        // 2) Semantic description; falls back through text -> description -> class name.
        val description = describe(e, isClick) ?: return

        // 3) Dedupe repeated window events (keyboard show/hide, focus storms).
        val key = "$pkg/$description/${e.eventType}"
        val now = System.currentTimeMillis()
        synchronized(recentKeys) {
            val last = recentKeys[key]
            if (!isClick && last != null && now - last < 1500) return
            recentKeys[key] = now
            if (recentKeys.size > 24) {
                val itr = recentKeys.entries.iterator()
                repeat(8) { if (itr.hasNext()) { itr.next(); itr.remove() } }
            }
        }

        // 4) Capture. Window-state events are delayed slightly: the system fires them while
        //    the app transition is still in flight, and an instant capture can race the
        //    swap (failing outright or shooting the old screen). Clicks fire while the UI
        //    is stable, so those go out immediately. The delay is user-configurable.
        val delayMs = if (isClick) 0L else windowDelayMs
        val type = if (isClick) "click" else "switch"
        mainHandler.postDelayed({
            if (!isRunning || isPaused) return@postDelayed
            captureAndStore(repo, now, pkg, description, eventType = type)
        }, delayMs)
    }

    /**
     * Builds "Clicked \"Submit\" (Button)" / "Opened Chrome" style sentences.
     * Falls back through: event text -> content-description -> class-derived label, and
     * only returns null when the event truly has no identifying information (rare).
     */
    private fun describe(e: AccessibilityEvent, isClick: Boolean): String? {
        val text = e.text.orEmpty().joinToString(" ") { it?.toString() ?: "" }.trim()
        val desc = e.contentDescription?.toString()?.trim().orEmpty()
        val rawClass = e.className?.toString().orEmpty()
        val simple = rawClass.substringAfterLast('.')

        val verb = if (isClick) getString(R.string.action_clicked)
        else getString(R.string.action_opened)

        val subject = when {
            text.isNotBlank() -> "\"${text.take(MAX_TEXT_CHARS)}\""
            desc.isNotBlank() -> "\"${desc.take(MAX_TEXT_CHARS)}\""
            else -> ""
        }

        return when {
            subject.isNotBlank() && simple.isNotBlank() -> "$verb $subject ($simple)"
            subject.isNotBlank() -> "$verb $subject"
            simple.isNotBlank() -> "$verb a $simple element"
            isClick -> "$verb something"
            else -> null
        }
    }

    //region Screenshot + persistence
    /**
     * Fires the asynchronous screen capture. The callback lands on the main-looper
     * executor; from there we hop to Dispatchers.IO so the Room transaction and the JPEG
     * write never block the looper. See the class kdoc for the full threading map.
     */
    private fun captureAndStore(
        repo: WorkflowRepository,
        timestamp: Long,
        pkg: String,
        description: String,
        eventType: String = "click",
        attempt: Int = 1
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap: Bitmap? = try {
                            Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer, screenshot.colorSpace
                            )?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (t: Throwable) {
                            null
                        } finally {
                            runCatching { screenshot.hardwareBuffer.close() }
                        }
                        persist(repo, timestamp, pkg, description, bitmap,
                            if (bitmap == null) "could not read the screen buffer" else null, eventType)
                    }

                    override fun onFailure(errorCode: Int) {
                        val reason = when (errorCode) {
                            ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "screen was mid-transition"
                            ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "capture permission revoked"
                            ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "display unavailable"
                            else -> "capture failed (code $errorCode)"
                        }
                        // INTERNAL_ERROR is the classic "fired during an app swap" race —
                        // one short retry usually lands a clean frame of the new app.
                        if (errorCode == ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR && attempt < 2) {
                            mainHandler.postDelayed({
                                if (isRunning) captureAndStore(repo, timestamp, pkg, description, eventType, attempt + 1)
                            }, CAPTURE_RETRY_DELAY_MS)
                            return
                        }
                        android.util.Log.w("TrackerService", "screenshot failed: $errorCode")
                        persist(repo, timestamp, pkg, description, null, reason, eventType)
                    }
                })
        } catch (t: Throwable) {
            // Can throw SecurityException if canTakeScreenshot was revoked at runtime.
            android.util.Log.e("TrackerService", "takeScreenshot threw", t)
            persist(repo, timestamp, pkg, description, null, "capture threw ${t.javaClass.simpleName}", eventType)
        }
    }

    /** Runs on the main-looper executor; hops to IO so storage never blocks the looper. */
    private fun persist(
        repo: WorkflowRepository,
        timestamp: Long,
        pkg: String,
        description: String,
        bitmap: Bitmap?,
        failureNote: String?,
        eventType: String = "click"
    ) {
        scope.launch {
            try {
                repo.record(timestamp, pkg, description, bitmap, tracked = true,
                    failureNote = failureNote, eventType = eventType)
            } catch (t: Throwable) {
                android.util.Log.e("TrackerService", "record failed", t)
            }
        }
    }
    //endregion
}
