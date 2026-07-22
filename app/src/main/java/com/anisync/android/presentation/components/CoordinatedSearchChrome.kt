package com.anisync.android.presentation.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Material 3 Expressive search app bars need a stable 72 dp measurement for their internal
 * input-field baseline and touch target. App density may compact the surrounding chrome, but must
 * never force the search component below this safe height.
 */
internal val MinimumSearchAppBarHeight: Dp = 72.dp

internal fun safeSearchAppBarHeight(configuredHeight: Dp): Dp =
    maxOf(configuredHeight, MinimumSearchAppBarHeight)

internal fun coordinatedSearchChromeOffset(
    heightOffset: Float,
    maximumCollapsePx: Int,
): Int = heightOffset
    .roundToInt()
    .coerceIn(-maximumCollapsePx.coerceAtLeast(0), 0)

internal fun coordinatedSearchChromeHeight(
    measuredHeight: Int,
    coordinatedOffsetPx: Int,
): Int = (measuredHeight + coordinatedOffsetPx).coerceAtLeast(0)
