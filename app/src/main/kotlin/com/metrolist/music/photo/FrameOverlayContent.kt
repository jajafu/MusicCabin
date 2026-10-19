package com.metrolist.music.photo

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.listentogether.RoomRole
import com.metrolist.music.ui.component.AutoResizeText
import com.metrolist.music.ui.component.FontSizeRange
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top overlay (clock, song info and the control row) shared by the photo
 * frame screens. The lyrics offset -/+ buttons share the per-song offset with
 * the lyrics page dialog, so every frame adjusts the same value.
 */
@Composable
internal fun FrameOverlayContent(
    uiScale: Float,
    showClock: Boolean,
    clockActive: Boolean,
    showSongInfo: Boolean,
    showLyrics: Boolean,
    canPreviousPhoto: Boolean,
    canNextPhoto: Boolean,
    textColor: Color,
    onSettings: () -> Unit,
    onPreviousPhoto: () -> Unit,
    onNextPhoto: () -> Unit,
    onSwitchLyrics: () -> Unit,
) {
    val connection = LocalPlayerConnection.current
    val metadata = connection?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    // Narrow screens: once the title is actually ellipsized, drop the artist so
    // the full title shows. Latched per track to avoid a show/hide loop.
    var hideArtist by remember(metadata?.id) { mutableStateOf(false) }
    val canPrevious = connection?.canSkipPrevious?.collectAsStateWithLifecycle()?.value == true
    val canNext = connection?.canSkipNext?.collectAsStateWithLifecycle()?.value == true
    val isPlaying = connection?.isEffectivelyPlaying?.collectAsStateWithLifecycle()?.value == true
    val role = LocalListenTogetherManager.current?.role?.collectAsStateWithLifecycle()?.value
    val ready = connection?.service?.isPlayerReady?.collectAsStateWithLifecycle()?.value == true
    val canControl = ready && metadata != null && role != RoomRole.GUEST
    // Same per-song offset as the lyrics page dialog: ±50ms per tap, clamped to ±3000ms.
    // Atomic DB update so rapid taps never lose increments.
    val currentSong = connection?.currentSong?.collectAsStateWithLifecycle()?.value
    val frameScope = rememberCoroutineScope()
    fun adjustLyricsOffset(deltaMs: Int) {
        val songId = currentSong?.song?.id ?: return
        frameScope.launch(Dispatchers.IO) {
            runCatching {
                connection?.database?.query {
                    shiftLyricsOffset(songId, deltaMs)
                }
            }
        }
    }
    val canAdjustOffset = canControl && currentSong != null
    val titleStyle = MaterialTheme.typography.titleLarge.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeight = TextUnit.Unspecified,
    )
    val artistStyle = MaterialTheme.typography.bodyLarge.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeight = TextUnit.Unspecified,
    )
    Column(verticalArrangement = Arrangement.spacedBy(FRAME_ROW_SPACING_DP.dp * uiScale)) {
        // Single-line info row: clock, title and artist share one visual center.
        // Row (instead of FlowRow) vertically centers the mixed text sizes;
        // the title shrinks with an ellipsis when the row is tight.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp * uiScale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showClock) FrameClock(clockActive, uiScale, textColor)
            if (showSongInfo && metadata != null) {
                AutoResizeText(
                    modifier = Modifier.weight(1f, fill = false),
                    text = metadata.title,
                    fontSizeRange = FontSizeRange(
                        min = titleStyle.fontSize * FRAME_TEXT_BASELINE_SCALE,
                        max = titleStyle.fontSize * FRAME_TEXT_BASELINE_SCALE * uiScale,
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = titleStyle,
                    onSettled = { if (it.lineCount > 0 && it.isLineEllipsized(0)) hideArtist = true },
                )
                if (!hideArtist) {
                    AutoResizeText(
                        text = metadata.artists.joinToString { it.name },
                        fontSizeRange = FontSizeRange(
                            min = artistStyle.fontSize * FRAME_TEXT_BASELINE_SCALE,
                            max = artistStyle.fontSize * FRAME_TEXT_BASELINE_SCALE * uiScale,
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = artistStyle,
                    )
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FRAME_ICON_SPACING_DP.dp * uiScale),
            verticalArrangement = Arrangement.spacedBy(FRAME_ROW_SPACING_DP.dp * uiScale),
        ) {
            FrameIcon(R.drawable.skip_previous, R.string.photo_frame_previous, uiScale, enabled = canControl && canPrevious) { connection?.seekToPrevious() }
            FrameIcon(
                if (isPlaying) R.drawable.pause else R.drawable.play,
                if (isPlaying) R.string.photo_frame_pause else R.string.photo_frame_play,
                uiScale,
                enabled = canControl,
            ) { connection?.togglePlayPause() }
            FrameIcon(R.drawable.skip_next, R.string.photo_frame_next, uiScale, enabled = canControl && canNext) { connection?.seekToNext() }
            FrameIcon(R.drawable.arrow_back, R.string.photo_frame_previous_photo, uiScale, enabled = canPreviousPhoto, onClick = onPreviousPhoto)
            FrameIcon(R.drawable.arrow_forward, R.string.photo_frame_next_photo, uiScale, enabled = canNextPhoto, onClick = onNextPhoto)
            if (showLyrics) {
                FrameIcon(R.drawable.lyrics, R.string.switch_lyrics_source, uiScale, enabled = canControl, onClick = onSwitchLyrics)
                FrameIcon(R.drawable.remove, R.string.lyrics_offset_decrease, uiScale, enabled = canAdjustOffset, onClick = { adjustLyricsOffset(-50) })
                FrameIcon(R.drawable.add, R.string.lyrics_offset_increase, uiScale, enabled = canAdjustOffset, onClick = { adjustLyricsOffset(50) })
            }
            // Photo selection already lives in the in-frame settings sheet; no shortcut here.
            FrameIcon(R.drawable.settings, R.string.photo_frame_settings, uiScale, onClick = onSettings)
        }
    }
}

@Composable
private fun FrameClock(active: Boolean, uiScale: Float, textColor: Color) {
    val context = LocalContext.current
    var time by remember { mutableStateOf(DateFormat.getTimeFormat(context).format(Date())) }
    LaunchedEffect(active) {
        if (active) while (true) {
            time = DateFormat.getTimeFormat(context).format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val style = MaterialTheme.typography.headlineLarge.copy(
        // Trim font padding and fixed line height so the clock top-aligns
        // with the song title and artist in the same row.
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeight = TextUnit.Unspecified,
    )
    AutoResizeText(
        text = time,
        fontSizeRange = FontSizeRange(
            min = style.fontSize * FRAME_TEXT_BASELINE_SCALE,
            max = style.fontSize * FRAME_TEXT_BASELINE_SCALE * uiScale,
        ),
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}

@Composable
private fun FrameIcon(icon: Int, label: Int, uiScale: Float, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(FRAME_ICON_BUTTON_BASELINE_DP.dp * uiScale),
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White.copy(alpha = 0.7f), disabledContentColor = Color.White.copy(alpha = 0.38f)),
    ) {
        Icon(
            painterResource(icon),
            stringResource(label),
            Modifier.size(FRAME_ICON_BASELINE_DP.dp * uiScale)
                .offset(y = (-FRAME_CONTROL_ICON_LIFT_DP * uiScale).dp),
        )
    }
}
