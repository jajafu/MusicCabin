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
import com.metrolist.music.constants.FixedUiScaleKey
import com.metrolist.music.constants.UiScaleMode
import com.metrolist.music.constants.UiScaleModeKey
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference

/**
 * Baseline short edge for a phone in dp. A 360dp-short-edge phone reports 1x;
 * a 720dp short edge reports 2x, and larger displays scale up to 3x.
 */
const val ADAPTIVE_SCALE_BASELINE_DP = 360f

/** Max growth for full-screen photo frame text and icons. */
const val FRAME_UI_SCALE_MAX = 3f

/** Max adaptive growth for the mini player, on top of its existing orientation baseline. */
const val MINI_PLAYER_EXTRA_SCALE_MAX = 3f

/** Default fixed scale offered in settings; matches a 720dp-short-edge head unit. */
const val FIXED_UI_SCALE_DEFAULT = 2f

/** Scale steps offered by the settings sliders, derived from the 1x..3x range. */
const val UI_SCALE_SLIDER_STEPS = 3

fun uiScaleLabel(value: Float): String =
    if (value % 1f == 0f) "${value.toInt()}x" else "${value}x"

fun adaptiveUiScale(shortestDp: Float, maxScale: Float = FRAME_UI_SCALE_MAX): Float =
    (shortestDp / ADAPTIVE_SCALE_BASELINE_DP).coerceIn(1f, maxScale.coerceAtLeast(1f))

fun miniPlayerExtraScale(shortestDp: Float, maxScale: Float = MINI_PLAYER_EXTRA_SCALE_MAX): Float =
    (shortestDp / ADAPTIVE_SCALE_BASELINE_DP).coerceIn(1f, maxScale.coerceAtLeast(1f))

/** User-chosen scaling behavior read from settings. */
data class AdaptiveScaleConfig(
    val mode: UiScaleMode,
    /** Upper bound for auto scaling. */
    val maxScale: Float,
    /** Locked scale used when [mode] is FIXED. */
    val fixedScale: Float,
)

@Composable
fun rememberAdaptiveScaleConfig(): AdaptiveScaleConfig {
    val mode by rememberEnumPreference(UiScaleModeKey, UiScaleMode.AUTO)
    val maxScale by rememberPreference(AdaptiveScaleMaxKey, FRAME_UI_SCALE_MAX)
    val fixedScale by rememberPreference(FixedUiScaleKey, FIXED_UI_SCALE_DEFAULT)
    return remember(mode, maxScale, fixedScale) {
        AdaptiveScaleConfig(
            mode = mode,
            maxScale = maxScale.coerceIn(1f, FRAME_UI_SCALE_MAX),
            fixedScale = fixedScale.coerceIn(1f, FRAME_UI_SCALE_MAX),
        )
    }
}

/** Resolve the final multiplier: fixed lock wins, otherwise grow with the screen. */
fun resolveUiScale(shortestDp: Float, config: AdaptiveScaleConfig): Float =
    if (config.mode == UiScaleMode.FIXED) config.fixedScale
    else adaptiveUiScale(shortestDp, config.maxScale)

/**
 * Adaptive scale for full-screen overlays. Call inside BoxWithConstraints so the
 * real layout size (not the configuration bucket) drives clock, song and lyric sizes.
 */
@Composable
fun BoxWithConstraintsScope.rememberFrameUiScale(): Float {
    val width = maxWidth.value
    val height = maxHeight.value
    val config = rememberAdaptiveScaleConfig()
    return remember(width, height, config) {
        resolveUiScale(minOf(width, height), config)
    }
}

/**
 * Adaptive scale that works without BoxWithConstraints (e.g. lyric overlay).
 * Falls back to the configuration bucket when the window size is unavailable.
 */
@Composable
fun rememberAdaptiveUiScale(): Float {
    val configuration = LocalConfiguration.current
    val config = rememberAdaptiveScaleConfig()
    return remember(configuration.screenWidthDp, configuration.screenHeightDp, config) {
        val shortest = minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()
        resolveUiScale(shortest, config)
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

@Composable
fun rememberMiniPlayerScales(
    isLandscape: Boolean,
    baseHeight: Dp,
): MiniPlayerScales {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    // Observe configuration so rotation recomposes even if containerSize lags.
    val configuration = LocalConfiguration.current
    val container = windowInfo.containerSize
    val config = rememberAdaptiveScaleConfig()
    return remember(isLandscape, container, density.density, configuration.orientation, config) {
        val densityValue = density.density.takeIf { it > 0f } ?: 1f
        val widthDp = if (container.width > 0) container.width / densityValue
        else configuration.screenWidthDp.toFloat()
        val heightDp = if (container.height > 0) container.height / densityValue
        else configuration.screenHeightDp.toFloat()
        val shortest = minOf(widthDp, heightDp).takeIf { it > 0f }
            ?: ADAPTIVE_SCALE_BASELINE_DP
        val extra = if (config.mode == UiScaleMode.FIXED) config.fixedScale
        else miniPlayerExtraScale(shortest, config.maxScale)
        val base = if (isLandscape) 2f else 1f
        MiniPlayerScales(
            sizeScale = base * extra,
            adaptiveScale = extra,
            height = baseHeight * extra,
        )
    }
}
