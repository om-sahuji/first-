package com.focuslock.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.BuildConfig
import com.focuslock.core.TimeFormat
import com.focuslock.domain.ScheduleMath
import com.focuslock.ui.FocusViewModel
import com.focuslock.ui.Route
import com.focuslock.ui.SystemSettings
import com.focuslock.ui.components.AppScaffold
import com.focuslock.ui.components.BoxyButton
import com.focuslock.ui.components.BoxyChip
import com.focuslock.ui.components.BoxyEmphasis
import com.focuslock.ui.components.BoxyNotice
import com.focuslock.ui.components.BoxyPanel
import com.focuslock.ui.components.KeyValueRow
import com.focuslock.ui.components.NoticeTone
import com.focuslock.ui.components.ScreenHeader
import com.focuslock.ui.components.ToggleRow
import java.time.Instant
import java.time.ZoneId

@Composable
fun SetupScreen(
    vm: FocusViewModel,
    onRequestVpnConsent: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val status by vm.enforcementStatus.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val session = snapshot.session

    AppScaffold {
        ScreenHeader(
            title = "Setup",
            subtitle = "Permissions, reliability, strict mode",
            onBack = { vm.navigate(if (session != null) Route.Focus else Route.Home) },
        )

        BoxyPanel(title = "Required") {
            PermissionRow(
                title = "Accessibility service",
                granted = status.accessibilityEnabled,
                detail = "Observes which app is in front so blocked apps can be replaced by the " +
                    "Focus screen. It never reads screen content.",
                actionLabel = if (status.accessibilityEnabled) "OPEN SETTINGS" else "ENABLE",
                onAction = { SystemSettings.accessibility(context) },
            )
            if (!status.accessibilityEnabled) {
                BoxyNotice(
                    text = "Without this service, sessions run but nothing is blocked.",
                )
            }
        }

        BoxyPanel(title = "Reliability") {
            PermissionRow(
                title = "Ignore battery optimisation",
                granted = status.ignoringBatteryOptimizations,
                detail = "Keeps schedule alarms and enforcement alive when the phone idles.",
                actionLabel = "OPEN",
                onAction = { SystemSettings.batteryOptimization(context) },
            )
            PermissionRow(
                title = "Exact alarms",
                granted = status.exactAlarmsAllowed,
                detail = "Schedules start on time instead of when Android wakes up.",
                actionLabel = "ALLOW",
                onAction = { SystemSettings.exactAlarms(context) },
            )
            PermissionRow(
                title = "Notifications",
                granted = status.notificationsGranted,
                detail = "Only used for the website filter status notification.",
                actionLabel = if (status.notificationsGranted) "MANAGE" else "ALLOW",
                onAction = {
                    if (status.notificationsGranted) {
                        SystemSettings.notificationSettings(context)
                    } else {
                        onRequestNotifications()
                    }
                },
            )
        }

        BoxyPanel(title = "Website filtering") {
            PermissionRow(
                title = "VPN permission",
                granted = status.vpnConsentGranted,
                detail = "Lets Focus Lock answer browser DNS lookups locally while a session runs.",
                actionLabel = if (status.vpnConsentGranted) "GRANTED" else "GRANT",
                onAction = { if (!status.vpnConsentGranted) onRequestVpnConsent() },
            )
            if (!status.privateDnsOff) {
                BoxyNotice(
                    text = "Private DNS is enabled and bypasses DNS filtering. Set it to Off in " +
                        "Network & internet → Private DNS.",
                )
            }
            BoxyNotice(
                text = "Only one VPN can be active. Using another VPN disables website filtering, " +
                    "not app blocking.",
                tone = NoticeTone.Info,
            )
        }

        BoxyPanel(title = "Home screen") {
            Text(
                text = "During a session, launch screens (including your normal home app) are " +
                    "treated as blocking targets, so Home returns to the Focus screen. Setting " +
                    "Focus Lock as your home app is optional but makes that instant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionRow(
                title = "Focus Lock is the home app",
                granted = status.isDefaultLauncher,
                detail = "Choose Focus Lock in the system home-app picker if you want a " +
                    "purpose-built focus home screen.",
                actionLabel = "CHOOSE",
                onAction = { SystemSettings.homeSettings(context) },
            )
        }

        BoxyPanel(title = "Strict mode (device owner)") {
            Text(
                text = "Ordinary mode is what you have now: the accessibility service redirects " +
                    "blocked apps and the launcher. Android does not let a normal app prevent app " +
                    "switching entirely.\n\nStrict mode adds real lock-task enforcement, which " +
                    "requires provisioning Focus Lock as device owner once, over ADB, on a phone " +
                    "without accounts or work profiles.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(
                    text = "adb shell dpm set-device-owner \\\n  com.focuslock/.admin.FocusDeviceAdminReceiver",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            KeyValueRow(
                key = "Device owner",
                value = if (status.deviceOwner) "Provisioned" else "Not provisioned",
            )
            ToggleRow(
                title = "Use lock task during sessions",
                description = if (status.deviceOwner) {
                    "Blocks Home, Recents and app switching while a session runs."
                } else {
                    "Unavailable until the device-owner command above succeeds."
                },
                checked = snapshot.settings.strictModeEnabled,
                onCheckedChange = vm::setStrictModeEnabled,
                enabled = status.deviceOwner,
            )
            BoxyNotice(
                text = "Recovery: end the session with Cat's LIFE, or run " +
                    "`adb shell dpm remove-active-admin com.focuslock/.admin.FocusDeviceAdminReceiver`.",
                tone = NoticeTone.Info,
            )
        }

        if (BuildConfig.DEBUG) {
            BoxyPanel(title = "Diagnostics (debug builds)") {
                KeyValueRow(
                    key = "Session",
                    value = session?.let { "active" } ?: "none",
                )
                KeyValueRow(
                    key = "Ends",
                    value = session?.let { TimeFormat.clockTime(it.endAtMillis) } ?: "—",
                )
                KeyValueRow(
                    key = "Web allowlist",
                    value = if (session?.websiteFilterEnabled == true) {
                        "${session.allowedDomains.size} domain(s)"
                    } else {
                        "off"
                    },
                )
                KeyValueRow(
                    key = "Next schedule",
                    value = ScheduleMath.nextStart(
                        snapshot.schedules,
                        Instant.now().atZone(ZoneId.systemDefault()),
                    )?.let { "${it.dayOfWeek} ${it.hour}:%02d".format(it.minute) } ?: "none",
                )
                KeyValueRow(key = "Lock task", value = if (status.deviceOwner) "available" else "unavailable")
                KeyValueRow(key = "Version", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text(
                    text = "Redirect latency is logged under the FocusLock logcat tag in debug builds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        BoxyPanel(title = "Privacy") {
            Text(
                text = "No account, no analytics, no network calls of our own. The app lists " +
                    "package names to know what to block; it never reads message, call or " +
                    "browsing content. Diagnostics are written only to logcat in debug builds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BoxyButton(
            label = "Back",
            onClick = { vm.navigate(if (session != null) Route.Focus else Route.Home) },
            emphasis = BoxyEmphasis.Quiet,
        )
        BoxyChip(
            label = "Build ${BuildConfig.VERSION_NAME} · API ${Build.VERSION.SDK_INT}",
            onClick = { vm.markSetupCompleted() },
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .background(
                    if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BoxyChip(label = actionLabel, onClick = onAction)
    }
}
