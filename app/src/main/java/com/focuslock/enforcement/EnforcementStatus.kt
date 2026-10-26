package com.focuslock.enforcement

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager

/** Snapshot of everything that can weaken enforcement, shown in the setup screen. */
data class EnforcementStatus(
    val accessibilityEnabled: Boolean = false,
    val exactAlarmsAllowed: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false,
    val notificationsGranted: Boolean = false,
    val vpnConsentGranted: Boolean = false,
    val isDefaultLauncher: Boolean = false,
    val privateDnsOff: Boolean = true,
    val deviceOwner: Boolean = false,
) {
    /** Accessibility is the only strictly required permission. */
    val blockingReady: Boolean get() = accessibilityEnabled
}

class EnforcementStatusReader(private val context: Context) {

    fun read(): EnforcementStatus = EnforcementStatus(
        accessibilityEnabled = isAccessibilityServiceEnabled(),
        exactAlarmsAllowed = canScheduleExactAlarms(),
        ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
        notificationsGranted = hasNotificationPermission(),
        vpnConsentGranted = vpnConsentGranted(),
        isDefaultLauncher = isDefaultLauncher(),
        privateDnsOff = isPrivateDnsOff(),
        deviceOwner = isDeviceOwner(),
    )

    /**
     * Checks the system's list of enabled accessibility services rather than reading the
     * settings string, so a service that is enabled but crashed still counts as disabled.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabled.any { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
            serviceInfo.packageName == context.packageName &&
                serviceInfo.name == FocusAccessibilityService::class.java.name
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun vpnConsentGranted(): Boolean = try {
        VpnService.prepare(context) == null
    } catch (error: Exception) {
        false
    }

    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        return resolved == context.packageName
    }

    /**
     * Private DNS (DoT) bypasses a DNS-based filter. The mode is readable but cannot be changed
     * without device-owner privileges, so it is surfaced as a warning instead.
     */
    fun isPrivateDnsOff(): Boolean = try {
        val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
        mode == null || mode == "off"
    } catch (error: Exception) {
        true
    }

    fun isDeviceOwner(): Boolean = try {
        context.getSystemService(DevicePolicyManager::class.java)
            ?.isDeviceOwnerApp(context.packageName) == true
    } catch (error: Exception) {
        false
    }

    fun isLockTaskActive(): Boolean {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return false
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }
}
