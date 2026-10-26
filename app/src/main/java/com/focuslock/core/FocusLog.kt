package com.focuslock.core

import android.util.Log
import com.focuslock.BuildConfig

/**
 * Debug-only diagnostics.
 *
 * Enforcement events are logged so the blocking path can be measured with
 * `adb logcat -s FocusLock` on a debug build. Only package identifiers and timings are
 * recorded; no content, keystrokes, web history or personal usage data is ever captured.
 */
object FocusLog {
    private const val TAG = "FocusLock"
    private val enabled: Boolean get() = BuildConfig.DEBUG

    fun d(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun w(message: String, tr: Throwable? = null) {
        if (enabled) Log.w(TAG, message, tr)
    }

    fun e(message: String, tr: Throwable? = null) {
        if (enabled) Log.e(TAG, message, tr)
    }
}

/**
 * Measures the redirect latency of the blocking path (event received -> focus screen launch)
 * so measured numbers can be compared with the 2-3 s of the previous prototype.
 */
class TimingSample(private val label: String, private val startedAtNanos: Long = System.nanoTime()) {
    fun finish(detail: String = "") {
        val millis = (System.nanoTime() - startedAtNanos) / 1_000_000.0
        FocusLog.d("$label took ${"%.2f".format(millis)} ms $detail")
    }
}
