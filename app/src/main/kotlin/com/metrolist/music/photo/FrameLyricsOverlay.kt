package com.metrolist.music.photo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.di.LyricsHelperEntryPoint
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.lyrics.lyricsTextLooksSynced
import com.metrolist.music.ui.component.AutoResizeText
import com.metrolist.music.ui.component.FontSizeRange
import com.metrolist.music.ui.component.rememberAdaptiveUiScale
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Phone-size scale relative to the Material typography base, so 1x renders the
 * stock headline/title/body sizes on a phone and grows to 3x on large head units.
 */
const val FRAME_TEXT_BASELINE_SCALE = 1f

/** Phone-size lyric scale relative to the Material typography base. */
const val FRAME_LYRICS_BASELINE_SCALE = 1f

/**
 * Phone-size scale for the control row. The fork's previous 64.dp / 48.dp buttons were sized for
 * the old doubled text baseline, so at 1x they looked oversized next to 32sp clock text; these
 * keep a 48.dp minimum touch target while matching the stock Material control sizes.
 */
const val FRAME_ICON_BUTTON_BASELINE_DP = 48f
const val FRAME_ICON_BASELINE_DP = 32f

/**
 * Vertical gap between the information row (clock/song/artist) and the control row. Kept small so
 * the two blocks read as one overlay instead of drifting apart on tall screens.
 */
const val FRAME_ROW_SPACING_DP = 2f

/**
 * Bottom lyric line for the photo frame overlays. Follows the synced lyric of the current song and
 * keeps to one or two lines, so it never competes with the photo above it. Plain (untimed) lyrics
 * have no current line to follow, so nothing is drawn for them.
 */
@Composable
fun BoxScope.FrameLyricsOverlay(
    textColor: Color = Color.White,
    modifier: Modifier = Modifier,
    uiScale: Float = rememberAdaptiveUiScale(),
) {
    val connection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val lyricsEntity by connection.currentLyrics.collectAsStateWithLifecycle()
    val song by connection.currentSong.collectAsStateWithLifecycle()
    val ready by connection.service.isPlayerReady.collectAsStateWithLifecycle()
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()

    // The service only pre-fetches lyrics when the player's own lyrics pane is enabled, so the
    // frame has to request them once per track or the layer stays blank.
    LaunchedEffect(metadata?.id, lyricsEntity) {
        val current = metadata ?: return@LaunchedEffect
        if (lyricsEntity != null) return@LaunchedEffect
        delay(FRAME_LYRICS_FETCH_DELAY_MS)
        withContext(Dispatchers.IO) {
            runCatching {
                val fetched = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    LyricsHelperEntryPoint::class.java,
                ).lyricsHelper().getLyrics(current)
                connection.database.query {
                    upsert(LyricsEntity(current.id, fetched.lyrics, fetched.provider))
                }
            }
        }
    }

    val lines = remember(lyricsEntity) {
        val text = lyricsEntity?.lyrics?.trim().orEmpty()
        if (text.isEmpty() || text == LyricsEntity.LYRICS_NOT_FOUND || !lyricsTextLooksSynced(text)) {
            emptyList()
        } else {
            LyricsUtils.parseLyrics(text)
        }
    }

    var lineIndex by remember(lines) { mutableIntStateOf(-1) }
    LaunchedEffect(lines, ready, song?.song?.lyricsOffset) {
        if (lines.isEmpty() || !ready) {
            lineIndex = -1
            return@LaunchedEffect
        }
        val offset = (song?.song?.lyricsOffset ?: 0).toLong()
        while (isActive) {
            val position = runCatching { connection.player.currentPosition }.getOrNull()
            if (position != null) lineIndex = LyricsUtils.findCurrentLineIndex(lines, position + offset)
            delay(FRAME_LYRICS_POLL_MS)
        }
    }

    val current = lines.getOrNull(lineIndex)?.text?.trim().orEmpty()
    if (current.isEmpty()) return
    // 1x is the stock Material titleLarge size on a phone; grow it with the screen
    // short edge, and never shrink a long line below that phone baseline.
    val style = MaterialTheme.typography.titleLarge
    val minFontSize = style.fontSize * FRAME_LYRICS_BASELINE_SCALE
    val maxFontSize = minFontSize * uiScale
    Box(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                ),
            )
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(horizontal = 24.dp * uiScale, vertical = 16.dp * uiScale),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = current,
            transitionSpec = { fadeIn(tween(FRAME_LYRICS_FADE_MS)) togetherWith fadeOut(tween(FRAME_LYRICS_FADE_MS)) },
            label = "Photo frame lyrics",
        ) { line ->
            AutoResizeText(
                text = line,
                fontSizeRange = FontSizeRange(min = minFontSize, max = maxFontSize),
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = style,
            )
        }
    }
}

private const val FRAME_LYRICS_POLL_MS = 100L
private const val FRAME_LYRICS_FADE_MS = 350
private const val FRAME_LYRICS_FETCH_DELAY_MS = 500L
