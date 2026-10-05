/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.SyncUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Which playlist the TV detail page should show. Kept here so the home,
 * library, and detail screens share one navigation contract.
 */
sealed interface TvPlaylistTarget {
    data class Local(val id: String) : TvPlaylistTarget
    data class Online(val browseId: String) : TvPlaylistTarget
}

fun Song.toSongItem(): SongItem =
    SongItem(
        id = song.id,
        title = song.title,
        artists = orderedArtists.map { Artist(name = it.name, id = it.channelId) },
        thumbnail = song.thumbnailUrl.orEmpty(),
        explicit = song.explicit,
        duration = song.duration.takeIf { it >= 0 },
    )

/** Toggle the liked state of an online song, mirroring YouTubeSongMenu. */
fun tvToggleSongLike(
    scope: CoroutineScope,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    song: SongItem,
) {
    scope.launch(Dispatchers.IO) {
        var liked: SongEntity? = null
        database.withTransaction {
            val existing = getSongByIdBlocking(song.id)
            liked =
                if (existing == null) {
                    insert(song.toMediaMetadata(), SongEntity::toggleLike)
                    song.toMediaMetadata().toSongEntity().toggleLike()
                } else {
                    existing.song.toggleLike().also { update(it) }
                }
        }
        liked?.let { syncUtils.likeSong(it) }
    }
}

/**
 * Bookmark or unbookmark an online playlist, mirroring OnlinePlaylistScreen:
 * an entity row is created on first save, afterwards only the bookmark flips.
 */
fun tvToggleOnlinePlaylistBookmark(
    scope: CoroutineScope,
    database: MusicDatabase,
    playlist: PlaylistItem,
    songs: List<SongItem>,
) {
    scope.launch(Dispatchers.IO) {
        val existing = database.playlistByBrowseId(playlist.id).first()
        if (existing != null) {
            database.query {
                update(existing.playlist.toggleLike())
            }
        } else {
            val entity =
                PlaylistEntity(
                    name = playlist.title,
                    browseId = playlist.id,
                    thumbnailUrl = playlist.thumbnail,
                    isEditable = playlist.isEditable,
                    remoteSongCount =
                        playlist.songCountText?.let {
                            Regex("""\d+""").find(it)?.value?.toIntOrNull()
                        },
                    playEndpointParams = playlist.playEndpoint?.params,
                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                    radioEndpointParams = playlist.radioEndpoint?.params,
                ).toggleLike()
            val songMetadata = songs.map { it.toMediaMetadata() }
            database.withTransaction {
                insert(entity)
                songMetadata.onEach { insert(it) }
                val created = database.playlistBlocking(entity.id) ?: return@withTransaction
                database.addSongsToPlaylist(created, songMetadata.map { it.id to it.setVideoId })
            }
        }
    }
}

fun tvToggleLocalPlaylistBookmark(
    database: MusicDatabase,
    playlist: PlaylistEntity,
) {
    database.query {
        update(playlist.toggleLike())
    }
}

fun tvAddSongToPlaylist(
    scope: CoroutineScope,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    song: SongItem,
    playlistId: String,
) {
    scope.launch(Dispatchers.IO) {
        database.insert(song.toMediaMetadata())
        val target = database.playlistBlocking(playlistId) ?: return@launch
        database.addSongsToPlaylist(target, listOf(song.id to song.setVideoId))
        target.playlist.browseId?.let { syncUtils.addToPlaylist(it, target.id, song.id) }
    }
}

fun tvCreatePlaylistWithSong(
    scope: CoroutineScope,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    name: String,
    song: SongItem?,
    onDone: (String?) -> Unit = {},
) {
    scope.launch(Dispatchers.IO) {
        val playlistId =
            runCatching {
                syncUtils.createPlaylist(name.trim(), false).getOrThrow().playlistId
            }.getOrNull()
        if (playlistId != null && song != null) {
            database.insert(song.toMediaMetadata())
            val target = database.playlistBlocking(playlistId)
            if (target != null) {
                database.addSongsToPlaylist(target, listOf(song.id to song.setVideoId))
            }
        }
        withContext(Dispatchers.Main) {
            onDone(playlistId)
        }
    }
}

/** Remove one song from a local playlist, mirroring LocalPlaylistScreen. */
fun tvRemoveSongFromPlaylist(
    database: MusicDatabase,
    syncUtils: SyncUtils,
    playlistId: String,
    browseId: String?,
    map: PlaylistSongMap,
) {
    database.transaction {
        move(playlistId, map.position, Int.MAX_VALUE)
        delete(map.copy(position = Int.MAX_VALUE))
    }
    if (browseId != null) {
        syncUtils.scheduleRemoveFromPlaylist(browseId, map.songId, playlistId, map.setVideoId)
    }
}

