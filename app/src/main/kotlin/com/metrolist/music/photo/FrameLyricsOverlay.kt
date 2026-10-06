package com.metrolist.music.photo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.lyrics.isManualLyrics
import com.metrolist.music.lyrics.lyricsTextLooksSynced
import com.metrolist.music.ui.component.AutoResizeText
import com.metrolist.music.ui.component.FontSizeRange
import com.metrolist.music.ui.component.WordLevelLyrics
import com.metrolist.music.ui.component.wholeLineWords
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
 * Vertical gap between the information row (clock/song/artist) and the control row.
 */
const val FRAME_ROW_SPACING_DP = 2.5f

/** Lift control glyphs within their unchanged touch targets to reduce the visible row gap. */
const val FRAME_CONTROL_ICON_LIFT_DP = 5f

/**
 * Alpha for the previous/next lyric lines relative to the current line color.
 * Matches the lyrics page dimming, fixed at 40%.
 */
const val FRAME_LYRICS_SIDE_ALPHA = 0.4f

/**
 * Alpha for the not-yet-sung part of the frame's current line. Sung words
 * render at full [textColor]; without dimming the unsung part the KTV sweep
 * would be invisible.
 */
const val FRAME_KTV_UNSUNG_ALPHA = 0.45f

/** Tight vertical gap between the previous/current/next lyric lines. */
const val FRAME_LYRICS_LINE_SPACING_DP = 2f

/**
 * Bottom lyric lines for the photo frame overlays. Follows the synced lyric of the current song and
 * shows the previous and next lines like the lyrics page, so it never competes with the photo above it. Plain (untimed) lyrics
 * have no current line to follow, so nothing is drawn for them.
 */
