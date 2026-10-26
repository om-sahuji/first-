package com.focuslock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.core.TimeFormat
import com.focuslock.data.model.ActiveSession
import com.focuslock.ui.FocusViewModel
import com.focuslock.ui.components.AppTile
import com.focuslock.ui.components.BoxyButton
import com.focuslock.ui.components.BoxyEmphasis
import com.focuslock.ui.components.BoxyNotice
import com.focuslock.ui.components.NoticeTone
import com.focuslock.ui.components.SectionLabel
import com.focuslock.ui.components.SegmentedProgress

/**
 * The session environment.
 *
 * Intentionally has no configuration affordances: permitted apps, the countdown and Cat's LIFE
 * are the only actions. Everything else is reachable only when no session is running.
 */
@Composable
fun FocusScreen(
    vm: FocusViewModel,
    session: ActiveSession,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val now by vm.now.collectAsStateWithLifecycle()
    val permanent by vm.permanentApps.collectAsStateWithLifecycle()
    val status by vm.enforcementStatus.collectAsStateWithLifecycle()
    val blockedLabel by vm.blockedLabel.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()

    val remaining = session.remainingMillis(now)
    val elapsedFraction = if (session.durationMillis > 0) {
        1f - (remaining.toFloat() / session.durationMillis.toFloat())
    } else {
        1f
    }
    val escapeAvailable = vm.escapeAvailable()
    var showEscapeDialog by remember { mutableStateOf(false) }

    val labels = remember(apps) { apps.associate { it.packageName to it.label } }
    val permitted = remember(permanent, session.allowedPackages, labels) {
        buildList {
            permanent.filter { it.available }.forEach { app ->
                add(app.label to app.packageName)
            }
            session.allowedPackages.forEach { packageName ->
                add((labels[packageName] ?: vm.labelFor(packageName)) to packageName)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = "FOCUS LOCK",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = TimeFormat.remaining(remaining),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                SegmentedProgress(fraction = elapsedFraction)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ENDS ${vm.endTimeLabel(session.endAtMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (session.websiteFilterEnabled) {
                        Text(
                            text = "· ${session.allowedDomains.size} SITE" +
                                if (session.allowedDomains.size == 1) "" else "S",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (blockedLabel != null) {
                    Text(
                        text = "$blockedLabel blocked",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!status.accessibilityEnabled) {
                    BoxyNotice(
                        text = "Enforcement is off: the accessibility service was disabled.",
                        actionLabel = "Fix",
                        onAction = onOpenAccessibilitySettings,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Permitted now")
                permitted.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { (label, packageName) ->
                            AppTile(
                                label = label,
                                packageName = packageName,
                                onClick = { packageName?.let(vm::launchApp) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxyButton(
                    label = "Cat's Life",
                    subtitle = if (escapeAvailable) {
                        "One emergency exit per calendar day"
                    } else {
                        "Already used today · available tomorrow"
                    },
                    enabled = escapeAvailable,
                    emphasis = BoxyEmphasis.Outline,
                    onClick = { showEscapeDialog = true },
                )
                SectionLabel(
                    text = "Focus Lock stays on until the timer ends.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showEscapeDialog) {
        AlertDialog(
            onDismissRequest = { showEscapeDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Use Cat's Life?") },
            text = {
                Text(
                    "This ends the session right now and spends your single escape for today. " +
                        "You will not be able to end another session early until tomorrow, and " +
                        "this session cannot be resumed.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEscapeDialog = false
                        vm.useEscape()
                    },
                ) {
                    Text("USE ESCAPE", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEscapeDialog = false }) {
                    Text("KEEP FOCUSING")
                }
            },
        )
    }
}
