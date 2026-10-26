package com.focuslock.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.focuslock.core.FocusLog

/** Deep links into the system screens a user must visit to grant enforcement permissions. */
object SystemSettings {

    fun accessibility(context: Context) = launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun appDetails(context: Context) = launch(
        context,
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )

    fun batteryOptimization(context: Context) = launch(
        context,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    fun exactAlarms(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }
        launch(context, intent)
    }

    fun notificationSettings(context: Context) = launch(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    fun homeSettings(context: Context) = launch(context, Intent(Settings.ACTION_HOME_SETTINGS))

    private fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: Exception) {
            FocusLog.w("Could not open ${intent.action}", error)
        }
    }
}
