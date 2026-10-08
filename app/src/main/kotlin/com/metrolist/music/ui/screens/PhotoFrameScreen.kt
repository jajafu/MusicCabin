package com.metrolist.music.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import com.metrolist.music.photo.FrameSlideDisplay
import com.metrolist.music.photo.coilFrameOrientation
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.listentogether.RoomRole
import com.metrolist.music.photo.FrameLyricsOverlay
import com.metrolist.music.photo.FrameLyricsSourcePicker
import com.metrolist.music.photo.FRAME_CONTROL_ICON_LIFT_DP
import com.metrolist.music.photo.FRAME_ICON_BASELINE_DP
import com.metrolist.music.photo.FRAME_ICON_BUTTON_BASELINE_DP
import com.metrolist.music.photo.FRAME_ICON_SPACING_DP
import com.metrolist.music.photo.FRAME_ROW_SPACING_DP
import com.metrolist.music.photo.FRAME_TEXT_BASELINE_SCALE
import com.metrolist.music.photo.FramePlaybackCommand
import com.metrolist.music.photo.FramePlaybackState
import com.metrolist.music.photo.FramePlaybackSession
import com.metrolist.music.photo.PhotoFramePlayback
import com.metrolist.music.ui.component.AutoResizeText
import com.metrolist.music.ui.component.FontSizeRange
import com.metrolist.music.ui.component.rememberFrameUiScale
import com.metrolist.music.viewmodels.PhotoFrameViewModel
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun PhotoFrameScreen(navController: NavHostController, viewModel: PhotoFrameViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val actionError by viewModel.error.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val generation by viewModel.generation.collectAsStateWithLifecycle()
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showMediaBrowser by rememberSaveable { mutableStateOf(false) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showLyricsPicker by rememberSaveable { mutableStateOf(false) }
    val frameMetadata = LocalPlayerConnection.current?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    val exit: () -> Unit = {
        if (!navController.popBackStack()) navController.navigate(Screens.Home.route) { launchSingleTop = true }
    }
    BackHandler(enabled = !showSettings && !showMediaBrowser, onBack = exit)

    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (foreground) viewModel.initialize() else viewModel.cancelOperation()
        }
        lifecycle.addObserver(observer)
        if (foreground) viewModel.initialize()
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.cancelOperation()
        }
    }
    FrameImmersiveMode(foreground)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // Bound decoding even on a 4K display; never decode at the photo's original size.
        val factor = (1920f / maxOf(constraints.maxWidth, constraints.maxHeight).coerceAtLeast(1)).coerceAtMost(1f)
        val width = (constraints.maxWidth * factor).toInt().coerceAtLeast(1)
        val height = (constraints.maxHeight * factor).toInt().coerceAtLeast(1)
        val uris = remember(state.photos) { state.photos.map { it.uri } }
        val session = remember(uris, generation) { FramePlaybackSession(uris) }
        val isLandscape = constraints.maxWidth > constraints.maxHeight
        val landscapeNow by rememberUpdatedState(isLandscape)
        // Scale clock, song info, icons and padding with the short edge so a
        // 720dp head unit renders larger than a 360dp phone.
        val uiScale = rememberFrameUiScale()
        // A cancelled decode can finish cleanup after its replacement has started.
        // Give each effect its own frame state so old cleanup cannot erase new images.
        var slides by remember(session, width, height, state.settings.intervalSeconds, foreground, showSettings, showMediaBrowser) {
            mutableStateOf(FramePlaybackState<coil3.Image>())
        }
        val fade = remember { Animatable(0f) }

        LaunchedEffect(session, width, height, state.settings.intervalSeconds, foreground, showSettings, showMediaBrowser) {
            if (!foreground || showSettings || showMediaBrowser) return@LaunchedEffect
            val playback = PhotoFramePlayback<coil3.Image>(
                load = { uri ->
                    val photoUri = uri.toUri()
                    require(photoUri.scheme == "content" || photoUri.scheme == "file")
                    val request = ImageRequest.Builder(context)
                        .data(photoUri)
                        .size(width, height)
                        .scale(Scale.FIT)
                        .precision(Precision.EXACT)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .networkCachePolicy(CachePolicy.DISABLED)
                        .build()
                    (context.imageLoader.execute(request) as? SuccessResult)?.image
                },
                onUnreadable = viewModel::markUnreadable,
                unavailableUris = viewModel::unavailableUris,
            )
            try {
                playback.play(
                    session,
                    state.settings.intervalSeconds * 1000L,
                    isLandscape = { landscapeNow },
                    orientationOf = ::coilFrameOrientation,
                ) { slides = it }
                // Keep image ownership within this effect, including single-photo/empty states.
                kotlinx.coroutines.awaitCancellation()
            } finally {
                slides = FramePlaybackState()
            }
        }
        LaunchedEffect(slides.incoming) {
            fade.snapTo(0f)
            if (slides.incoming != null) fade.animateTo(1f, tween(350))
        }

        val scale = if (state.settings.crop) ContentScale.Crop else ContentScale.Fit
        val toggleLabel = stringResource(R.string.photo_frame_toggle_controls)
        Box(Modifier.fillMaxSize().clickable(onClickLabel = toggleLabel) { showControls = !showControls }) {
            FrameSlideDisplay(slides.current, isLandscape, scale)
            slides.incoming?.let { incoming ->
                FrameSlideDisplay(incoming, isLandscape, scale, Modifier.graphicsLayer { alpha = fade.value })
            }
        }
        if (state.settings.showLyrics && !showSettings && !showMediaBrowser) {
            FrameLyricsOverlay(uiScale = uiScale, s2tEnabled = state.settings.s2tEnabled)
        }
        if (showControls || uris.isEmpty() || slides.exhausted) {
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp * uiScale),
                verticalArrangement = Arrangement.spacedBy(8.dp * uiScale),
            ) {
                FrameOverlayContent(
                    uiScale = uiScale,
                    showClock = state.settings.showClock,
                    clockActive = foreground,
                    showSongInfo = state.settings.showSongInfo,
                    canNavigatePhotos = uris.size > 1,
                    onSettings = { showSettings = true },
                    onPreviousPhoto = { session.request(FramePlaybackCommand.PREVIOUS) },
                    onNextPhoto = { session.request(FramePlaybackCommand.NEXT) },
                    onExit = exit,
                    onSwitchLyrics = { showLyricsPicker = true },
                )
                val error = actionError ?: state.error
                if (error != null) Text(stringResource(frameErrorMessage(error)), color = Color.White, style = MaterialTheme.typography.bodySmall)
                else if (slides.exhausted) Text(stringResource(R.string.photo_frame_unreadable), color = Color.White)
            }
        }
    }
    val lyricsPickerMetadata = if (showLyricsPicker) frameMetadata else null
    lyricsPickerMetadata?.let { metadata ->
        FrameLyricsSourcePicker(
            metadata = metadata,
            onDismiss = { showLyricsPicker = false },
        )
    }
    if (showMediaBrowser) {
        MediaStorePhotoBrowser(
            onDismiss = { showMediaBrowser = false },
            onPhotosSelected = { uris ->
                showMediaBrowser = false
                viewModel.addPhotos(uris)
            },
        )
    } else if (showSettings) {
        PhotoFrameSettingsPanel(
            state = state,
            busy = busy,
            error = actionError ?: state.error,
            transfer = transfer,
            onDismiss = { showSettings = false; viewModel.dismissError() },
            onBrowsePhotos = { showMediaBrowser = true },
            onRescan = viewModel::rescan,
            onRemove = viewModel::removeSource,
            onClear = viewModel::clear,
            onClearReceived = viewModel::clearReceived,
            onStartReceiver = viewModel::startReceiver,
            onStopReceiver = viewModel::stopReceiver,
            onCancelScan = viewModel::cancelOperation,
            onSettings = viewModel::updateSettings,
        )
    }
}

