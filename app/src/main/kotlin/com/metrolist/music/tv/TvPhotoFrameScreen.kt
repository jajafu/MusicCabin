package com.metrolist.music.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.DateFormat
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import coil3.compose.asPainter
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.listentogether.RoomRole
import com.metrolist.music.photo.FramePlaybackCommand
import com.metrolist.music.photo.FramePlaybackState
import com.metrolist.music.photo.FramePlaybackSession
import com.metrolist.music.photo.PhotoFramePlayback
import com.metrolist.music.ui.screens.frameErrorMessage
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun TvPhotoFrameScreen(onExit: () -> Unit, viewModel: TvPhotoFrameViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val actionError by viewModel.error.collectAsStateWithLifecycle()
    val generation by viewModel.generation.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showMediaBrowser by rememberSaveable { mutableStateOf(false) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    val frameFocus = remember { FocusRequester() }
    var revealKeyCode by remember { mutableStateOf<Int?>(null) }
    BackHandler(enabled = !showSettings && !showMediaBrowser) {
        if (showControls) onExit() else showControls = true
    }
    LaunchedEffect(showControls) {
        if (!showControls) {
            withFrameNanos { }
            frameFocus.requestFocus()
        }
    }

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

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(frameFocus)
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (keyCode == revealKeyCode) {
                    if (event.type == KeyEventType.KeyUp) revealKeyCode = null
                    true
                } else if (event.type == KeyEventType.KeyDown && keyCode == AndroidKeyEvent.KEYCODE_MENU) {
                    revealKeyCode = keyCode
                    showControls = !showControls
                    true
                } else if (!showControls && event.type == KeyEventType.KeyDown && isFrameRevealKey(keyCode)) {
                    revealKeyCode = keyCode
                    showControls = true
                    true
                } else false
            }.focusable(),
    ) {
        // Bound decoding even on a 4K display; never decode at the photo's original size.
        val factor = (1920f / maxOf(constraints.maxWidth, constraints.maxHeight).coerceAtLeast(1)).coerceAtMost(1f)
        val width = (constraints.maxWidth * factor).toInt().coerceAtLeast(1)
        val height = (constraints.maxHeight * factor).toInt().coerceAtLeast(1)
        val uris = remember(state.photos, source) {
            state.photos.filter { photo ->
                val imported = TvPhotoReceiver.isImportedUri(context, photo.uri)
                if (source == TvPhotoFrameViewModel.Source.TRANSFER) imported else !imported
            }.map { it.uri }
        }
        val session = remember(uris, generation) { FramePlaybackSession(uris) }
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
                playback.play(session, state.settings.intervalSeconds * 1000L) { slides = it }
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
            slides.current?.let { frame ->
                Image(
                    painter = remember(frame.image) { frame.image.asPainter(context) },
                    contentDescription = null,
                    contentScale = scale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            slides.incoming?.let { frame ->
                Image(
                    painter = remember(frame.image) { frame.image.asPainter(context) },
                    contentDescription = null,
                    contentScale = scale,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
                )
            }
        }
        if (showControls) {
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FrameOverlayContent(
                    showClock = state.settings.showClock,
                    clockActive = foreground,
                    showSongInfo = state.settings.showSongInfo,
                    canSelect = state.initialized && !busy,
                    canNavigatePhotos = uris.size > 1,
                    onSelect = { viewModel.selectSource(TvPhotoFrameViewModel.Source.LOCAL); showMediaBrowser = true },
                    onSettings = { showSettings = true },
                    onPreviousPhoto = { session.request(FramePlaybackCommand.PREVIOUS) },
                    onNextPhoto = { session.request(FramePlaybackCommand.NEXT) },
                    onExit = onExit,
                    onHideControls = { showControls = false },
                )
                val error = actionError ?: state.error
                if (error != null) Text(stringResource(frameErrorMessage(error)), color = Color.White, style = MaterialTheme.typography.bodySmall)
                else if (slides.exhausted) Text(stringResource(R.string.photo_frame_unreadable), color = Color.White)
                else if (source == TvPhotoFrameViewModel.Source.TRANSFER && uris.isEmpty()) {
                    Text(tvLocalizedString(R.string.tv_photo_pair_open_settings, R.string.tv_photo_pair_open_settings_zh_tw),
                        color = Color.White)
                }
            }
        }
    }
    if (showMediaBrowser) {
        TvMediaStorePhotoBrowser(
            onDismiss = { showMediaBrowser = false },
            onPhotosSelected = { uris ->
                showMediaBrowser = false
                showSettings = false
                viewModel.selectSource(TvPhotoFrameViewModel.Source.LOCAL)
                viewModel.addPhotos(uris)
            },
            tvMode = true,
        )
    } else if (showSettings) {
        TvPhotoFrameSettingsPanel(
            viewModel = viewModel,
            onDismiss = { showSettings = false; viewModel.dismissError() },
            onBrowsePhotos = { showMediaBrowser = true },
        )
    }
}

