package com.metrolist.music.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.ui.screens.frameErrorMessage

private enum class TvFrameSettingsPage { LOCAL, TRANSFER, DISPLAY }

@Composable
internal fun TvPhotoFrameSettingsPanel(
    viewModel: TvPhotoFrameViewModel,
    onDismiss: () -> Unit,
    onBrowsePhotos: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val localSources = remember(state.sources) {
        state.sources.filterNot { TvPhotoReceiver.isImportedUri(context, it.uri) }
    }
    val importedCount = remember(state.sources) {
        state.sources.count { TvPhotoReceiver.isImportedUri(context, it.uri) }
    }
    var page by rememberSaveable { mutableStateOf(TvFrameSettingsPage.LOCAL) }
    var confirmClearLocal by rememberSaveable { mutableStateOf(false) }
    var confirmClearImported by rememberSaveable { mutableStateOf(false) }
    val closeFocus = remember { FocusRequester() }
    var closeFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        closeFocus.requestFocus()
    }
    LaunchedEffect(source) {
        if (page != TvFrameSettingsPage.DISPLAY) {
            page = if (source == TvPhotoFrameViewModel.Source.TRANSFER) TvFrameSettingsPage.TRANSFER
                else TvFrameSettingsPage.LOCAL
        }
    }
    DisposableEffect(page) {
        if (page == TvFrameSettingsPage.TRANSFER) viewModel.startReceiver()
        onDispose { if (page == TvFrameSettingsPage.TRANSFER) viewModel.stopReceiver() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(tvLocalizedString(R.string.tv_photo_frame_title, R.string.tv_photo_frame_title_zh_tw),
                        style = MaterialTheme.typography.headlineSmall)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(56.dp).focusRequester(closeFocus)
                            .onFocusChanged { closeFocused = it.isFocused }
                            .then(if (closeFocused) Modifier.border(4.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier),
                    ) { Icon(painterResource(R.drawable.close), stringResource(R.string.photo_frame_done)) }
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        Modifier.width(220.dp).padding(start = 20.dp, end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsPageButton(tvLocalizedString(R.string.tv_photo_local_title, R.string.tv_photo_local_title_zh_tw),
                            R.drawable.insert_photo, page == TvFrameSettingsPage.LOCAL) {
                            viewModel.selectSource(TvPhotoFrameViewModel.Source.LOCAL)
                            page = TvFrameSettingsPage.LOCAL
                        }
                        SettingsPageButton(tvLocalizedString(R.string.tv_photo_pair_title, R.string.tv_photo_pair_title_zh_tw),
                            R.drawable.upload, page == TvFrameSettingsPage.TRANSFER) {
                            viewModel.selectSource(TvPhotoFrameViewModel.Source.TRANSFER)
                            page = TvFrameSettingsPage.TRANSFER
                        }
                        SettingsPageButton(stringResource(R.string.photo_frame_display),
                            R.drawable.settings, page == TvFrameSettingsPage.DISPLAY) { page = TvFrameSettingsPage.DISPLAY }
                    }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 24.dp, bottom = 24.dp),
                    ) {
                        when (page) {
                            TvFrameSettingsPage.LOCAL -> {
                                item {
                                    Text(stringResource(R.string.photo_browser_browse_device), style = MaterialTheme.typography.titleLarge)
                                    Text(stringResource(R.string.photo_frame_sources, state.photos.count {
                                        !TvPhotoReceiver.isImportedUri(context, it.uri)
                                    }))
                                }
                                item {
                                    Button(onClick = onBrowsePhotos, enabled = state.initialized && !busy,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).tvFrameFocus()) {
                                        Text(stringResource(R.string.photo_browser_browse_device))
                                    }
                                }
                                item {
                                    Button(onClick = onDismiss, enabled = localSources.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).tvFrameFocus()) {
                                        Text(tvLocalizedString(R.string.tv_photo_play_local, R.string.tv_photo_play_local_zh_tw))
                                    }
                                }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(onClick = viewModel::rescan, enabled = localSources.isNotEmpty() && !busy,
                                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).tvFrameFocus()) {
                                            Text(stringResource(R.string.photo_frame_rescan))
                                        }
                                        TextButton(onClick = { confirmClearLocal = true }, enabled = localSources.isNotEmpty() && !busy,
                                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).tvFrameFocus()) {
                                            Text(stringResource(R.string.photo_frame_clear))
                                        }
                                    }
                                }
                                if (localSources.isEmpty()) item { Text(stringResource(R.string.photo_frame_empty)) }
                                items(localSources, key = { it.uri }) { source ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(source.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                        IconButton(onClick = { viewModel.removeSource(source.uri) }, enabled = !busy,
                                            modifier = Modifier.size(56.dp).tvFrameFocus()) {
                                            Icon(painterResource(R.drawable.close), stringResource(R.string.photo_frame_remove, source.name))
                                        }
                                    }
                                }
                            }
                            TvFrameSettingsPage.TRANSFER -> {
                                item {
                                    Text(tvLocalizedString(R.string.tv_photo_pair_title, R.string.tv_photo_pair_title_zh_tw),
                                        style = MaterialTheme.typography.titleLarge)
                                }
                                item {
                                    if (transfer.url != null) {
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TvPhotoQrCode(transfer.url!!) }
                                        Text(tvLocalizedString(R.string.tv_photo_pair_address, R.string.tv_photo_pair_address_zh_tw,
                                            transfer.url!!), style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary)
                                    } else Text(tvLocalizedString(
                                        if (transfer.error) R.string.tv_photo_pair_error else R.string.tv_photo_pair_waiting,
                                        if (transfer.error) R.string.tv_photo_pair_error_zh_tw else R.string.tv_photo_pair_waiting_zh_tw,
                                    ))
                                }
                                item { Text(tvLocalizedString(R.string.tv_photo_pair_hint, R.string.tv_photo_pair_hint_zh_tw)) }
                                item { Text(tvLocalizedString(R.string.tv_photo_pair_received, R.string.tv_photo_pair_received_zh_tw,
                                    transfer.count)) }
                                item { Text(tvLocalizedString(R.string.tv_photo_pair_stored, R.string.tv_photo_pair_stored_zh_tw,
                                    importedCount)) }
                                item {
                                    Button(onClick = onDismiss, enabled = importedCount > 0,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).tvFrameFocus()) {
                                        Text(tvLocalizedString(R.string.tv_photo_pair_play, R.string.tv_photo_pair_play_zh_tw))
                                    }
                                }
                                item {
                                    TextButton(onClick = { confirmClearImported = true }, enabled = importedCount > 0,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).tvFrameFocus(),
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                        Text(tvLocalizedString(R.string.tv_photo_pair_clear, R.string.tv_photo_pair_clear_zh_tw))
                                    }
                                }
                            }
                            TvFrameSettingsPage.DISPLAY -> {
                                item { Text(stringResource(R.string.photo_frame_display), style = MaterialTheme.typography.titleLarge) }
                                item { Text(stringResource(R.string.photo_frame_interval), style = MaterialTheme.typography.titleMedium) }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(5, 10, 15, 30, 60).forEach { seconds ->
                                            OutlinedButton(onClick = {
                                                viewModel.updateSettings(state.settings.copy(intervalSeconds = seconds))
                                            }, enabled = !busy, modifier = Modifier.weight(1f).tvFrameFocus(),
                                                border = BorderStroke(if (seconds == state.settings.intervalSeconds) 3.dp else 1.dp,
                                                    if (seconds == state.settings.intervalSeconds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                                                Text(stringResource(R.string.photo_frame_seconds, seconds))
                                            }
                                        }
                                    }
                                }
                                item { SettingsToggle(stringResource(R.string.photo_frame_fill), state.settings.crop, !busy) {
                                    viewModel.updateSettings(state.settings.copy(crop = !state.settings.crop))
                                } }
                                item { SettingsToggle(stringResource(R.string.photo_frame_clock), state.settings.showClock, !busy) {
                                    viewModel.updateSettings(state.settings.copy(showClock = !state.settings.showClock))
                                } }
                                item { SettingsToggle(stringResource(R.string.photo_frame_song_info), state.settings.showSongInfo, !busy) {
                                    viewModel.updateSettings(state.settings.copy(showSongInfo = !state.settings.showSongInfo))
                                } }
                            }
                        }
                        val visibleError = error ?: state.error
                        if (visibleError != null) item {
                            Text(stringResource(frameErrorMessage(visibleError)), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (confirmClearLocal) {
        ConfirmClearDialog(stringResource(R.string.photo_frame_clear),
            stringResource(R.string.photo_frame_clear_confirm),
            onDismiss = { confirmClearLocal = false },
            onConfirm = { confirmClearLocal = false; viewModel.clearLocal() })
    }
    if (confirmClearImported) {
        ConfirmClearDialog(tvLocalizedString(R.string.tv_photo_pair_clear, R.string.tv_photo_pair_clear_zh_tw),
            tvLocalizedString(R.string.tv_photo_pair_clear_confirm, R.string.tv_photo_pair_clear_confirm_zh_tw),
            onDismiss = { confirmClearImported = false },
            onConfirm = { confirmClearImported = false; viewModel.clearImported() })
    }
}

@Composable
private fun SettingsPageButton(label: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).tvFrameFocus(),
        border = BorderStroke(if (selected) 3.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
        Icon(painterResource(icon), null, Modifier.size(24.dp))
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SettingsToggle(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).tvFrameFocus(),
        border = BorderStroke(if (selected) 3.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
        Text(label, Modifier.weight(1f))
        Text(if (selected) "✓" else "○")
    }
}

@Composable
private fun ConfirmClearDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.tvFrameFocus()) { Text(title) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFrameFocus()) {
            Text(stringResource(R.string.photo_frame_cancel))
        } })
}

private fun Modifier.tvFrameFocus(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(4.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
}
