package com.metrolist.music.photo.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.Image
import com.metrolist.music.R
import com.metrolist.music.photo.FrameCatalogState
import com.metrolist.music.photo.FrameError
import com.metrolist.music.photo.FrameSelectionType
import com.metrolist.music.photo.FrameSettings
import com.metrolist.music.photo.v2.drive.DriveAuthorizationUi
import com.metrolist.music.photo.v2.drive.DriveProbeController
import com.metrolist.music.photo.v2.drive.DriveProbeState
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoFrameV2SettingsPanel(
    state: FrameCatalogState,
    busy: Boolean,
    error: FrameError?,
    onDismiss: () -> Unit,
    onBrowsePhotos: () -> Unit,
    onRescan: () -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onCancelScan: () -> Unit,
    onSettings: (FrameSettings) -> Unit,
    source: FrameV2Source,
    onSource: (FrameV2Source) -> Unit,
    drive: DriveProbeState<Image>,
    controller: DriveProbeController<Image>,
    authorizationUi: DriveAuthorizationUi,
    slideshowActive: Boolean,
    slideshowPlaying: Boolean,
    onToggleSlideshow: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val enabled = state.initialized && !busy
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrameSettingsIcon(R.drawable.settings)
                Column(Modifier.weight(1f)) {
                    Text(frameV2String(FrameV2Text.Title), style = MaterialTheme.typography.titleLarge)
                    Text(frameV2String(FrameV2Text.SettingsSubtitle), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.close), stringResource(R.string.photo_frame_done))
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    FrameSettingsSectionTitle(frameV2String(FrameV2Text.Source))
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FrameV2Source.entries.forEach { choice ->
                            FrameSourceOption(choice, source == choice, { onSource(choice) }, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
                if (source == FrameV2Source.DRIVE) driveFolderSettings(drive, controller, authorizationUi)
                if (source == FrameV2Source.LOCAL) item {
                    FrameSettingsCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FrameSettingsIcon(R.drawable.insert_photo)
                            Column(Modifier.weight(1f)) {
                                Text(frameV2String(FrameV2Text.LocalPhotos), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.photo_frame_sources, state.photos.size),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(onClick = onBrowsePhotos, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Icon(painterResource(R.drawable.insert_photo), null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.photo_browser_browse_device))
                        }
                        if (state.sources.isEmpty()) Text(stringResource(R.string.photo_frame_empty),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (busy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.photo_frame_scanning, state.scanCount), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = onCancelScan, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.photo_frame_cancel)) }
                            }
                        }
                    }
                }
                if (source == FrameV2Source.LOCAL && error != null) item { FrameSettingsError(stringResource(frameErrorMessage(error))) }
                item {
                    FrameSettingsSectionTitle(stringResource(R.string.photo_frame_display))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FrameSettingsCard {
                            FrameIntervalSlider(state.settings.intervalSeconds, enabled) {
                                onSettings(state.settings.copy(intervalSeconds = it))
                            }
                        }
                        Material3SettingsGroup(
                            useLowContrast = true,
                            items = listOf(
                                Material3SettingsItem(
                                    icon = painterResource(R.drawable.crop),
                                    title = { Text(stringResource(R.string.photo_frame_fill)) },
                                    description = { Text(stringResource(R.string.photo_frame_fill_desc)) },
                                    enabled = enabled,
                                    trailingContent = {
                                        FrameSettingSwitch(R.string.photo_frame_fill, state.settings.crop, enabled) { onSettings(state.settings.copy(crop = it)) }
                                    },
                                ),
                                Material3SettingsItem(
                                    icon = painterResource(R.drawable.timer),
                                    title = { Text(stringResource(R.string.photo_frame_clock)) },
                                    enabled = enabled,
                                    trailingContent = {
                                        FrameSettingSwitch(R.string.photo_frame_clock, state.settings.showClock, enabled) { onSettings(state.settings.copy(showClock = it)) }
                                    },
                                ),
                                Material3SettingsItem(
                                    icon = painterResource(R.drawable.music_note),
                                    title = { Text(stringResource(R.string.photo_frame_song_info)) },
                                    enabled = enabled,
                                    trailingContent = {
                                        FrameSettingSwitch(R.string.photo_frame_song_info, state.settings.showSongInfo, enabled) { onSettings(state.settings.copy(showSongInfo = it)) }
                                    },
                                ),
                            ),
                        )
                    }
                }
                if (source == FrameV2Source.DRIVE && slideshowActive) item {
                    FilledTonalButton(onClick = onToggleSlideshow, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Icon(painterResource(if (slideshowPlaying) R.drawable.pause else R.drawable.play), null, Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(frameV2String(if (slideshowPlaying) FrameV2Text.PauseSlideshow else FrameV2Text.ResumeSlideshow))
                    }
                }
                if (source == FrameV2Source.LOCAL && state.sources.isNotEmpty()) {
                    item {
                        FrameSettingsSectionTitle(frameV2String(FrameV2Text.SelectedPhotos))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(onClick = onRescan, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.photo_frame_rescan))
                            }
                            TextButton(onClick = { confirmClear = true }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                Text(stringResource(R.string.photo_frame_clear))
                            }
                        }
                    }
                    items(state.sources, key = { "${it.type}:${it.uri}" }) { selection ->
                        val status = when {
                            selection.needsPermission -> stringResource(R.string.photo_frame_permission)
                            selection.unavailable -> stringResource(R.string.photo_frame_unreadable)
                            selection.type == FrameSelectionType.FOLDER && !selection.scanned -> stringResource(R.string.photo_frame_not_scanned)
                            selection.unreadableCount > 0 -> stringResource(R.string.photo_frame_failed_photos, selection.unreadableCount, selection.photoCount)
                            selection.photoCount == 0 -> stringResource(R.string.photo_frame_no_images)
                            selection.type == FrameSelectionType.FOLDER -> stringResource(R.string.photo_frame_sources, selection.photoCount)
                            else -> null
                        }
                        ListItem(
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            leadingContent = {
                                Icon(painterResource(if (selection.type == FrameSelectionType.FOLDER) R.drawable.storage else R.drawable.insert_photo), null,
                                    Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            headlineContent = { Text(selection.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge) },
                            supportingContent = status?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
                            trailingContent = {
                                IconButton(onClick = { onRemove(selection.uri) }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                                    Icon(painterResource(R.drawable.close), stringResource(R.string.photo_frame_remove, selection.name), Modifier.size(20.dp))
                                }
                            },
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 56.dp)) {
                Icon(painterResource(R.drawable.check), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.photo_frame_done))
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.photo_frame_clear)) },
            text = { Text(stringResource(R.string.photo_frame_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }, enabled = enabled) { Text(stringResource(R.string.photo_frame_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.photo_frame_cancel)) }
            },
        )
    }
}

@Composable
private fun FrameSourceOption(source: FrameV2Source, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val local = source == FrameV2Source.LOCAL
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(if (local) R.drawable.insert_photo else R.drawable.cloud), null, Modifier.size(28.dp))
                Icon(painterResource(if (selected) R.drawable.radio_button_checked else R.drawable.radio_button_unchecked), null, Modifier.size(20.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(frameV2String(if (local) FrameV2Text.LocalPhotos else FrameV2Text.DriveSource), style = MaterialTheme.typography.titleSmall)
            Text(frameV2String(if (local) FrameV2Text.LocalSourceDetail else FrameV2Text.CloudSourceDetail),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FrameSettingsIcon(R.drawable.timer)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(valueLabel, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
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
    val description = stringResource(label)
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
