/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.PlaylistPage
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubePlaylistQueue
import com.metrolist.music.utils.SyncUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TV playlist detail page. Local playlists show their stored songs with
 * remove support; online playlists fetch one page and can be saved to the
 * library. Deleting only appears for editable local playlists.
 */
@Composable
fun TvPlaylistDetailPage(
    target: TvPlaylistTarget,
    database: MusicDatabase,
    playerConnection: PlayerConnection?,
    scope: CoroutineScope,
    syncUtils: SyncUtils,
    ready: Boolean,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (SongItem) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    when (target) {
        is TvPlaylistTarget.Local ->
            TvLocalPlaylistDetail(
                playlistId = target.id,
                database = database,
                playerConnection = playerConnection,
                scope = scope,
                syncUtils = syncUtils,
                ready = ready,
                modifier = modifier,
                onAddToPlaylist = onAddToPlaylist,
                onDeleted = onDeleted,
                onBack = onBack,
            )

        is TvPlaylistTarget.Online ->
            TvOnlinePlaylistDetail(
                browseId = target.browseId,
                database = database,
                playerConnection = playerConnection,
                scope = scope,
                syncUtils = syncUtils,
                ready = ready,
                modifier = modifier,
                onAddToPlaylist = onAddToPlaylist,
                onBack = onBack,
            )
    }
}

