package com.focuslock.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.core.TimeFormat
import com.focuslock.data.model.SessionRules
import com.focuslock.domain.ScheduleMath
import com.focuslock.ui.FocusViewModel
import com.focuslock.ui.PickerTarget
import com.focuslock.ui.Route
import com.focuslock.ui.components.AddTile
import com.focuslock.ui.components.AppTile
import com.focuslock.ui.components.BoxyButton
import com.focuslock.ui.components.BoxyChip
import com.focuslock.ui.components.BoxyEmphasis
import com.focuslock.ui.components.BoxyNotice
import com.focuslock.ui.components.BoxyPanel
import com.focuslock.ui.components.DurationWheel
import com.focuslock.ui.components.IconBoxButton
import com.focuslock.ui.components.ScreenHeader
import com.focuslock.ui.components.SectionLabel
import com.focuslock.ui.components.ToggleRow
import java.time.Instant
import java.time.ZoneId

@Composable
fun HomeScreen(vm: FocusViewModel) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val status by vm.enforcementStatus.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val permanent by vm.permanentApps.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    val appLabels = remember(apps) { apps.associate { it.packageName to it.label } }
    val nextStart = remember(snapshot.schedules, now) {
        ScheduleMath.nextStart(
            snapshot.schedules,
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()),
        )
    }

    com.focuslock.ui.components.AppScaffold {
        ScreenHeader(
            title = "Focus Lock",
            subtitle = "Block out a stretch of undistracted time.",
        ) {
            IconBoxButton(
                glyph = "⚙",
                onClick = { vm.navigate(Route.Setup) },
                contentDescription = "Setup and permissions",
            )
        }

        if (!status.accessibilityEnabled) {
            BoxyNotice(
                text = "Blocking is off. Enable the accessibility service to make sessions stick.",
                actionLabel = "Set up",
                onAction = { vm.navigate(Route.Setup) },
            )
        }

        BoxyPanel(title = "Duration") {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = draft.durationMinutes.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "min",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionRules.PRESETS.forEach { preset ->
                    BoxyChip(
                        label = TimeFormat.durationLabel(preset),
                        selected = draft.durationMinutes == preset,
                        onClick = { vm.setDuration(preset) },
                    )
                }
            }
            DurationWheel(
                minutes = draft.durationMinutes,
                onMinutesChange = vm::setDuration,
            )
        }

        BoxyPanel(title = "Allowed apps") {
            Text(
                text = "Phone, Messages and Camera stay available. Add up to " +
                    "${SessionRules.MAX_ADDITIONAL_APPS} more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                permanent.forEach { app ->
                    AppTile(
                        label = app.label,
                        packageName = app.packageName,
                        dimmed = !app.available,
                    )
                }
            }
            if (draft.allowedPackages.isEmpty()) {
                Text(
                    text = "No extra apps yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(draft.allowedPackages, key = { it }) { packageName ->
                        AppTile(
                            label = appLabels[packageName] ?: vm.labelFor(packageName),
                            packageName = packageName,
                            onClick = { vm.toggleAllowedApp(packageName) },
                        )
                    }
                    item {
                        AddTile(onClick = { vm.openAppPicker(PickerTarget.HOME) })
                    }
                }
            }
            if (draft.allowedPackages.size < SessionRules.MAX_ADDITIONAL_APPS) {
                BoxyButton(
                    label = "Choose apps (${draft.allowedPackages.size}/${SessionRules.MAX_ADDITIONAL_APPS})",
                    onClick = { vm.openAppPicker(PickerTarget.HOME) },
                    emphasis = BoxyEmphasis.Outline,
                )
            } else {
                Text(
                    text = "All five extra slots are used. Tap an app above to remove it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        BoxyPanel(title = "Websites") {
            ToggleRow(
                title = "Allow only these sites",
                description = "Applies to browser apps you permit, while the session runs.",
                checked = draft.websiteFilterEnabled,
                onCheckedChange = vm::setWebsiteFilterEnabled,
                enabled = draft.domains.isNotEmpty(),
            )
            if (draft.domains.isEmpty()) {
                Text(
                    text = "No sites added. Add one to use the website allowlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                draft.domains.forEach { domain ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "REMOVE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { vm.removeDomain(domain) }
                                .padding(6.dp),
                        )
                    }
                }
            }
            BoxyButton(
                label = "Manage sites",
                onClick = { vm.navigate(Route.Websites) },
                emphasis = BoxyEmphasis.Outline,
            )
            if (draft.websiteFilterEnabled && !status.vpnConsentGranted) {
                BoxyNotice(
                    text = "VPN permission is needed for website filtering. Apps are still blocked without it.",
                    tone = com.focuslock.ui.components.NoticeTone.Info,
                )
            }
        }

        BoxyPanel(title = "Schedules") {
            Text(
                text = when {
                    snapshot.schedules.isEmpty() -> "No schedules yet. Repeating focus blocks start on their own."
                    nextStart != null -> "Next: ${dayLabel(nextStart.dayOfWeek.value)} at " +
                        TimeFormat.minuteOfDayLabel(nextStart.hour * 60 + nextStart.minute)
                    else -> "${snapshot.schedules.size} schedule(s), none enabled."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            BoxyButton(
                label = if (snapshot.schedules.isEmpty()) "Create a schedule" else "Manage schedules",
                onClick = { vm.navigate(Route.Schedules) },
                emphasis = BoxyEmphasis.Outline,
            )
        }

        Spacer(Modifier.height(4.dp))
        BoxyButton(
            label = "Start focus · ${draft.durationMinutes} min",
            subtitle = "Ends ${endLabel(draft.durationMinutes, now)}",
            onClick = vm::startSession,
        )
        SectionLabel(
            text = "Cat's LIFE: one emergency exit per calendar day.",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun dayLabel(isoDay: Int): String = when (isoDay) {
    1 -> "Mon"
    2 -> "Tue"
    3 -> "Wed"
    4 -> "Thu"
    5 -> "Fri"
    6 -> "Sat"
    else -> "Sun"
}

private fun endLabel(durationMinutes: Int, now: Long): String =
    "at " + TimeFormat.clockTime(now + durationMinutes * 60_000L)
