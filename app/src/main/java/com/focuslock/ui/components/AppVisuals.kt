package com.focuslock.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

private object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(64)

    fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(96, 96)
                .asImageBitmap()
        }.onSuccess { cache.put(packageName, it) }.getOrNull()
    }

}

/** App icon with a process-wide cache so lists do not hit the package manager while scrolling. */
@Composable
fun rememberAppIcon(packageName: String?, size: Dp = 44.dp): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName, size) {
        packageName?.let { AppIconCache.load(context, it) }
    }
}

@Composable
fun AppIcon(
    packageName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val icon = rememberAppIcon(packageName, size)
    val shape = RoundedCornerShape(8.dp)
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape),
        )
    }
}

/** Compact tappable app tile used in the Focus grid and the selection strips. */
@Composable
fun AppTile(
    label: String,
    packageName: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    dimmed: Boolean = false,
    tileSize: Dp = 44.dp,
) {
    Column(
        modifier = modifier
            .width(68.dp)
            .alpha(if (dimmed) 0.45f else 1f)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AppIcon(packageName = packageName, size = tileSize)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AddTile(
    label: String = "Add",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tileSize: Dp = 44.dp,
) {
    Column(
        modifier = modifier
            .width(68.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(tileSize)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Segmented progress: elapsed focus time as filled blocks, not a decorative gradient. */
@Composable
fun SegmentedProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    segments: Int = 24,
) {
    val filled = (fraction.coerceIn(0f, 1f) * segments).roundToInt()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(segments) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .background(
                        if (index < filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.small,
            )
            .padding(16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun KeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}