@Composable
fun BoxScope.FrameLyricsOverlay(
    textColor: Color = Color.White,
    modifier: Modifier = Modifier,
    uiScale: Float = rememberAdaptiveUiScale(),
    ktvOnly: Boolean = false,
) {
    val connection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val lyricsEntity by connection.currentLyrics.collectAsStateWithLifecycle()
    val song by connection.currentSong.collectAsStateWithLifecycle()
    val ready by connection.service.isPlayerReady.collectAsStateWithLifecycle()
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    var ktvFallbackDoneFor by remember { mutableStateOf<String?>(null) }

    // The service only pre-fetches lyrics when the player's own lyrics pane is enabled, so the
    // frame has to request them once per track or the layer stays blank.
    LaunchedEffect(metadata?.id, lyricsEntity, ktvOnly) {
        val current = metadata ?: return@LaunchedEffect
        if (!ktvOnly && lyricsEntity != null) return@LaunchedEffect
        delay(FRAME_LYRICS_FETCH_DELAY_MS)
        withContext(Dispatchers.IO) {
            runCatching {
                val helper = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    LyricsHelperEntryPoint::class.java,
                ).lyricsHelper()
                if (!ktvOnly) {
                    val fetched = helper.getLyrics(current)
                    connection.database.query {
                        upsert(LyricsEntity(current.id, fetched.lyrics, fetched.provider))
                    }
                    return@runCatching
                }
                val manualPick = context.isManualLyrics(current.id)
                val cached = lyricsEntity?.lyrics
                if (!cached.isNullOrBlank() && cached != LyricsEntity.LYRICS_NOT_FOUND &&
                    (manualPick || LyricsUtils.isWordSynced(cached))
                ) {
                    return@runCatching
                }
                if (ktvFallbackDoneFor == current.id) return@runCatching
                helper.getKtvLyrics(current)?.let { ktv ->
                    // The user may have picked lyrics while the fetch was in
                    // flight; never overwrite an explicit pick.
                    if (context.isManualLyrics(current.id)) return@runCatching
                    connection.database.query {
                        upsert(LyricsEntity(current.id, ktv.lyrics, ktv.provider))
                    }
                    return@runCatching
                }
                ktvFallbackDoneFor = current.id
                if (context.isManualLyrics(current.id)) return@runCatching
                val fallback = helper.getLyrics(current)
                connection.database.query {
                    upsert(LyricsEntity(current.id, fallback.lyrics, fallback.provider))
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
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lines, ready, song?.song?.lyricsOffset) {
        if (lines.isEmpty() || !ready) {
            lineIndex = -1
            return@LaunchedEffect
        }
        val offset = (song?.song?.lyricsOffset ?: 0).toLong()
        while (isActive) {
            val position = runCatching { connection.player.currentPosition }.getOrNull()
            if (position != null) {
                positionMs = position
                lineIndex = LyricsUtils.findCurrentLineIndex(lines, position + offset)
            }
            delay(FRAME_LYRICS_POLL_MS)
        }
    }

    val entry = lines.getOrNull(lineIndex)
    val current = entry?.text?.trim().orEmpty()
    if (current.isEmpty()) return
    val lyricsOffset = (song?.song?.lyricsOffset ?: 0).toLong()
    val previous = lines.getOrNull(lineIndex - 1)?.text?.trim().orEmpty()
    val next = lines.getOrNull(lineIndex + 1)?.text?.trim().orEmpty()
    val lineEndMs = lines.getOrNull(lineIndex + 1)?.time
    val window = Triple(previous, current, next)
    // 1x is the stock Material titleLarge size on a phone; grow it with the screen
    // short edge, and never shrink a long line below that phone baseline.
    val style = MaterialTheme.typography.titleLarge
    val minFontSize = style.fontSize * FRAME_LYRICS_BASELINE_SCALE
    val maxFontSize = minFontSize * uiScale
    val sideColor = textColor.copy(alpha = textColor.alpha * FRAME_LYRICS_SIDE_ALPHA)
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
            .padding(horizontal = 24.dp * uiScale, vertical = 12.dp * uiScale),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = window,
            transitionSpec = { fadeIn(tween(FRAME_LYRICS_FADE_MS)) togetherWith fadeOut(tween(FRAME_LYRICS_FADE_MS)) },
            label = "Photo frame lyrics",
        ) { (prevLine, currentLine, nextLine) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FRAME_LYRICS_LINE_SPACING_DP.dp * uiScale),
            ) {
                if (prevLine.isNotEmpty()) {
                    AutoResizeText(
                        text = prevLine,
                        fontSizeRange = FontSizeRange(min = minFontSize, max = maxFontSize),
                        color = sideColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = style,
                    )
                }
                FrameKtvCurrentLine(
                    entry = entry,
                    lineEndMs = lineEndMs,
                    currentPositionMs = positionMs,
                    lyricsOffset = lyricsOffset,
                    textColor = textColor,
                    maxFontSize = maxFontSize,
                )
                if (nextLine.isNotEmpty()) {
                    AutoResizeText(
                        text = nextLine,
                        fontSizeRange = FontSizeRange(min = minFontSize, max = maxFontSize),
                        color = sideColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = style,
                    )
                }
            }
        }
    }
}

private const val FRAME_LYRICS_POLL_MS = 100L
private const val FRAME_LYRICS_FADE_MS = 350
private const val FRAME_LYRICS_FETCH_DELAY_MS = 500L
private const val FRAME_KTV_FALLBACK_MS = 3000L

/**
 * Current frame line rendered with the shared KTV engine. Word-synced sources
 * highlight per word; line-level sources light the whole line at once.
 * Previous/next lines stay plain dimmed text in the caller.
 */
@Composable
private fun FrameKtvCurrentLine(
    entry: LyricsEntry?,
    lineEndMs: Long?,
    currentPositionMs: Long,
    lyricsOffset: Long,
    textColor: Color,
    maxFontSize: androidx.compose.ui.unit.TextUnit,
) {
    val connection = LocalPlayerConnection.current ?: return
    val item = entry ?: return
    val text = item.text.trim()
    if (text.isEmpty()) return
    val words = item.words?.takeIf { it.isNotEmpty() }
        ?: wholeLineWords(text, item.time, lineEndMs ?: (item.time + FRAME_KTV_FALLBACK_MS))
    WordLevelLyrics(
        mainText = text,
        words = words,
        isActiveLine = true,
        currentPositionState = currentPositionMs,
        lyricsOffset = lyricsOffset,
        playerConnection = connection,
        lyricStyle = androidx.compose.ui.text.TextStyle(
            fontSize = maxFontSize,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        ),
        lineColor = textColor,
        expressiveAccent = textColor,
        isBackground = item.isBackground,
        focusedAlpha = textColor.alpha * FRAME_KTV_UNSUNG_ALPHA,
        alignment = TextAlign.Center,
    )
}
