package com.focuslock.session

import android.content.Context
import com.focuslock.core.Clock
import com.focuslock.core.FocusLog
import com.focuslock.data.FocusRepository
import com.focuslock.data.model.ActiveSession
import com.focuslock.enforcement.KioskManager
import com.focuslock.enforcement.ScheduleAlarmScheduler
import com.focuslock.web.WebsiteFilterController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Applies the side effects of session transitions: alarms, the website filter tunnel and
 * strict-mode lock task whitelisting.
 *
 * The UI is not involved at all, so these effects happen even when the app was started in the
 * background by an alarm or by the accessibility service.
 */
class SessionCoordinator(
    private val context: Context,
    private val repository: FocusRepository,
    private val alarms: ScheduleAlarmScheduler,
    private val websiteFilter: WebsiteFilterController,
    private val kiosk: KioskManager,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {

    fun start() {
        scope.launch {
            repository.state
                .map { snapshot -> snapshot.session }
                .distinctUntilChanged()
                .collect { session -> onSessionChanged(session) }
        }
        scope.launch {
            repository.state
                .map { snapshot -> snapshot.schedules }
                .distinctUntilChanged()
                .collect { schedules -> alarms.scheduleNextStart(schedules) }
        }
        scope.launch {
            // Expired sessions must not survive a process restart.
            repository.reconcile()
        }
        alarms.scheduleNextStart(repository.state.value.schedules)
        syncEnforcementWithSession()
    }

    /** Re-applies enforcement after a reboot or when recovery is needed. */
    fun syncEnforcementWithSession() {
        val now = clock.nowMillis()
        val session = repository.activeSession(now)
        if (session != null) {
            alarms.scheduleSessionEnd(session)
            websiteFilter.sync(session)
            applyStrictMode(session)
        } else {
            alarms.cancelSessionEnd()
            websiteFilter.stop()
            kiosk.clearLockTaskPackages()
        }
    }

    /**
     * Lock-task packages are only whitelisted when the user enabled strict mode; otherwise
     * the app behaves exactly like ordinary mode even on a device-owner provisioned phone.
     */
    private fun applyStrictMode(session: ActiveSession) {
        if (!repository.state.value.settings.strictModeEnabled) {
            kiosk.clearLockTaskPackages()
            return
        }
        kiosk.applyLockTaskPackages(session)
    }

    private fun onSessionChanged(session: ActiveSession?) {
        val now = clock.nowMillis()
        val active = session?.takeIf { it.isActiveAt(now) }
        if (active != null) {
            FocusLog.d("Session started until ${active.endAtMillis}")
            alarms.scheduleSessionEnd(active)
            websiteFilter.sync(active)
            applyStrictMode(active)
        } else {
            if (session != null) FocusLog.d("Session ended")
            alarms.cancelSessionEnd()
            websiteFilter.stop()
            kiosk.clearLockTaskPackages()
        }
        alarms.scheduleNextStart(repository.state.value.schedules)
    }
}
