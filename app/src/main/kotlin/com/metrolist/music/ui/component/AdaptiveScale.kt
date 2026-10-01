/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import com.metrolist.music.constants.AdaptiveScaleMaxKey
import com.metrolist.music.constants.MiniPlayerHeight
import com.metrolist.music.utils.rememberPreference

/**
 * Baseline short edge for a phone in dp. A 360dp-short-edge phone reports 1x;
 * a 720dp short edge reports 2x, and larger displays scale up to 3x.
 */
const val ADAPTIVE_SCALE_BASELINE_DP = 360f

/** Max growth for full-screen photo frame text and icons. */
const val FRAME_UI_SCALE_MAX = 3f

/** Scale steps offered by the settings slider, derived from the 1x..3x range. */
const val UI_SCALE_SLIDER_STEPS = 3

fun uiScaleLabel(value: Float): String =
    if (value % 1f == 0f) "${value.toInt()}x" else "${value}x"

fun adaptiveUiScale(shortestDp: Float, maxScale: Float = FRAME_UI_SCALE_MAX): Float =
    (shortestDp / ADAPTIVE_SCALE_BASELINE_DP).coerceIn(1f, maxScale.coerceAtLeast(1f))

/** User-chosen upper bound for auto scaling, read from settings. */
@Composable
fun rememberAdaptiveScaleMax(): Float {
    val maxScale by rememberPreference(AdaptiveScaleMaxKey, FRAME_UI_SCALE_MAX)
    return remember(maxScale) { maxScale.coerceIn(1f, FRAME_UI_SCALE_MAX) }
}

/**
 * Adaptive scale for full-screen overlays. Call inside BoxWithConstraints so the
 * real layout size (not the configuration bucket) drives clock, song and lyric sizes.
 */
@Composable
fun BoxWithConstraintsScope.rememberFrameUiScale(): Float {
    val width = maxWidth.value
    val height = maxHeight.value
    val maxScale = rememberAdaptiveScaleMax()
    return remember(width, height, maxScale) {
        adaptiveUiScale(minOf(width, height), maxScale)
    }
}

/**
 * Adaptive scale that works without BoxWithConstraints (e.g. lyric overlay).
 * Falls back to the configuration bucket when the window size is unavailable.
 */
@Composable
fun rememberAdaptiveUiScale(): Float {
    val configuration = LocalConfiguration.current
    val maxScale = rememberAdaptiveScaleMax()
    return remember(configuration.screenWidthDp, configuration.screenHeightDp, maxScale) {
        val shortest = minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()
        adaptiveUiScale(shortest, maxScale)
    }
}

data class MiniPlayerScales(
    /** Multiply every dp/sp inside the mini player by this. */
    val sizeScale: Float,
    /** Also grow the tablet width cap so scaled controls retain their layout space. */
    val adaptiveScale: Float,
    /** Height already including the extra growth. */
    val height: Dp,
)

/**
 * Mini player scales from the upstream 64.dp layout, so landscape and portrait share one
 * size and only the screen's short edge decides the multiplier.
 */
@Composable
fun rememberMiniPlayerScales(baseHeight: Dp = MiniPlayerHeight): MiniPlayerScales {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    // Observe configuration so rotation recomposes even if containerSize lags.
    val configuration = LocalConfiguration.current
    val container = windowInfo.containerSize
    val maxScale = rememberAdaptiveScaleMax()
    return remember(container, density.density, configuration.orientation, baseHeight, maxScale) {
        val densityValue = density.density.takeIf { it > 0f } ?: 1f
        val widthDp = if (container.width > 0) container.width / densityValue
        else configuration.screenWidthDp.toFloat()
        val heightDp = if (container.height > 0) container.height / densityValue
        else configuration.screenHeightDp.toFloat()
        val shortest = minOf(widthDp, heightDp).takeIf { it > 0f }
            ?: ADAPTIVE_SCALE_BASELINE_DP
        val scale = adaptiveUiScale(shortest, maxScale)
        MiniPlayerScales(
            sizeScale = scale,
            adaptiveScale = scale,
            height = baseHeight * scale,
        )
    }
}
