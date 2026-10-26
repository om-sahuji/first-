package com.focuslock.core

import android.os.SystemClock

/**
 * Debug-only startup probe.
 *
 * Records process start -> first composed frame so launch cost can be measured separately from
 * blocking latency. Values are only written to logcat in debug builds and are never collected
 * or transmitted.
 */
object StartupTrace {

    @Volatile
    private var applicationCreatedAtUptime = 0L

    fun onApplicationCreate() {
        applicationCreatedAtUptime = SystemClock.uptimeMillis()
        FocusLog.d("process created")
    }

    fun onFirstFrame() {
        val startedAt = applicationCreatedAtUptime
        if (startedAt == 0L) {
            FocusLog.d("first frame (app create time unknown)")
            return
        }
        FocusLog.d("first frame ${SystemClock.uptimeMillis() - startedAt} ms after application create")
    }
}
