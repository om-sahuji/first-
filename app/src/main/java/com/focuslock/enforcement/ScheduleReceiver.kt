package com.focuslock.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.FocusLockApp
import com.focuslock.core.FocusLog
import com.focuslock.data.model.SessionRules
import com.focuslock.data.model.SessionSource
import com.focuslock.domain.ScheduleMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Starts scheduled sessions.
 *
 * The session is started in the background without stealing focus: Android does not allow an
 * alarm receiver to launch UI. The Focus screen appears the moment the user opens the app or
 * touches a blocked app, and enforcement is already live in the meantime.
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SCHEDULE_START) return
        val container = (context.applicationContext as FocusLockApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val nowMillis = container.clock.nowMillis()
                container.repository.reconcile(nowMillis)
                val now = Instant.ofEpochMilli(nowMillis).atZone(container.clock.zone())
                val schedules = container.repository.state.value.schedules
                val due = schedules.firstOrNull { schedule ->
                    ScheduleMath.isDueNow(schedule, now) && !ScheduleMath.alreadyStartedToday(schedule, now)
                }
                val sessionActive = container.repository.activeSession(nowMillis) != null

                if (due != null && !sessionActive) {
                    val permanent = container.systemApps.permittedPackages().alwaysAllowed
                    val packages = SessionRules.sanitizeAdditionalApps(due.allowedPackages, permanent)
                    val result = container.repository.startSession(
                        durationMinutes = due.durationMinutes,
                        allowedPackages = packages,
                        allowedDomains = due.allowedDomains,
                        websiteFilterEnabled = due.websiteFilterEnabled,
                        source = SessionSource.SCHEDULE,
                        scheduleId = due.id,
                    )
                    FocusLog.d("Scheduled start for ${due.id}: $result")
                    container.repository.markScheduleStarted(due.id, now.toLocalDate().toEpochDay())
                } else if (due != null) {
                    // A manual session is already running; skip this occurrence so the
                    // running session's configuration cannot be changed underneath it.
                    FocusLog.d("Skipping schedule ${due.id}: a session is already active")
                    container.repository.markScheduleStarted(due.id, now.toLocalDate().toEpochDay())
                }
            } catch (error: Exception) {
                FocusLog.e("Scheduled start failed", error)
            } finally {
                container.alarms.scheduleNextStart(container.repository.state.value.schedules)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SCHEDULE_START = "com.focuslock.action.SCHEDULE_START"
    }
}