@Composable
private fun FrameClock(active: Boolean, uiScale: Float) {
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
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}

@Composable
private fun FrameOverlayContent(
    uiScale: Float,
    showClock: Boolean,
    clockActive: Boolean,
    showSongInfo: Boolean,
    canNavigatePhotos: Boolean,
    onSettings: () -> Unit,
    onPreviousPhoto: () -> Unit,
    onNextPhoto: () -> Unit,
    onExit: () -> Unit,
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
            if (showClock) FrameClock(clockActive, uiScale)
            if (showSongInfo && metadata != null) {
                AutoResizeText(
                    modifier = Modifier.weight(1f, fill = false),
                    text = metadata.title,
                    fontSizeRange = FontSizeRange(
                        min = titleStyle.fontSize * FRAME_TEXT_BASELINE_SCALE,
                        max = titleStyle.fontSize * FRAME_TEXT_BASELINE_SCALE * uiScale,
                    ),
                    color = Color.White,
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
                        color = Color.White,
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
            FrameIcon(R.drawable.arrow_back, R.string.photo_frame_previous_photo, uiScale, enabled = canNavigatePhotos, onClick = onPreviousPhoto)
            FrameIcon(R.drawable.arrow_forward, R.string.photo_frame_next_photo, uiScale, enabled = canNavigatePhotos, onClick = onNextPhoto)
            FrameIcon(R.drawable.lyrics, R.string.switch_lyrics_source, uiScale, enabled = canControl, onClick = onSwitchLyrics)
            // Photo selection already lives in the in-frame settings sheet; no shortcut here.
            FrameIcon(R.drawable.settings, R.string.photo_frame_settings, uiScale, onClick = onSettings)
            FrameIcon(R.drawable.close, R.string.photo_frame_exit, uiScale, onClick = onExit)
        }
    }
}

@Composable
private fun FrameIcon(icon: Int, label: Int, uiScale: Float, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(FRAME_ICON_BUTTON_BASELINE_DP.dp * uiScale),
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.38f)),
    ) {
        Icon(
            painterResource(icon),
            stringResource(label),
            Modifier.size(FRAME_ICON_BASELINE_DP.dp * uiScale)
                .offset(y = (-FRAME_CONTROL_ICON_LIFT_DP * uiScale).dp),
        )
    }
}

@Composable
private fun FrameImmersiveMode(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, active) {
        var unwrapped: Context = context
        while (unwrapped is ContextWrapper && unwrapped !is Activity) unwrapped = unwrapped.baseContext
        val activity = unwrapped as? Activity
        if (!active || activity == null) return@DisposableEffect onDispose { }
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val behavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.systemBarsBehavior = behavior
            if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
