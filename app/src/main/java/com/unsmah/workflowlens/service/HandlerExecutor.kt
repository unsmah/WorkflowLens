package com.unsmah.workflowlens.service

import android.os.Handler
import android.os.Looper

/**
 * Minimal java.util.concurrent.Executor that runs tasks on a Handler — lets us hand
 * takeScreenshot() a main-looper executor without pulling in executors' thread overhead.
 */
class HandlerExecutor(private val handler: Handler) : java.util.concurrent.Executor {
    override fun execute(command: Runnable) {
        handler.post(command)
    }
}
