package com.metrolist.music.photo.v2

import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.photo.v2.drive.*
import androidx.compose.animation.Crossfade
import androidx.compose.material3.CircularProgressIndicator
import android.view.WindowManager

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.res.stringResource
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
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.R
import com.metrolist.music.listentogether.RoomRole
import com.metrolist.music.photo.FrameOverlayContent
import com.metrolist.music.photo.FramePlaybackCommand
import com.metrolist.music.photo.FrameError
import com.metrolist.music.photo.FrameLyricsOverlay
import com.metrolist.music.photo.FrameLyricsSourcePicker
import com.metrolist.music.photo.FrameImage
import com.metrolist.music.photo.FramePlaybackState
import com.metrolist.music.photo.FrameSlide
import com.metrolist.music.photo.FrameSlideDisplay
import com.metrolist.music.photo.MediaStorePhotoBrowser
import com.metrolist.music.photo.PhotoFramePlayback
import com.metrolist.music.photo.coilFrameOrientation
import com.metrolist.music.ui.component.rememberFrameUiScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val PHOTO_FRAME_V2_ROUTE = "photo_frame_v2"

@Composable
fun PhotoFrameV2Screen(
    navController: NavHostController,
    viewModel: LocalPhotoFrameViewModel = hiltViewModel(),
    driveViewModel: PhotoFrameV2ViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val actionError by viewModel.error.collectAsStateWithLifecycle()
    val generation by viewModel.generation.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val source by driveViewModel.source.collectAsStateWithLifecycle()
    val drive by driveViewModel.state.collectAsStateWithLifecycle()
    // Receive the cleared image state on STOP, instead of retaining a decoded frame until START.
    val cloudSlides by driveViewModel.slideshow.state.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val controller = driveViewModel.controller
    val authorizationUi = rememberDriveAuthorizationUi()
    val localError = actionError ?: state.error ?: drive.error?.takeIf { it.failure == DriveFailure.STORAGE }?.let { FrameError.STORAGE }
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showMediaBrowser by rememberSaveable { mutableStateOf(false) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showLyricsPicker by rememberSaveable { mutableStateOf(false) }
    val frameMetadata = LocalPlayerConnection.current?.mediaMetadata?.collectAsStateWithLifecycle()?.value
    val framePlayerConnection = LocalPlayerConnection.current
    val pickerScope = rememberCoroutineScope()
    val exit: () -> Unit = {
        if (!navController.popBackStack()) navController.navigate(Screens.Home.route) { launchSingleTop = true }
    }
    BackHandler(enabled = !showSettings && !showMediaBrowser, onBack = exit)

    DisposableEffect(lifecycle, viewModel, controller) {
        // Rotation recreates the activity but retains ViewModels; skip teardown then
        // so the Drive session and local queue survive and only the layout changes.
        fun isConfigChange(): Boolean = context.findActivity()?.isChangingConfigurations == true
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (foreground) {
                viewModel.initialize()
                controller.initialize()
            } else if (!isConfigChange()) {
                viewModel.cancelOperation()
                controller.background()
            }
        }
        lifecycle.addObserver(observer)
        if (foreground) { viewModel.initialize(); controller.initialize() }
        onDispose {
            lifecycle.removeObserver(observer)
            if (!isConfigChange()) {
                viewModel.cancelOperation()
                controller.stop()
            }
        }
    }
    FrameImmersiveMode(foreground)
    // The photo receiver (photo transfer + song requests) runs for as long as
    // this frame screen is in the foreground; leaving the screen stops it.
    LaunchedEffect(foreground) {
        if (foreground) viewModel.startReceiver() else viewModel.stopReceiver()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopReceiver() }
    }
    DisposableEffect(framePlayerConnection) {
        viewModel.attachPlayer(framePlayerConnection)
        onDispose { viewModel.attachPlayer(null) }
    }
    val listenTogether = LocalListenTogetherManager.current
    LaunchedEffect(listenTogether) {
        listenTogether?.role?.collect { role ->
            viewModel.attachGuestRole(role == RoomRole.GUEST)
        }
    }
    LaunchedEffect(foreground, source, drive.initialized, showSettings, showMediaBrowser) {
        driveViewModel.slideshow.setSuspended(!foreground || showSettings || showMediaBrowser)
        if (foreground && source == FrameV2Source.DRIVE && drive.initialized && !showSettings && !showMediaBrowser && !drive.busy) {
            driveViewModel.startCachedSlideshow()
            controller.resumeSavedFolder()
        }
        if (foreground && showSettings && source == FrameV2Source.DRIVE && drive.connected && !drive.busy) controller.refreshFolders()
    }
    LaunchedEffect(state.settings.intervalSeconds) { driveViewModel.slideshow.setInterval(state.settings.intervalSeconds) }
    var handledSelection by remember { mutableStateOf(drive.selectionRevision) }
    LaunchedEffect(drive.selectionRevision) {
        if (drive.selectionRevision != handledSelection) {
            handledSelection = drive.selectionRevision
            showSettings = false
        }
    }
    LaunchedEffect(drive.error, cloudSlides.error) {
        if (drive.error != null || cloudSlides.error != null) showControls = true
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // Bound decoding even on a 4K display; never decode at the photo's original size.
        val factor = (1920f / maxOf(constraints.maxWidth, constraints.maxHeight).coerceAtLeast(1)).coerceAtMost(1f)
        val width = (constraints.maxWidth * factor).toInt().coerceAtLeast(1)
        val height = (constraints.maxHeight * factor).toInt().coerceAtLeast(1)
        val uris = remember(state.photos, source) { if (source == FrameV2Source.LOCAL) state.photos.map { it.uri } else emptyList() }
        // ViewModel-retained: rotation reuses the shuffle order and resumes at the current photo.
        val session = viewModel.playbackSession(uris, generation)
        val isLandscape = constraints.maxWidth > constraints.maxHeight
        // Scale clock, song info and icons with the short edge (phone 360dp -> 1x).
        val uiScale = rememberFrameUiScale()
        // Decode size follows the latest layout without restarting playback;
        // rotation only re-lays out the current image and applies the new size to later photos.
        val decodeSize by rememberUpdatedState(width to height)
        val landscapeNow by rememberUpdatedState(isLandscape)
        // Pairing follows the current screen orientation; rotation applies to later photos.
        LaunchedEffect(isLandscape) { driveViewModel.slideshow.setLandscapeScreen(isLandscape) }
        // A cancelled decode can finish cleanup after its replacement has started.
        // Give each effect its own frame state so old cleanup cannot erase new images.
        var slides by remember(session, state.settings.intervalSeconds, foreground, showSettings, showMediaBrowser) {
            mutableStateOf(FramePlaybackState<coil3.Image>())
        }
        val fade = remember { Animatable(0f) }

        LaunchedEffect(session, state.settings.intervalSeconds, foreground, showSettings, showMediaBrowser) {
            if (!foreground || showSettings || showMediaBrowser || uris.isEmpty()) return@LaunchedEffect
            val playback = PhotoFramePlayback<coil3.Image>(
                load = { uri ->
                    val photoUri = uri.toUri()
                    require(photoUri.scheme == "content" || photoUri.scheme == "file")
                    val (decodeWidth, decodeHeight) = decodeSize
                    val request = ImageRequest.Builder(context)
                        .data(photoUri)
                        .size(decodeWidth, decodeHeight)
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
            if (source == FrameV2Source.DRIVE && foreground) {
                Crossfade(targetState = cloudSlides.current, animationSpec = tween(350), label = "Drive photo") { slide ->
                    if (slide != null) {
                        val frames = listOfNotNull(slide.image, slide.pairImage)
                            .mapIndexed { index, image -> FrameImage(index.toString(), image) }
                        FrameSlideDisplay(FrameSlide(frames), isLandscape, scale)
                    }
                }
            }
            slides.current?.let { FrameSlideDisplay(it, isLandscape, scale) }
            slides.incoming?.let { incoming ->
                FrameSlideDisplay(incoming, isLandscape, scale, Modifier.graphicsLayer { alpha = fade.value })
            }
        }
        val empty = when (source) {
            FrameV2Source.LOCAL -> uris.isEmpty() || slides.exhausted
            FrameV2Source.DRIVE -> cloudSlides.current == null
            null -> true
        }
        if (source == null || (source == FrameV2Source.LOCAL && (!state.initialized || busy) && slides.current == null) ||
            (source == FrameV2Source.DRIVE && (drive.busy || cloudSlides.loading) && cloudSlides.current == null)) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        if (state.settings.showLyrics && !showSettings && !showMediaBrowser) {
            FrameLyricsOverlay(textColor = Color.White.copy(alpha = 0.85f), uiScale = uiScale, s2tEnabled = state.settings.s2tEnabled)
        }
        if (showControls || empty) {
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.3f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(start = 4.dp * uiScale, end = 4.dp * uiScale, top = 4.dp * uiScale, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp * uiScale),
            ) {
                FrameOverlayContent(
                    uiScale = uiScale,
                    showClock = state.settings.showClock,
                    clockActive = foreground,
                    showSongInfo = state.settings.showSongInfo,
                    showLyrics = state.settings.showLyrics,
                    canPreviousPhoto = if (source == FrameV2Source.DRIVE) cloudSlides.canPrevious && !cloudSlides.loading else uris.size > 1,
                    canNextPhoto = if (source == FrameV2Source.DRIVE) cloudSlides.photoCount > 1 && !cloudSlides.loading else uris.size > 1,
                    textColor = Color.White.copy(alpha = 0.85f),
                    onSettings = { showSettings = true },
                    onPreviousPhoto = { if (source == FrameV2Source.DRIVE) driveViewModel.slideshow.previous() else session.request(FramePlaybackCommand.PREVIOUS) },
                    onNextPhoto = { if (source == FrameV2Source.DRIVE) driveViewModel.slideshow.next() else session.request(FramePlaybackCommand.NEXT) },
                    onSwitchLyrics = { showLyricsPicker = true },
                )
                if (source == FrameV2Source.LOCAL) {
                    val error = localError
                    if (error != null) Text(stringResource(frameErrorMessage(error)), color = Color.White)
                    else if (slides.exhausted) Text(stringResource(R.string.photo_frame_unreadable), color = Color.White)
                    else if (state.initialized && uris.isEmpty()) Text(frameV2String(FrameV2Text.ChooseSource), color = Color.White)
                } else if (source == FrameV2Source.DRIVE) {
                    val error = drive.error ?: cloudSlides.error
                    if (error != null) {
                        Text(frameV2String(error.failure.text()), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.frameStatus())
                        val code = error.code?.let { "${error.failure.name}-$it" } ?: error.failure.name
                        Text(frameV2String(FrameV2Text.ErrorCode, code), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.frameStatus())
                    }
                    else if (!drive.busy && !cloudSlides.loading && empty) Text(
                        frameV2String(if (cloudSlides.active) FrameV2Text.NoPlayablePhotos else FrameV2Text.ChooseSource), color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
    val lyricsPickerMetadata = if (showLyricsPicker) frameMetadata else null
    lyricsPickerMetadata?.let { metadata ->
        FrameLyricsSourcePicker(
            metadata = metadata,
            onDismiss = { showLyricsPicker = false },
            onPickLyric = {
                // New lyrics replace the old timing, so the previous per-song
                // offset no longer applies. Atomic reset for the picked track.
                pickerScope.launch(Dispatchers.IO) {
                    runCatching {
                        framePlayerConnection?.database?.query {
                            resetLyricsOffset(metadata.id)
                        }
                    }
                }
            },
        )
    }
    if (showMediaBrowser) {
        MediaStorePhotoBrowser(
            onDismiss = { showMediaBrowser = false },
            onPhotosSelected = { uris ->
                showMediaBrowser = false
                showSettings = false
                viewModel.addPhotos(uris)
            },
        )
    } else if (showSettings) {
        PhotoFrameV2SettingsPanel(
            state = state,
            busy = busy,
            error = localError,
            transfer = transfer,
            onClearReceived = viewModel::clearReceived,
            onDismiss = { showSettings = false; viewModel.dismissError() },
            onBrowsePhotos = { showMediaBrowser = true },
            onRescan = viewModel::rescan,
            onRemove = viewModel::removeSource,
            onClear = viewModel::clear,
            onCancelScan = viewModel::cancelOperation,
            onSettings = viewModel::updateSettings,
            source = source ?: FrameV2Source.LOCAL,
            onSource = driveViewModel::selectSource,
            drive = drive,
            controller = controller,
            authorizationUi = authorizationUi,
            slideshowActive = cloudSlides.active,
            slideshowPlaying = cloudSlides.playing,
            onToggleSlideshow = driveViewModel.slideshow::togglePlaying,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
        val keepScreenOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val behavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (!keepScreenOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.systemBarsBehavior = behavior
            if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
