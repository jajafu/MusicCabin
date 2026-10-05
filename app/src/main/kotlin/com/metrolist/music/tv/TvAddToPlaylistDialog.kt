/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.SyncUtils
import kotlinx.coroutines.CoroutineScope

/**
 * D-pad friendly "add to playlist" sheet for TV: pick an editable playlist
 * or create a new local one. The song is inserted locally first so the
 * playlist map always has a row to reference, mirroring the phone dialogs.
 */
@Composable
fun TvAddToPlaylistDialog(
    song: SongItem,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val playlists by database.editablePlaylistsByNameAsc().collectAsStateWithLifecycle(initialValue = emptyList())
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        nameFocus.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.width(560.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = tvLocalizedString(
                        com.metrolist.music.R.string.tv_add_to_playlist,
                        com.metrolist.music.R.string.tv_add_to_playlist_zh_tw,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = {
                            Text(
                                tvLocalizedString(
                                    com.metrolist.music.R.string.tv_playlist_name_hint,
                                    com.metrolist.music.R.string.tv_playlist_name_hint_zh_tw,
                                ),
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(nameFocus),
                    )
                    TvButton(
                        text = tvLocalizedString(
                            com.metrolist.music.R.string.tv_create,
                            com.metrolist.music.R.string.tv_create_zh_tw,
                        ),
                        selected = false,
                        enabled = newName.isNotBlank() && !creating,
                    ) {
                        creating = true
                        tvCreatePlaylistWithSong(scope, database, syncUtils, newName, song) {
                            creating = false
                            onDismiss()
                        }
                    }
                }
                Text(
                    text = tvLocalizedString(
                        com.metrolist.music.R.string.tv_new_playlist,
                        com.metrolist.music.R.string.tv_new_playlist_zh_tw,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        TvPlaylistRow(
                            title = playlist.title,
                            subtitle =
                                playlist.songCount
                                    .takeIf { it > 0 }
                                    ?.let {
                                        tvLocalizedString(
                                            com.metrolist.music.R.string.tv_songs_count,
                                            com.metrolist.music.R.string.tv_songs_count_zh_tw,
                                            it,
                                        )
                                    }.orEmpty(),
                            thumbnailUrl = playlist.thumbnails.firstOrNull(),
                            bookmarked = null,
                            bookmarkLabel = "",
                            onOpen = {
                                tvAddSongToPlaylist(scope, database, syncUtils, song, playlist.id)
                                onDismiss()
                            },
                        )
                    }
                }
                TvButton(
                    text = tvLocalizedString(
                        com.metrolist.music.R.string.tv_back,
                        com.metrolist.music.R.string.tv_back_zh_tw,
                    ),
                    selected = false,
                ) {
                    onDismiss()
                }
            }
        }
    }
}
