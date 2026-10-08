package com.metrolist.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.R
import com.metrolist.music.photo.FrameCatalogState
import com.metrolist.music.photo.FrameError
import com.metrolist.music.photo.FrameSettings
import com.metrolist.music.photo.FramePhotoQrCode
import com.metrolist.music.photo.FramePhotoReceiver
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.viewmodels.PhotoFrameViewModel
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoFrameSettingsPanel(
    state: FrameCatalogState,
    busy: Boolean,
    error: FrameError?,
    transfer: PhotoFrameViewModel.TransferState,
    onDismiss: () -> Unit,
    onBrowsePhotos: () -> Unit,
    onRescan: () -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onClearReceived: () -> Unit,
    onStartReceiver: () -> Unit,
    onStopReceiver: () -> Unit,
    onCancelScan: () -> Unit,
    onSettings: (FrameSettings) -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmClearReceived by rememberSaveable { mutableStateOf(false) }
    var transferOpen by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val localSources = remember(state.sources, context) {
        state.sources.filterNot { FramePhotoReceiver.isImportedUri(context, it.uri) }
    }
    val receivedCount = state.sources.size - localSources.size
    val localPhotoCount = remember(state.photos, context) {
        state.photos.count { !FramePhotoReceiver.isImportedUri(context, it.uri) }
    }
    DisposableEffect(transferOpen, lifecycle) {
        if (transferOpen) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onStartReceiver()
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> onStartReceiver()
                    Lifecycle.Event.ON_STOP -> onStopReceiver()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                onStopReceiver()
            }
        } else onDispose { onStopReceiver() }
    }
    val enabled = state.initialized && !busy
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            item {
                Text(stringResource(R.string.photo_frame), style = MaterialTheme.typography.headlineSmall)
                Text(frameTransferString(R.string.frame_transfer_intro, R.string.frame_transfer_intro_zh_tw),
                    style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Button(onClick = onBrowsePhotos, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.photo_browser_browse_device))
                }
                if (state.sources.isEmpty()) Text(stringResource(R.string.photo_frame_empty))
            }
            item {
                Button(onClick = { transferOpen = !transferOpen }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(frameTransferString(R.string.tv_photo_pair_title, R.string.tv_photo_pair_title_zh_tw))
                }
            }
            if (transferOpen) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(frameTransferString(R.string.frame_transfer_hint, R.string.frame_transfer_hint_zh_tw))
                        if (transfer.url != null) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                FramePhotoQrCode(transfer.url,
                                    frameTransferString(R.string.frame_transfer_qr_hint, R.string.frame_transfer_qr_hint_zh_tw))
                            }
                            Text(frameTransferString(R.string.tv_photo_pair_address, R.string.tv_photo_pair_address_zh_tw,
                                transfer.url), color = MaterialTheme.colorScheme.primary)
                        } else Text(frameTransferString(
                            if (transfer.error) R.string.frame_transfer_error else R.string.tv_photo_pair_waiting,
                            if (transfer.error) R.string.frame_transfer_error_zh_tw else R.string.tv_photo_pair_waiting_zh_tw,
                        ))
                        Text(frameTransferString(R.string.tv_photo_pair_received, R.string.tv_photo_pair_received_zh_tw,
                            transfer.count))
                        Text(frameTransferString(R.string.frame_transfer_stored, R.string.frame_transfer_stored_zh_tw,
                            receivedCount))
                        OutlinedButton(
                            onClick = { confirmClearReceived = true },
                            enabled = enabled && receivedCount > 0,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(frameTransferString(R.string.tv_photo_pair_clear, R.string.tv_photo_pair_clear_zh_tw))
                        }
                    }
                }
            }
            if (busy) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.photo_frame_scanning, state.scanCount))
                    OutlinedButton(onClick = onCancelScan, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.photo_frame_cancel))
                    }
                }
            }
            if (error != null) item {
                Text(stringResource(frameErrorMessage(error)), color = MaterialTheme.colorScheme.error)
            }
            item {
                Material3SettingsGroup(
                    title = stringResource(R.string.photo_frame_display),
                    items = listOf(
                        Material3SettingsItem(
                            title = {
                                FrameIntervalSlider(
                                    intervalSeconds = state.settings.intervalSeconds,
                                    enabled = enabled,
                                ) { intervalSeconds ->
                                    onSettings(state.settings.copy(intervalSeconds = intervalSeconds))
                                }
                            },
                            enabled = enabled,
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.photo_frame_fill)) },
                            description = { Text(stringResource(R.string.photo_frame_fill_desc)) },
                            trailingContent = {
                                FrameSettingSwitch(R.string.photo_frame_fill, state.settings.crop, enabled) { onSettings(state.settings.copy(crop = it)) }
                            },
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.photo_frame_clock)) },
                            trailingContent = {
                                FrameSettingSwitch(R.string.photo_frame_clock, state.settings.showClock, enabled) { onSettings(state.settings.copy(showClock = it)) }
                            },
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.photo_frame_song_info)) },
                            trailingContent = {
                                FrameSettingSwitch(R.string.photo_frame_song_info, state.settings.showSongInfo, enabled) { onSettings(state.settings.copy(showSongInfo = it)) }
                            },
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.photo_frame_lyrics)) },
                            trailingContent = {
                                FrameSettingSwitch(R.string.photo_frame_lyrics, state.settings.showLyrics, enabled) { onSettings(state.settings.copy(showLyrics = it)) }
                            },
                        ),
                        Material3SettingsItem(
                            title = { Text(frameTransferString(R.string.photo_frame_s2t, R.string.photo_frame_s2t_zh_tw)) },
                            trailingContent = {
                                FrameSettingSwitch(frameTransferString(R.string.photo_frame_s2t, R.string.photo_frame_s2t_zh_tw), state.settings.s2tEnabled, enabled) { onSettings(state.settings.copy(s2tEnabled = it)) }
                            },
                        ),
                    ),
                )
            }
            item {
                Text(frameTransferString(R.string.frame_transfer_local_count, R.string.frame_transfer_local_count_zh_tw,
                    localPhotoCount), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRescan, enabled = enabled && localSources.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.photo_frame_rescan))
                    }
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        enabled = enabled && state.sources.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(frameTransferString(R.string.frame_transfer_clear_all, R.string.frame_transfer_clear_all_zh_tw))
                    }
                }
            }
            items(localSources, key = { "${it.type}:${it.uri}" }) { source ->
                ListItem(
                    headlineContent = { Text(source.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onRemove(source.uri) }, enabled = enabled) {
                                Icon(painterResource(R.drawable.close), stringResource(R.string.photo_frame_remove, source.name))
                            }
                        }
                    },
                )
            }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 56.dp),
            ) {
                Icon(painterResource(R.drawable.check), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.photo_frame_done))
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(frameTransferString(R.string.frame_transfer_clear_all, R.string.frame_transfer_clear_all_zh_tw)) },
            text = { Text(frameTransferString(R.string.frame_transfer_clear_all_confirm,
                R.string.frame_transfer_clear_all_confirm_zh_tw)) },
            confirmButton = {
                Button(
                    onClick = { confirmClear = false; onClear() },
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(frameTransferString(R.string.frame_transfer_clear_all, R.string.frame_transfer_clear_all_zh_tw))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmClear = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.photo_frame_cancel))
                }
            },
        )
    }
    if (confirmClearReceived) {
        AlertDialog(
            onDismissRequest = { confirmClearReceived = false },
            title = { Text(frameTransferString(R.string.tv_photo_pair_clear, R.string.tv_photo_pair_clear_zh_tw)) },
            text = { Text(frameTransferString(R.string.frame_transfer_clear_confirm, R.string.frame_transfer_clear_confirm_zh_tw)) },
            confirmButton = {
                Button(
                    onClick = { confirmClearReceived = false; onClearReceived() },
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(frameTransferString(R.string.tv_photo_pair_clear, R.string.tv_photo_pair_clear_zh_tw))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmClearReceived = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.photo_frame_cancel))
                }
            },
        )
    }
}

