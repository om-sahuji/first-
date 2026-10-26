package com.focuslock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focuslock.ui.MessageTone
import com.focuslock.ui.UiMessage
import kotlinx.coroutines.delay

/** Inline status message. Auto-dismisses so it never becomes another thing to manage. */
@Composable
fun MessageBar(
    message: UiMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    LaunchedEffect(message.id) {
        delay(5_000)
        onDismiss()
    }
    val accent = when (message.tone) {
        MessageTone.Warning -> MaterialTheme.colorScheme.error
        MessageTone.Info -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "×",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 6.dp),
            )
        }
    }
}
