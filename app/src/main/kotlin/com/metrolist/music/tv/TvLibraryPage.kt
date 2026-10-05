/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.utils.SyncUtils
import kotlinx.coroutines.CoroutineScope

/**
 * TV Library page: liked songs on top, bookmarked playlists below.
 * Unliking a song or unsaving a playlist removes it from the list
 * automatically because both lists are database flows.
 */
@Composable
fun TvLibraryPage(
    database: MusicDatabase,
    playerConnection: PlayerConnection?,
    scope: CoroutineScope,
    syncUtils: SyncUtils,
    ready: Boolean,
    modifier: Modifier = Modifier,
    onOpenLocalPlaylist: (String) -> Unit,
    onAddToPlaylist: (SongItem) -> Unit,
) {
    val likedSongs by database.likedSongsByCreateDateAsc().collectAsStateWithLifecycle(initialValue = emptyList())
    val savedPlaylists by database.playlistsByCreateDateAsc().collectAsStateWithLifecycle(initialValue = emptyList())
    val likedTitle = tvLocalizedString(R.string.tv_liked_songs, R.string.tv_liked_songs_zh_tw)
    val addLabel = tvLocalizedString(R.string.tv_add_to_playlist, R.string.tv_add_to_playlist_zh_tw)
    val saveLabel = tvLocalizedString(R.string.tv_save, R.string.tv_save_zh_tw)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "liked_heading") {
            TvHeading(likedTitle)
        }
        if (likedSongs.isNotEmpty()) {
            item(key = "liked_play_all") {
                TvButton(
                    text = tvLocalizedString(R.string.tv_play_all, R.string.tv_play_all_zh_tw),
                    selected = false,
                    enabled = ready,
                ) {
                    playerConnection?.playQueue(
                        ListQueue(
                            title = likedTitle,
                            items = likedSongs.map { it.toMediaItem() },
                        ),
                    )
                }
            }
        }
        itemsIndexed(likedSongs, key = { _, song -> "liked_${song.id}" }) { index, song ->
            TvLocalSongRow(
                song = song,
                syncUtils = syncUtils,
                scope = scope,
                database = database,
                enabled = ready,
                addLabel = addLabel,
                onPlay = {
                    playerConnection?.playQueue(
                        ListQueue(
                            title = likedTitle,
                            items = likedSongs.map { it.toMediaItem() },
                            startIndex = index,
                        ),
                    )
                },
                onAddToPlaylist = onAddToPlaylist,
            )
        }
        if (likedSongs.isEmpty()) {
            item(key = "liked_empty") {
                TvMessage(
                    tvLocalizedString(
                        R.string.tv_empty_liked_songs,
                        R.string.tv_empty_liked_songs_zh_tw,
                    ),
                )
            }
        }
        item(key = "saved_heading") {
            TvHeading(
                tvLocalizedString(R.string.tv_saved_playlists, R.string.tv_saved_playlists_zh_tw),
            )
        }
        if (savedPlaylists.isEmpty()) {
            item(key = "saved_empty") {
                TvMessage(
                    tvLocalizedString(
                        R.string.tv_empty_saved_playlists,
                        R.string.tv_empty_saved_playlists_zh_tw,
                    ),
                )
            }
        }
        itemsIndexed(savedPlaylists, key = { _, playlist -> "saved_${playlist.id}" }) { _, playlist ->
            TvPlaylistRow(
                title = playlist.title,
                subtitle =
                    playlist.songCount
                        .takeIf { it > 0 }
                        ?.let {
                            tvLocalizedString(R.string.tv_songs_count, R.string.tv_songs_count_zh_tw, it)
                        }.orEmpty(),
                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                bookmarked = playlist.playlist.bookmarkedAt != null,
                bookmarkLabel = saveLabel,
                onOpen = { onOpenLocalPlaylist(playlist.id) },
                onToggleBookmark = { tvToggleLocalPlaylistBookmark(database, playlist.playlist) },
            )
        }
    }
}