@Composable
private fun frameTransferString(english: Int, chinese: Int, vararg args: Any): String {
    val locale = LocalConfiguration.current.locales[0].language
    return stringResource(if (locale == "zh") chinese else english, *args)
}

@Composable
private fun FrameIntervalSlider(
    intervalSeconds: Int,
    enabled: Boolean,
    onIntervalChange: (Int) -> Unit,
) {
    val initialIndex = FrameIntervals.indexOf(intervalSeconds).coerceAtLeast(0)
    var sliderPosition by rememberSaveable(intervalSeconds) { mutableFloatStateOf(initialIndex.toFloat()) }
    val selectedIndex = sliderPosition.roundToInt().coerceIn(FrameIntervals.indices)
    val selectedInterval = FrameIntervals[selectedIndex]
    val label = stringResource(R.string.photo_frame_interval)
    val valueLabel = stringResource(R.string.photo_frame_seconds, selectedInterval)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                if (selectedInterval != intervalSeconds) onIntervalChange(selectedInterval)
            },
            valueRange = 0f..FrameIntervals.lastIndex.toFloat(),
            steps = FrameIntervals.size - 2,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                stateDescription = valueLabel
            },
        )
    }
}

@Composable
private fun FrameSettingSwitch(label: Int, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    FrameSettingSwitch(stringResource(label), checked, enabled, onCheckedChange)
}

@Composable
private fun FrameSettingSwitch(description: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked, onCheckedChange, Modifier.semantics { contentDescription = description }, enabled = enabled)
}

private val FrameIntervals = listOf(5, 10, 15, 30, 60)

internal fun frameErrorMessage(error: FrameError): Int = when (error) {
    FrameError.STORAGE -> R.string.photo_frame_storage_error
    FrameError.PERMISSION -> R.string.photo_frame_permission
    FrameError.UNREADABLE -> R.string.photo_frame_unreadable
    FrameError.INVALID_IMAGE -> R.string.photo_frame_invalid_image
    FrameError.MANIFEST -> R.string.photo_frame_manifest_error
}
