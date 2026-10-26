package com.focuslock.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.FocusLockApp
import com.focuslock.core.FocusLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Rebuilds enforcement after a reboot or an app update.
 *
 * An active session is restored from disk: if its end time is still in the future it keeps
 * running, otherwise it is closed. Schedule alarms and the website filter are re-armed because
 * Android drops both across reboots.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_TIME_CHANGED,
            ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }
        val container = (context.applicationContext as FocusLockApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                container.repository.reconcile()
                container.alarms.scheduleNextStart(container.repository.state.value.schedules)
                container.coordinator.syncEnforcementWithSession()
                val session = container.repository.activeSession()
                FocusLog.d(
                    "Recovered after ${intent.action}: " +
                        if (session != null) "session active until ${session.endAtMillis}" else "no active session",
                )
            } catch (error: Exception) {
                FocusLog.e("Recovery failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TIME_CHANGED = "android.intent.action.TIME_SET"
        const val ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED"
    }
}
