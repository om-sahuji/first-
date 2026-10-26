package com.focuslock.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.focuslock.FocusLockApp
import com.focuslock.core.FocusLog
import com.focuslock.core.TimingSample
import com.focuslock.data.apps.PermittedSystemPackages
import com.focuslock.domain.BlockingPolicy
import com.focuslock.ui.FocusIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The enforcement anchor.
 *
 * Android keeps an enabled accessibility service alive independently of the app's UI, and
 * grants it the one capability ordinary apps cannot otherwise have: observing which window is
 * in front, and starting an activity from the background. That is what makes blocking work
 * when Focus Lock is not running in the foreground.
 *
 * The hot path is deliberately tiny: read the window's package name from the event, look it up
 * in pre-computed sets, launch the Focus screen. No package-manager queries, no disk access,
 * no polling, no repeated launches of blocked apps.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val container get() = (application as FocusLockApp).container

    @Volatile
    private var permitted: PermittedSystemPackages? = null

    private var sessionJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var lastRedirectUptime = 0L
    private var consecutiveRedirects = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        FocusLog.d("Accessibility service connected")
        refreshPermittedPackages()
        // Permanent/system packages rarely change, but a session start is a good moment to
        // rebuild the cache (for example when the default dialer was changed since boot).
        sessionJob = scope.launch {
            container.repository.state
                .map { it.session?.startedAtMillis }
                .distinctUntilChanged()
                .collect { refreshPermittedPackages() }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        val foregroundPackage = event.packageName?.toString() ?: return

        // Our own UI becoming visible ends a redirect burst; this prevents a backoff from
        // being applied after successful redirects.
        if (foregroundPackage == packageName) {
            consecutiveRedirects = 0
            return
        }

        val session = container.repository.activeSession() ?: return
        val systemPackages = permitted ?: refreshPermittedPackages()

        if (!BlockingPolicy.shouldRedirect(
                packageName = foregroundPackage,
                session = session,
                nowMillis = System.currentTimeMillis(),
                alwaysAllowedPackages = systemPackages.alwaysAllowed,
                selfPackage = this.packageName,
            )
        ) {
            return
        }
        redirect(foregroundPackage)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        sessionJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshPermittedPackages(): PermittedSystemPackages {
        val resolved = container.systemApps.permittedPackages(forceRefresh = true)
        permitted = resolved
        return resolved
    }

    private fun redirect(blockedPackage: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirectUptime > REDIRECT_BURST_WINDOW_MS) {
            consecutiveRedirects = 0
        }
        val minimumInterval = if (consecutiveRedirects >= 3) BACKOFF_INTERVAL_MS else MIN_REDIRECT_INTERVAL_MS
        if (now - lastRedirectUptime < minimumInterval) {
            FocusLog.d("Redirect throttled for $blockedPackage")
            return
        }

        consecutiveRedirects += 1
        lastRedirectUptime = now
        val timing = TimingSample("redirect of $blockedPackage")

        val intent = FocusIntents.focus(this).apply {
            putExtra(FocusIntents.EXTRA_BLOCKED_PACKAGE, blockedPackage)
        }
        try {
            startActivity(intent)
            timing.finish("(attempt $consecutiveRedirects)")
        } catch (error: Exception) {
            FocusLog.w("Background launch refused for $blockedPackage, using HOME", error)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private companion object {
        /** Guards against the same app resuming twice within one gesture. */
        const val MIN_REDIRECT_INTERVAL_MS = 400L

        /** Used when several redirects in a row did not bring our UI to the front. */
        const val BACKOFF_INTERVAL_MS = 1_500L

        const val REDIRECT_BURST_WINDOW_MS = 2_000L
    }
}
