package com.focuslock.enforcement

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.focuslock.admin.FocusDeviceAdminReceiver
import com.focuslock.core.FocusLog
import com.focuslock.data.model.ActiveSession

/**
 * Optional strict mode.
 *
 * Ordinary mode relies on the accessibility service, which is the strongest enforcement a
 * normal installed app can use. True kiosk (lock task) enforcement requires the app to be
 * provisioned as device owner, which cannot happen silently and needs a one-time ADB command.
 *
 * Nothing here runs unless the phone is already device owner: without that, every call is a
 * no-op and the app simply behaves as ordinary enforcement.
 */
class KioskManager(private val context: Context) {

    private val devicePolicyManager: DevicePolicyManager? =
        context.getSystemService(DevicePolicyManager::class.java)

    private val adminComponent = ComponentName(context, FocusDeviceAdminReceiver::class.java)

    val isDeviceOwner: Boolean
        get() = devicePolicyManager?.isDeviceOwnerApp(context.packageName) == true

    val isAdminActive: Boolean
        get() = devicePolicyManager?.isAdminActive(adminComponent) == true

    /**
     * Whitelists the session's permitted apps so lock task mode lets them launch. Only valid
     * for a device owner; silently ignored otherwise.
     */
    fun applyLockTaskPackages(session: ActiveSession) {
        val dpm = devicePolicyManager ?: return
        if (!isDeviceOwner) return
        val packages = (listOf(context.packageName) + session.allowedPackages).toTypedArray()
        try {
            dpm.setLockTaskPackages(adminComponent, packages)
        } catch (error: Exception) {
            FocusLog.w("Could not set lock task packages", error)
        }
    }

    /** Removes the whitelist; the app returns to normal enforcement. */
    fun clearLockTaskPackages() {
        val dpm = devicePolicyManager ?: return
        if (!isDeviceOwner) return
        try {
            dpm.setLockTaskPackages(adminComponent, emptyArray())
        } catch (error: Exception) {
            FocusLog.w("Could not clear lock task packages", error)
        }
    }
}