@Composable
private fun TvLocalPlaylistDetail(
    playlistId: String,
    database: MusicDatabase,
    playerConnection: PlayerConnection?,
    scope: CoroutineScope,
    syncUtils: SyncUtils,
    ready: Boolean,
    modifier: Modifier,
    onAddToPlaylist: (SongItem) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val playlist by database.playlist(playlistId).collectAsStateWithLifecycle(initialValue = null)
    val entries by database.playlistSongs(playlistId).collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    val addLabel = tvLocalizedString(R.string.tv_add_to_playlist, R.string.tv_add_to_playlist_zh_tw)
    val saveLabel = tvLocalizedString(R.string.tv_save, R.string.tv_save_zh_tw)
    val savedLabel = tvLocalizedString(R.string.tv_saved, R.string.tv_saved_zh_tw)

    val entity = playlist?.playlist
    if (entity == null) {
        TvMessage(tvLocalizedString(R.string.tv_loading, R.string.tv_loading))
        return
    }
    val editable = entity.isEditable
    val bookmarked = entity.bookmarkedAt != null

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "detail_header") {
            TvDetailHeader(
                title = entity.name,
                subtitle =
                    playlist
                        ?.songCount
                        ?.takeIf { it > 0 }
                        ?.let { tvLocalizedString(R.string.tv_songs_count, R.string.tv_songs_count_zh_tw, it) }
                        .orEmpty(),
                thumbnailUrl = playlist?.thumbnails?.firstOrNull(),
                primaryLabel = tvLocalizedString(R.string.tv_play_all, R.string.tv_play_all_zh_tw),
                primaryEnabled = ready && entries.isNotEmpty(),
                onPrimary = {
                    playerConnection?.playQueue(
                        ListQueue(
                            title = entity.name,
                            items = entries.map { it.song.toMediaItem() },
                        ),
                    )
                },
                bookmarkLabel = if (bookmarked) savedLabel else saveLabel,
                bookmarked = bookmarked,
                onBookmark = { tvToggleLocalPlaylistBookmark(database, entity) },
                showDelete = editable,
                confirmDelete = confirmDelete,
                onDeleteClick = { confirmDelete = true },
                onDeleteConfirm = { tvDeletePlaylist(scope, database, entity) { onDeleted() } },
                onDeleteCancel = { confirmDelete = false },
                onBack = onBack,
            )
        }
        itemsIndexed(entries, key = { _, entry -> "local_${entry.map.songId}_${entry.map.position}" }) { index, entry ->
            TvLocalSongRow(
                song = entry.song,
                syncUtils = syncUtils,
                scope = scope,
                database = database,
                enabled = ready,
                addLabel = addLabel,
                onPlay = {
                    playerConnection?.playQueue(
                        ListQueue(
                            title = entity.name,
                            items = entries.map { it.song.toMediaItem() },
                            startIndex = index,
                        ),
                    )
                },
                onAddToPlaylist = onAddToPlaylist,
                onRemove =
                    if (editable) {
                        {
                            tvRemoveSongFromPlaylist(
                                database,
                                syncUtils,
                                playlistId,
                                entity.browseId,
                                entry.map,
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun TvOnlinePlaylistDetail(
    browseId: String,
    database: MusicDatabase,
    playerConnection: PlayerConnection?,
    scope: CoroutineScope,
    syncUtils: SyncUtils,
    ready: Boolean,
    modifier: Modifier,
    onAddToPlaylist: (SongItem) -> Unit,
    onBack: () -> Unit,
) {
    var page by remember(browseId) { mutableStateOf<PlaylistPage?>(null) }
    var loading by remember(browseId) { mutableStateOf(true) }
    var failed by remember(browseId) { mutableStateOf(false) }
    var reload by remember(browseId) { mutableStateOf(0) }
    val dbPlaylist by database.playlistByBrowseId(browseId).collectAsStateWithLifecycle(initialValue = null)
    val bookmarked = dbPlaylist?.playlist?.bookmarkedAt != null
    val addLabel = tvLocalizedString(R.string.tv_add_to_playlist, R.string.tv_add_to_playlist_zh_tw)
    val saveLabel = tvLocalizedString(R.string.tv_save, R.string.tv_save_zh_tw)
    val savedLabel = tvLocalizedString(R.string.tv_saved, R.string.tv_saved_zh_tw)

    LaunchedEffect(browseId, reload) {
        // Only the latest request may publish: a cancelled load (e.g. fast
        // playlist switching) must not report a stale failure or clear the
        // loading state of its replacement.
        val activeBrowseId = browseId
        val activeReload = reload
        fun isCurrent() = activeBrowseId == browseId && activeReload == reload
        loading = true
        failed = false
        try {
            page = withContext(Dispatchers.IO) {
                YouTube.playlist(browseId).getOrThrow()
            }
            if (!isCurrent()) return@LaunchedEffect
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (!isCurrent()) return@LaunchedEffect
            failed = true
        } finally {
            if (isCurrent()) loading = false
        }
    }

    val loaded = page
    if (loaded == null) {
        if (failed) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvMessage(tvLocalizedString(R.string.tv_loading, R.string.tv_loading))
                TvButton(
                    text = tvLocalizedString(R.string.retry, R.string.retry),
                    selected = false,
                ) {
                    reload++
                }
            }
        } else {
            TvMessage(tvLocalizedString(R.string.tv_loading, R.string.tv_loading))
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "detail_header") {
            TvDetailHeader(
                title = loaded.playlist.title,
                subtitle = listOfNotNull(loaded.playlist.author?.name, loaded.playlist.songCountText).joinToString(" · "),
                thumbnailUrl = loaded.playlist.thumbnail,
                primaryLabel = tvLocalizedString(R.string.tv_play_all, R.string.tv_play_all_zh_tw),
                primaryEnabled = ready && loaded.songs.isNotEmpty(),
                onPrimary = {
                    playerConnection?.playQueue(
                        YouTubePlaylistQueue(
                            playlistId = browseId,
                            playlistTitle = loaded.playlist.title,
                            initialSongs = loaded.songs,
                            initialContinuation = loaded.songsContinuation,
                        ),
                    )
                },
                bookmarkLabel = if (bookmarked) savedLabel else saveLabel,
                bookmarked = bookmarked,
                onBookmark = { tvToggleOnlinePlaylistBookmark(scope, database, loaded.playlist, loaded.songs) },
                showDelete = false,
                confirmDelete = false,
                onDeleteClick = {},
                onDeleteConfirm = {},
                onDeleteCancel = {},
                onBack = onBack,
            )
        }
        itemsIndexed(loaded.songs, key = { _, song -> "online_${song.id}" }) { index, song ->
            TvOnlineSongRow(
                song = song,
                database = database,
                syncUtils = syncUtils,
                scope = scope,
                enabled = ready,
                addLabel = addLabel,
                onPlay = {
                    playerConnection?.playQueue(
                        YouTubePlaylistQueue(
                            playlistId = browseId,
                            playlistTitle = loaded.playlist.title,
                            initialSongs = loaded.songs,
                            initialContinuation = loaded.songsContinuation,
                            startIndex = index,
                        ),
                    )
                },
                onAddToPlaylist = { onAddToPlaylist(song) },
            )
        }
    }
}

@Composable
private fun TvDetailHeader(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    bookmarkLabel: String,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    showDelete: Boolean,
    confirmDelete: Boolean,
    onDeleteClick: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvArtwork(thumbnailUrl, 120.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvButton(text = primaryLabel, selected = false, enabled = primaryEnabled) {
                onPrimary()
            }
            TvButton(text = bookmarkLabel, selected = bookmarked) {
                onBookmark()
            }
            if (showDelete && !confirmDelete) {
                TvButton(
                    text = tvLocalizedString(R.string.tv_delete_playlist, R.string.tv_delete_playlist_zh_tw),
                    selected = false,
                ) {
                    onDeleteClick()
                }
            }
            TvButton(
                text = tvLocalizedString(R.string.tv_back, R.string.tv_back_zh_tw),
                selected = false,
            ) {
                onBack()
            }
        }
        if (showDelete && confirmDelete) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tvLocalizedString(
                        R.string.tv_delete_playlist_confirm,
                        R.string.tv_delete_playlist_confirm_zh_tw,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                TvButton(
                    text = tvLocalizedString(R.string.tv_delete_playlist, R.string.tv_delete_playlist_zh_tw),
                    selected = true,
                ) {
                    onDeleteConfirm()
                }
                TvButton(
                    text = tvLocalizedString(R.string.tv_back, R.string.tv_back_zh_tw),
                    selected = false,
                ) {
                    onDeleteCancel()
                }
            }
        }
    }
}
