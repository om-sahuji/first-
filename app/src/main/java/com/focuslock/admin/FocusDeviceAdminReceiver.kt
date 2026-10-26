package com.focuslock.admin

import android.app.admin.DeviceAdminReceiver
import com.focuslock.core.FocusLog

/**
 * Administrator component needed for lock task (strict) mode.
 *
 * This receiver is inert until the user completes device-owner provisioning with ADB; Focus
 * Lock never enables device administration silently. See docs/SETUP.md for the exact steps and
 * the recovery command.
 */
class FocusDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: android.content.Context, intent: android.content.Intent) {
        FocusLog.d("Device admin enabled")
    }

    override fun onDisabled(context: android.content.Context, intent: android.content.Intent) {
        FocusLog.d("Device admin disabled")
    }
}
