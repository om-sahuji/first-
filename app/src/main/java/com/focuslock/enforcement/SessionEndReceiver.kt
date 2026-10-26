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

/** Closes out a session exactly at its end time so stale state and the VPN do not linger. */
class SessionEndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SESSION_END) return
        val container = (context.applicationContext as FocusLockApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                container.repository.reconcile()
                FocusLog.d("Session end alarm handled")
            } catch (error: Exception) {
                FocusLog.e("Session end handling failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SESSION_END = "com.focuslock.action.SESSION_END"
    }
}
