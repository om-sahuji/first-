package com.focuslock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.core.TimeFormat
import com.focuslock.data.model.Schedule
import com.focuslock.data.model.SessionRules
import com.focuslock.ui.FocusViewModel
import com.focuslock.ui.Route
import com.focuslock.ui.components.AppScaffold
import com.focuslock.ui.components.BoxyButton
import com.focuslock.ui.components.BoxyChip
import com.focuslock.ui.components.BoxyEmphasis
import com.focuslock.ui.components.BoxyNotice
import com.focuslock.ui.components.BoxyPanel
import com.focuslock.ui.components.EmptyHint
import com.focuslock.ui.components.IconBoxButton
import com.focuslock.ui.components.NoticeTone
import com.focuslock.ui.components.ScreenHeader

@Composable
fun SchedulesScreen(vm: FocusViewModel) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Schedule?>(null) }

    AppScaffold {
        ScreenHeader(
            title = "Schedules",
            subtitle = "Repeating focus blocks",
            onBack = { vm.navigate(Route.Home) },
        ) {
            IconBoxButton(
                glyph = "+",
                onClick = { vm.openScheduleEditor(null) },
                contentDescription = "New schedule",
            )
        }

        if (snapshot.schedules.isEmpty()) {
            EmptyHint(
                "No schedules yet. A schedule starts a session automatically on the days and " +
                    "time you choose. Tap + to create one.",
            )
        }

        snapshot.schedules.forEach { schedule ->
            ScheduleCard(
                schedule = schedule,
                onEdit = { vm.openScheduleEditor(schedule.id) },
                onToggle = { enabled -> vm.setScheduleEnabled(schedule.id, enabled) },
                onDelete = { pendingDelete = schedule },
            )
        }

        BoxyNotice(
            text = "Android starts these in the background when it can. Exempting Focus Lock " +
                "from battery optimisation keeps starts close to the minute.",
            tone = NoticeTone.Info,
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete schedule?") },
            text = { Text("This removes the repeating block. A session that is already running is not affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        vm.deleteSchedule(toDelete.id)
                    },
                ) {
                    Text("DELETE", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("CANCEL") }
            },
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    BoxyPanel(title = schedule.label.ifBlank { "Focus block" }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = TimeFormat.minuteOfDayLabel(schedule.startMinuteOfDay),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 10.dp),
            )
            Text(
                text = TimeFormat.durationLabel(schedule.durationMinutes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Switch(checked = schedule.enabled, onCheckedChange = onToggle)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).forEach { day ->
                DayBadge(label = DAY_LETTERS[day - 1], active = day in schedule.days)
            }
        }

        Text(
            text = buildString {
                append("${schedule.allowedPackages.size} extra app")
                if (schedule.allowedPackages.size != 1) append("s")
                if (schedule.websiteFilterEnabled) {
                    append(" · ${schedule.allowedDomains.size} site")
                    if (schedule.allowedDomains.size != 1) append("s")
                }
                append(" · up to ${SessionRules.MAX_ADDITIONAL_APPS} allowed")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxyChip(label = "Edit", onClick = onEdit)
            BoxyChip(label = "Delete", onClick = onDelete)
            Spacer(Modifier.weight(1f))
            BoxyButton(
                label = "Open",
                onClick = onEdit,
                emphasis = BoxyEmphasis.Quiet,
                modifier = Modifier.width(96.dp),
            )
        }
    }
}

@Composable
private fun DayBadge(label: String, active: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 28.dp)
            .background(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.extraSmall,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
