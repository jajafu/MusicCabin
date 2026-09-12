package com.metrolist.music.photo.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.Image
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import com.metrolist.music.photo.v2.drive.*

internal fun LazyListScope.driveFolderSettings(
    state: DriveProbeState<Image>,
    controller: DriveProbeController<Image>,
    ui: DriveAuthorizationUi,
) {
    item {
        FrameSettingsCard {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FrameSettingsIcon(R.drawable.cloud)
                Column(Modifier.weight(1f)) {
                    Text(frameV2String(FrameV2Text.DriveSource), style = MaterialTheme.typography.titleMedium)
                    Text(frameV2String(if (state.connected) FrameV2Text.Connected else FrameV2Text.NotConnected),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = controller::setEnabled,
                    enabled = state.initialized && BuildConfig.DRIVE_OAUTH_AVAILABLE,
                    modifier = Modifier.semanticsForFrameToggle(frameV2String(FrameV2Text.Enable)),
                )
            }
            state.account?.let {
                Text(it.emailAddress, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!BuildConfig.DRIVE_OAUTH_AVAILABLE) Text(frameV2String(FrameV2Text.BuildDisabled), style = MaterialTheme.typography.bodySmall)
            if (!state.connected) {
                Text(frameV2String(FrameV2Text.Permission), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { controller.connect(ui) },
                    enabled = state.initialized && state.enabled && !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(frameV2String(FrameV2Text.Connect)) }
            }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(frameV2String(if (state.awaitingConsent) FrameV2Text.Authorizing else FrameV2Text.Preparing),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = controller::stop, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(frameV2String(FrameV2Text.Cancel))
                    }
                }
            }
            if (state.account != null) {
                TextButton(onClick = controller::disconnect, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                    Text(frameV2String(FrameV2Text.Disconnect))
                }
            }
        }
    }
    state.error?.let { error -> item { FrameSettingsError(frameV2String(error.failure.text())) } }
    if (state.connected) {
        item {
            FrameSettingsSectionTitle(frameV2String(FrameV2Text.FolderSelection))
            FrameSettingsCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FrameSettingsIcon(R.drawable.storage)
                    Column(Modifier.weight(1f)) {
                        Text(state.path.lastOrNull()?.name ?: frameV2String(FrameV2Text.Root),
                            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (state.path.isNotEmpty()) Text(
                            (listOf(frameV2String(FrameV2Text.Root)) + state.path.map { it.name }).joinToString(" / "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = controller::parentFolder, enabled = !state.busy && state.path.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(frameV2String(FrameV2Text.Parent))
                    }
                    OutlinedButton(onClick = controller::refreshFolders, enabled = !state.busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(frameV2String(FrameV2Text.RefreshFolders))
                    }
                }
                Button(onClick = controller::useFolder, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(painterResource(R.drawable.play), null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(frameV2String(FrameV2Text.ListPhotos))
                }
            }
        }
        items(state.folders, key = { "drive:" + it.id }) { folder ->
            ListItem(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).alpha(if (state.busy) 0.38f else 1f)
                    .clickable(enabled = !state.busy, role = Role.Button) { controller.openFolder(folder) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                leadingContent = {
                    Icon(painterResource(R.drawable.storage), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                },
                headlineContent = { Text(folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                trailingContent = { Icon(painterResource(R.drawable.arrow_forward), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
        if (state.folders.isEmpty() && !state.busy) item {
            Text(frameV2String(FrameV2Text.NoFolders), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item {
        FrameSettingsNote(frameV2String(FrameV2Text.DriveDetails),
            listOf(frameV2String(FrameV2Text.Permission), frameV2String(FrameV2Text.DisconnectHint)).joinToString("\n\n"))
    }
    item { FrameSettingsNote(frameV2String(FrameV2Text.FormatAndCache), frameV2String(FrameV2Text.PreviewLimit)) }
}
