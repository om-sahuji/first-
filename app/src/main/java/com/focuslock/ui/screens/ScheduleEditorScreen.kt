package com.focuslock.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.data.model.SessionRules
import com.focuslock.ui.FocusViewModel
import com.focuslock.ui.PickerTarget
import com.focuslock.ui.Route
import com.focuslock.ui.components.AppScaffold
import com.focuslock.ui.components.AppTile
import com.focuslock.ui.components.BoxyButton
import com.focuslock.ui.components.BoxyChip
import com.focuslock.ui.components.BoxyEmphasis
import com.focuslock.ui.components.BoxyNotice
import com.focuslock.ui.components.BoxyPanel
import com.focuslock.ui.components.DurationWheel
import com.focuslock.ui.components.NumberWheel
import com.focuslock.ui.components.ScreenHeader
import com.focuslock.ui.components.ToggleRow
import com.focuslock.ui.components.NoticeTone

@Composable
fun ScheduleEditorScreen(vm: FocusViewModel) {
    val draft by vm.scheduleDraft.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    var domainInput by remember { mutableStateOf("") }

    val current = draft
    if (current == null) {
        // The draft is transient; if the process was recreated there is nothing to edit.
        LaunchedEffect(Unit) { vm.navigate(Route.Schedules) }
        return
    }

    val labels = remember(apps) { apps.associate { it.packageName to it.label } }

    AppScaffold {
        ScreenHeader(
            title = if (current.isNew) "New schedule" else "Edit schedule",
            subtitle = "Same rules as a manual session",
            onBack = { vm.navigate(Route.Schedules) },
        )

        BoxyPanel(title = "Name") {
            OutlinedTextField(
                value = current.label,
                onValueChange = { value -> vm.updateScheduleDraft { it.copy(label = value) } },
                singleLine = true,
                placeholder = { Text("Morning study (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        BoxyPanel(title = "Days") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (1..7).forEach { day ->
                    BoxyChip(
                        label = DAY_NAMES[day - 1],
                        selected = day in current.days,
                        onClick = {
                            vm.updateScheduleDraft { value ->
                                value.copy(
                                    days = if (day in value.days) value.days - day else value.days + day,
                                )
                            }
                        },
                    )
                }
            }
        }

        BoxyPanel(title = "Start time") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberWheel(
                    values = (0..23).toList(),
                    selected = current.startMinuteOfDay / 60,
                    onSelectedChange = { hour ->
                        vm.updateScheduleDraft { it.copy(startMinuteOfDay = hour * 60 + it.startMinuteOfDay % 60) }
                    },
                    label = { "%02d".format(it) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NumberWheel(
                    values = (0..55 step 5).toList(),
                    selected = current.startMinuteOfDay % 60 - (current.startMinuteOfDay % 60) % 5,
                    onSelectedChange = { minute ->
                        vm.updateScheduleDraft { it.copy(startMinuteOfDay = it.startMinuteOfDay / 60 * 60 + minute) }
                    },
                    label = { "%02d".format(it) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "Starts at ${com.focuslock.core.TimeFormat.minuteOfDayLabel(current.startMinuteOfDay)} " +
                    "in this device's time zone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BoxyPanel(title = "Duration") {
            DurationWheel(
                minutes = current.durationMinutes,
                onMinutesChange = { minutes ->
                    vm.updateScheduleDraft { it.copy(durationMinutes = minutes) }
                },
            )
        }

        BoxyPanel(title = "Allowed apps") {
            Text(
                text = "Phone, Messages and Camera are always available. Up to " +
                    "${SessionRules.MAX_ADDITIONAL_APPS} more for this schedule.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (current.allowedPackages.isEmpty()) {
                Text(
                    text = "No extra apps chosen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    current.allowedPackages.forEach { packageName ->
                        AppTile(
                            label = labels[packageName] ?: vm.labelFor(packageName),
                            packageName = packageName,
                            onClick = { vm.toggleScheduleApp(packageName) },
                        )
                    }
                }
            }
            BoxyButton(
                label = "Choose apps (${current.allowedPackages.size}/${SessionRules.MAX_ADDITIONAL_APPS})",
                onClick = { vm.openAppPicker(PickerTarget.SCHEDULE) },
                emphasis = BoxyEmphasis.Outline,
            )
        }

        BoxyPanel(title = "Websites") {
            ToggleRow(
                title = "Allow only these sites",
                description = "Applies to permitted browsers for this schedule.",
                checked = current.websiteFilterEnabled,
                onCheckedChange = vm::setScheduleWebsiteFilter,
                enabled = current.domains.isNotEmpty(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    singleLine = true,
                    placeholder = { Text("rollcall.iitd.ac.in") },
                    modifier = Modifier.weight(1f),
                )
                BoxyChip(
                    label = "Add",
                    onClick = { if (vm.addScheduleDomain(domainInput)) domainInput = "" },
                )
            }
            current.domains.forEach { domain ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(domain, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        text = "REMOVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { vm.removeScheduleDomain(domain) }
                            .padding(6.dp),
                    )
                }
            }
        }

        BoxyButton(label = "Save schedule", onClick = vm::saveSchedule)
        if (!current.isNew) {
            BoxyButton(
                label = "Delete schedule",
                onClick = { vm.deleteSchedule(current.id) },
                emphasis = BoxyEmphasis.Outline,
            )
        }
        BoxyNotice(
            text = "Schedules never change a session that is already running.",
            tone = NoticeTone.Info,
        )
    }
}

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