@Composable
private fun FrameClock(active: Boolean) {
    val context = LocalContext.current
    var time by remember { mutableStateOf(DateFormat.getTimeFormat(context).format(Date())) }
    LaunchedEffect(active) {
        if (active) while (true) {
            time = DateFormat.getTimeFormat(context).format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val style = MaterialTheme.typography.headlineLarge
    Text(
        text = time,
        color = Color.White,
        maxLines = 1,
        style = style.copy(fontSize = style.fontSize * 2f, lineHeight = style.lineHeight * 2f),
    )
}

@Composable
private fun FrameOverlayContent(
    showClock: Boolean,
    clockActive: Boolean,
    showSongInfo: Boolean,
    canSelect: Boolean,
    canNavigatePhotos: Boolean,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    onPreviousPhoto: () -> Unit,
    onNextPhoto: () -> Unit,
    onExit: () -> Unit,
    onHideControls: () -> Unit,
) {
    val connection = LocalPlayerConnection.current
    val metadata = connection?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    val canPrevious = connection?.canSkipPrevious?.collectAsStateWithLifecycle()?.value == true
    val canNext = connection?.canSkipNext?.collectAsStateWithLifecycle()?.value == true
    val isPlaying = connection?.isEffectivelyPlaying?.collectAsStateWithLifecycle()?.value == true
    val role = LocalListenTogetherManager.current?.role?.collectAsStateWithLifecycle()?.value
    val ready = connection?.service?.isPlayerReady?.collectAsStateWithLifecycle()?.value == true
    val canControl = ready && metadata != null && role != RoomRole.GUEST
    val titleStyle = MaterialTheme.typography.titleLarge
    val artistStyle = MaterialTheme.typography.bodyLarge
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showClock) FrameClock(clockActive)
        if (showSongInfo && metadata != null) {
            Text(
                text = metadata.title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = titleStyle.copy(fontSize = titleStyle.fontSize * 2f, lineHeight = titleStyle.lineHeight * 2f),
            )
            Text(
                text = metadata.artists.joinToString { it.name },
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = artistStyle.copy(fontSize = artistStyle.fontSize * 2f, lineHeight = artistStyle.lineHeight * 2f),
            )
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FrameIcon(R.drawable.skip_previous, R.string.photo_frame_previous, enabled = canControl && canPrevious) { connection?.seekToPrevious() }
        FrameIcon(
            if (isPlaying) R.drawable.pause else R.drawable.play,
            if (isPlaying) R.string.photo_frame_pause else R.string.photo_frame_play,
            enabled = canControl,
        ) { connection?.togglePlayPause() }
        FrameIcon(R.drawable.skip_next, R.string.photo_frame_next, enabled = canControl && canNext) { connection?.seekToNext() }
        FrameIcon(R.drawable.arrow_back, R.string.photo_frame_previous_photo, enabled = canNavigatePhotos, onClick = onPreviousPhoto)
        FrameIcon(R.drawable.arrow_forward, R.string.photo_frame_next_photo, enabled = canNavigatePhotos, onClick = onNextPhoto)
        FrameIcon(R.drawable.insert_photo, R.string.photo_frame_pick_photos, enabled = canSelect, onClick = onSelect)
        FrameIcon(R.drawable.settings, R.string.photo_frame_settings, autoFocus = true, onClick = onSettings)
        FrameIcon(R.drawable.fullscreen, R.string.tv_frame_hide_controls, onClick = onHideControls)
        FrameIcon(R.drawable.close, R.string.photo_frame_exit, onClick = onExit)
    }
}

@Composable
private fun FrameIcon(icon: Int, label: Int, enabled: Boolean = true, autoFocus: Boolean = false, onClick: () -> Unit) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(autoFocus) { if (autoFocus) requester.requestFocus() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(64.dp)
            .then(if (autoFocus) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(3.dp, Color.White, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) else Modifier),
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.38f)),
    ) {
        Icon(painterResource(icon), stringResource(label), Modifier.size(48.dp))
    }
}

private fun isFrameRevealKey(keyCode: Int): Boolean = when (keyCode) {
    AndroidKeyEvent.KEYCODE_DPAD_UP,
    AndroidKeyEvent.KEYCODE_DPAD_DOWN,
    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
    AndroidKeyEvent.KEYCODE_ENTER -> true
    else -> false
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