fun tvDeletePlaylist(
    scope: CoroutineScope,
    database: MusicDatabase,
    playlist: PlaylistEntity,
    onDone: () -> Unit,
) {
    database.query {
        delete(playlist)
    }
    scope.launch(Dispatchers.IO) {
        playlist.browseId?.let { runCatching { YouTube.deletePlaylist(it) } }
        withContext(Dispatchers.Main) {
            onDone()
        }
    }
}

@Composable
internal fun TvRowIconButton(
    icon: Int,
    description: String?,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp).onFocusChanged { focused = it.isFocused },
        shape = CircleShape,
        color = if (active || focused) colors.primary else colors.surfaceVariant.copy(alpha = 0.4f),
        border = if (focused) BorderStroke(2.dp, colors.onSurface) else null,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                tint = if (active || focused) colors.onPrimary else colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * A song row with D-pad friendly actions: tap the row to play, heart to
 * like/unlike, plus to add to a playlist, optional cross to remove.
 */
@Composable
internal fun TvOnlineSongRow(
    song: SongItem,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    scope: CoroutineScope,
    enabled: Boolean,
    addLabel: String,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val dbSong by database.song(song.id).collectAsStateWithLifecycle(initialValue = null)
    val liked = dbSong?.song?.liked == true
    TvSongRow(
        title = song.title,
        artists = song.artists.joinToString { it.name },
        thumbnailUrl = song.thumbnail.resize(160, 160),
        enabled = enabled,
        onClick = onPlay,
    ) {
        TvRowIconButton(
            icon = if (liked) R.drawable.favorite else R.drawable.favorite_border,
            description = null,
            active = liked,
        ) {
            tvToggleSongLike(scope, database, syncUtils, song)
        }
        TvRowIconButton(icon = R.drawable.playlist_add, description = addLabel) {
            onAddToPlaylist()
        }
        if (onRemove != null) {
            TvRowIconButton(icon = R.drawable.remove, description = null) {
                onRemove()
            }
        }
    }
}

@Composable
internal fun TvLocalSongRow(
    song: Song,
    syncUtils: SyncUtils,
    scope: CoroutineScope,
    database: MusicDatabase,
    enabled: Boolean,
    addLabel: String,
    onPlay: () -> Unit,
    onAddToPlaylist: (SongItem) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val liked = song.song.liked
    TvSongRow(
        title = song.title,
        artists = song.orderedArtists.joinToString { it.name },
        thumbnailUrl = song.thumbnailUrl?.resize(160, 160),
        enabled = enabled,
        onClick = onPlay,
    ) {
        TvRowIconButton(
            icon = if (liked) R.drawable.favorite else R.drawable.favorite_border,
            description = null,
            active = liked,
        ) {
            tvToggleSongLike(scope, database, syncUtils, song.toSongItem())
        }
        TvRowIconButton(icon = R.drawable.playlist_add, description = addLabel) {
            onAddToPlaylist(song.toSongItem())
        }
        if (onRemove != null) {
            TvRowIconButton(icon = R.drawable.remove, description = null) {
                onRemove()
            }
        }
    }
}

@Composable
internal fun TvPlaylistRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    bookmarked: Boolean?,
    bookmarkLabel: String,
    onOpen: () -> Unit,
    onToggleBookmark: (() -> Unit)? = null,
) {
    TvSongRow(
        title = title,
        artists = subtitle,
        thumbnailUrl = thumbnailUrl?.resize(160, 160),
        enabled = true,
        onClick = onOpen,
    ) {
        if (bookmarked != null && onToggleBookmark != null) {
            TvRowIconButton(
                icon = if (bookmarked) R.drawable.favorite else R.drawable.favorite_border,
                description = bookmarkLabel,
                active = bookmarked,
            ) {
                onToggleBookmark()
            }
        }
    }
}

/** Home section playlist row: bookmark state comes from the library. */
@Composable
internal fun TvHomePlaylistRow(
    playlist: PlaylistItem,
    database: MusicDatabase,
    scope: CoroutineScope,
    saveLabel: String,
    onOpen: () -> Unit,
) {
    val dbPlaylist by database.playlistByBrowseId(playlist.id).collectAsStateWithLifecycle(initialValue = null)
    val bookmarked = dbPlaylist?.playlist?.bookmarkedAt != null
    TvPlaylistRow(
        title = playlist.title,
        subtitle = listOfNotNull(playlist.author?.name, playlist.songCountText).joinToString(" · "),
        thumbnailUrl = playlist.thumbnail,
        bookmarked = bookmarked,
        bookmarkLabel = saveLabel,
        onOpen = onOpen,
        onToggleBookmark = { tvToggleOnlinePlaylistBookmark(scope, database, playlist, emptyList()) },
    )
}
