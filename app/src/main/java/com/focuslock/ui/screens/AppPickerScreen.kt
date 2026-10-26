package com.focuslock.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.data.apps.AppEntry
import com.focuslock.data.apps.PermanentApp
import com.focuslock.data.model.SessionRules
import com.focuslock.ui.FocusViewModel
import com.focuslock.ui.PickerTarget
import com.focuslock.ui.Route
import com.focuslock.ui.components.AppIcon
import com.focuslock.ui.components.SectionLabel
import com.focuslock.ui.components.ScreenHeader

/** Selects the additional apps for a manual session or a schedule. */
@Composable
fun AppPickerScreen(vm: FocusViewModel) {
    val target by vm.pickerTarget.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val scheduleDraft by vm.scheduleDraft.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val permanent by vm.permanentApps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val selected = if (target == PickerTarget.HOME) {
        draft.allowedPackages
    } else {
        scheduleDraft?.allowedPackages.orEmpty()
    }
    val onToggle: (String) -> Unit = if (target == PickerTarget.HOME) {
        vm::toggleAllowedApp
    } else {
        vm::toggleScheduleApp
    }
    val goBack: () -> Unit = {
        if (target == PickerTarget.HOME) {
            vm.navigate(Route.Home)
        } else {
            val id = scheduleDraft?.takeIf { !it.isNew }?.id
            vm.navigate(Route.ScheduleEditor(id))
        }
    }
    val filtered = remember(apps, query) {
        val needle = query.trim()
        if (needle.isEmpty()) apps else apps.filter { it.label.contains(needle, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenHeader(
                title = "Allowed apps",
                subtitle = "${selected.size} of ${SessionRules.MAX_ADDITIONAL_APPS} extra apps selected",
                onBack = goBack,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search installed apps") },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                SectionLabel("Always available", modifier = Modifier.padding(vertical = 8.dp))
            }
            items(permanent, key = { "permanent-${it.role.name}" }) { app ->
                PermanentAppRow(app)
            }
            item {
                SectionLabel("Your apps", modifier = Modifier.padding(vertical = 8.dp))
            }
            items(filtered, key = { it.packageName }) { app ->
                val isSelected = app.packageName in selected
                AppSelectRow(
                    app = app,
                    selected = isSelected,
                    enabled = isSelected || selected.size < SessionRules.MAX_ADDITIONAL_APPS,
                    onClick = { onToggle(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun PermanentAppRow(app: PermanentApp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(packageName = app.packageName, size = 34.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (app.available) "Always reachable" else "Not found on this device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "LOCKED IN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AppSelectRow(
    app: AppEntry,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(packageName = app.packageName, size = 34.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SelectionBox(selected = selected, enabled = enabled)
        }
    }
}

@Composable
private fun SelectionBox(selected: Boolean, enabled: Boolean) {
    val accent = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(1.dp, accent, MaterialTheme.shapes.extraSmall)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.extraSmall,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
