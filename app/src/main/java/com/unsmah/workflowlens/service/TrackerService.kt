package com.unsmah.workflowlens.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.unsmah.workflowlens.R
import com.unsmah.workflowlens.data.WorkflowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Component A. Listens for clicks and window transitions, builds a semantic sentence from
 * the event, captures the screen and stores both via [WorkflowRepository].
 *
 * Lifecycle notes
 *  - The system binds/unbinds this service; a bound service has no onCreated/onDestroyed
 *    guarantee pair like activities, so the repository handle is (re)created lazily in
 *    onServiceConnected and cleared in onDestroy/onUnbind.
 *  - takeScreenshot() result arrives on [screenshotExecutor]; the DB write itself happens
 *    on Dispatchers.IO inside a coroutine (see [onScreenshot]).
 */
class TrackerService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var repository: WorkflowRepository? = null

    private val screenshotExecutor by lazy {
        HandlerExecutor(Handler(Looper.getMainLooper()))
    }

    /** User-maintained blocklist broadcast from the dashboard (package -> track/dim). */
    private val filterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_FILTERS_CHANGED) return
            val newAllowed = intent.getStringArrayListExtra(EXTRA_ALLOWED) ?: return
            allowedPackages = newAllowed.toSet()
        }
    }

    private var allowedPackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = WorkflowRepository.get(this)
        registerReceiver(filterReceiver, IntentFilter(ACTION_FILTERS_CHANGED))
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(filterReceiver) }
        repository = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        // Required override; nothing to interrupt — we only listen.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = repository ?: return
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        val pkg = e.packageName?.toString() ?: return
        val description = describe(e) ?: return // nothing human-readable -> don't log noise
        val now = System.currentTimeMillis()

        // 1) Fire the async screenshot; the DB write happens in the callback, so the
        //    accessibility thread is never blocked by storage.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer, screenshot.colorSpace
                        )?.copy(Bitmap.Config.ARGB_8888, false)
                        screenshot.hardwareBuffer.close()
                        onScreenshot(current, now, pkg, description, bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        // Still log the action — only the image is missing.
                        onScreenshot(current, now, pkg, description, null)
                    }
                })
        }
    }

    /** Runs on the executor thread; hops into IO to persist row + blob. */
    private fun onScreenshot(
        repo: WorkflowRepository,
        timestamp: Long,
        pkg: String,
        description: String,
        bitmap: Bitmap?
    ) {
        scope.launch {
            val tracked = pkg in allowedPackages || allowedPackages.isEmpty()
            repo.record(timestamp, pkg, description, bitmap, tracked)
        }
    }

    /** "Clicked 'Submit' in Chrome" / "Opened com.example.app". */
    private fun describe(e: AccessibilityEvent): String? {
        val text = e.text.orEmpty().joinToString(" ") { it.toString() }.trim()
        val cls = e.className?.toString()?.substringAfterLast('.') ?: ""
        val verb = if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            getString(R.string.action_clicked)
        } else {
            getString(R.string.action_opened)
        }
        return when {
            text.isNotBlank() && cls.isNotBlank() -> "$verb '$text' ($cls)"
            text.isNotBlank() -> "$verb '$text'"
            cls.isNotBlank() -> "$verb a $cls"
            else -> null
        }
    }

    companion object {
        const val ACTION_FILTERS_CHANGED = "com.unsmah.workflowlens.FILTERS_CHANGED"
        const val EXTRA_ALLOWED = "allowed_packages"
    }
}
