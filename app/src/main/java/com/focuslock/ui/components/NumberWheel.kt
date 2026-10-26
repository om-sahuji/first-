package com.focuslock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Snapping numeric wheel.
 *
 * The centred row is the selection; a light haptic tick fires only when the value changes, so
 * a fast flick stays cheap. Used for duration and for schedule times so both feel identical.
 */
@Composable
fun NumberWheel(
    values: List<Int>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: (Int) -> String = { it.toString() },
    itemHeight: Dp = 44.dp,
    visibleItems: Int = 3,
) {
    require(values.isNotEmpty()) { "NumberWheel needs at least one value" }
    val selectedIndex = values.indexOf(selected).let { if (it >= 0) it else 0 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val haptics = LocalHapticFeedback.current
    var lastReported by remember { mutableIntStateOf(values.getOrElse(selectedIndex) { values.first() }) }

    val centeredIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2 - center) }
                ?.index
        }
    }

    LaunchedEffect(centeredIndex) {
        val index = centeredIndex ?: return@LaunchedEffect
        val value = values.getOrNull(index) ?: return@LaunchedEffect
        if (value != lastReported) {
            lastReported = value
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelectedChange(value)
        }
    }

    // External changes (preset taps) move the wheel; user scrolling already matches. A
    // scroll in progress is left alone so the wheel never fights the finger.
    LaunchedEffect(selected) {
        val index = values.indexOf(selected)
        if (index >= 0 && centeredIndex != index && !listState.isScrollInProgress) {
            listState.animateScrollToItem(index)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight * visibleItems),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = (itemHeight * (visibleItems - 1)) / 2),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(count = values.size) { index ->
                val value = values[index]
                val isCentered = index == centeredIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(value),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (isCentered) 26.sp else 17.sp,
                        color = if (isCentered) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .padding(horizontal = 6.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
        )
    }
}

/** Duration wheel in five minute steps. */
@Composable
fun DurationWheel(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    minMinutes: Int = 5,
    maxMinutes: Int = 180,
    modifier: Modifier = Modifier,
) {
    val values = remember(minMinutes, maxMinutes) {
        (minMinutes..maxMinutes step 5).toList()
    }
    Column(modifier = modifier) {
        NumberWheel(
            values = values,
            selected = minutes,
            onSelectedChange = onMinutesChange,
            label = { value -> if (value >= 60) "%dh %02d".format(value / 60, value % 60) else "$value min" },
        )
    }
}
