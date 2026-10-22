/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.YouTubeConstants
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.music.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.MediaSessionConstants
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.extensions.toggleRepeatMode
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.getArtistSeparator
import com.metrolist.music.utils.joinToArtistString
import com.metrolist.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import javax.inject.Inject
import com.metrolist.music.constants.AndroidAutoSectionsOrderKey
import com.metrolist.music.constants.AndroidAutoYouTubePlaylistsKey
import com.metrolist.music.ui.screens.settings.AndroidAutoSection
import com.metrolist.music.ui.screens.settings.deserializeSections
import com.metrolist.music.ui.screens.settings.serializeSections

internal fun isManualSkipCommand(command: Int): Boolean =
    command == COMMAND_SEEK_TO_NEXT ||
        command == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
        command == COMMAND_SEEK_TO_PREVIOUS ||
        command == COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM

internal fun Player.Commands.containsManualSkipCommand(): Boolean =
    (0 until size()).any { index -> isManualSkipCommand(get(index)) }

/**
 * Computes the volume a SET_VOLUME request should write for [mode] given the
 * [current] value and the [requested] target, rounded to percent precision so
 * repeated voice steps do not drift through float error (0.30000004-style
 * values) and clamped to 0..1. Returns null for a state query (or an unknown
 * mode), which must never write anything. Pure and internal so the boundary
 * behavior is unit-testable without a running session.
 */
internal fun requestedPlayerVolume(
    mode: String,
    current: Float,
    requested: Float,
): Float? = when (mode) {
    MediaSessionConstants.VOLUME_MODE_SET -> quantizedVolume(requested)
    MediaSessionConstants.VOLUME_MODE_UP -> quantizedVolume(current + MediaSessionConstants.VOLUME_STEP)
    MediaSessionConstants.VOLUME_MODE_DOWN -> quantizedVolume(current - MediaSessionConstants.VOLUME_STEP)
    else -> null
}

private fun quantizedVolume(volume: Float): Float =
    (volume * 100).roundToInt().coerceIn(0, 100) / 100f

class MediaLibrarySessionCallback
@Inject
constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val downloadUtil: DownloadUtil,
) : MediaLibrarySession.Callback {
    private val scope = CoroutineScope(Dispatchers.Main) + Job()
    private val carLibrary = AndroidAutoLibrary(database) {
        downloadUtil.downloads.value.values
            .filter { it.state == Download.STATE_COMPLETED }
            .sortedBy { it.updateTimeMs }
            .map { it.request.id }
    }
    lateinit var service: MusicService
    var toggleLike: () -> Unit = {}
    var toggleStartRadio: () -> Unit = {}
    var toggleLibrary: () -> Unit = {}
    var addToTargetPlaylist: () -> Unit = {}

    fun release() {
        scope.cancel()
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        if (!connectionResult.isAccepted ||
            !MediaControllerCommandPolicy.canUseCustomCommands(controller.isTrusted)
        ) {
            return connectionResult
        }

        return MediaSession.ConnectionResult.accept(
            connectionResult.availableSessionCommands
                .buildUpon()
                .add(MediaSessionConstants.CommandToggleLike)
                .add(MediaSessionConstants.CommandToggleStartRadio)
                .add(MediaSessionConstants.CommandToggleLibrary)
                .add(MediaSessionConstants.CommandToggleShuffle)
                .add(MediaSessionConstants.CommandToggleRepeatMode)
                .add(MediaSessionConstants.CommandAddToTargetPlaylist)
                .add(MediaSessionConstants.CommandSetVolume)
                .build(),
            connectionResult.availablePlayerCommands,
        )
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (!MediaControllerCommandPolicy.canUseCustomCommands(controller.isTrusted)) {
            return Futures.immediateFuture(
                SessionResult(SessionError.ERROR_PERMISSION_DENIED),
            )
        }

        return when (customCommand.customAction) {
            MediaSessionConstants.ACTION_TOGGLE_LIKE -> successfulCustomCommand(toggleLike)
            MediaSessionConstants.ACTION_TOGGLE_START_RADIO -> successfulCustomCommand(toggleStartRadio)
            MediaSessionConstants.ACTION_TOGGLE_LIBRARY -> successfulCustomCommand(toggleLibrary)
            MediaSessionConstants.ACTION_TOGGLE_SHUFFLE ->
                successfulCustomCommand {
                    session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
                }

            MediaSessionConstants.ACTION_TOGGLE_REPEAT_MODE ->
                successfulCustomCommand(session.player::toggleRepeatMode)

            MediaSessionConstants.ACTION_ADD_TO_TARGET_PLAYLIST ->
                successfulCustomCommand(addToTargetPlaylist)

            MediaSessionConstants.ACTION_SET_VOLUME -> {
                val newVolume = applySetVolume(args)
                Futures.immediateFuture(
                    SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply { putFloat(MediaSessionConstants.EXTRA_VOLUME, newVolume) },
                    ),
                )
            }

            else -> super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onPlayerInteractionFinished(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        playerCommands: Player.Commands,
    ) {
        if (playerCommands.containsManualSkipCommand()) {
            service.resumePlaybackAfterManualSkip()
        }
    }

    private fun successfulCustomCommand(action: () -> Unit): ListenableFuture<SessionResult> {
        action()
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /**
     * Applies a volume request (voice assistant, etc.) to the app's own
     * player volume — not the device volume. Sleep timer and audio focus
     * keep multiplying on top, so their behavior is unchanged. The new
     * volume is returned in the result so callers can report the level.
     * A state query returns the current value untouched and never writes.
     */
    private fun applySetVolume(args: Bundle): Float {
        val current = service.playerVolume.value
        val mode = args.getString(MediaSessionConstants.EXTRA_VOLUME_MODE, MediaSessionConstants.VOLUME_MODE_GET)
        if (mode == MediaSessionConstants.VOLUME_MODE_GET) return current
        val newVolume = requestedPlayerVolume(mode, current, args.getFloat(MediaSessionConstants.EXTRA_VOLUME, current))
            ?: return current
        if (newVolume != current) {
            service.playerVolume.value = newVolume
        }
        return newVolume
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaItemsWithStartPosition> =
        Futures.immediateFuture(
            MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET),
        )

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(
            LibraryResult.ofItem(
                MediaItem
                    .Builder()
                    .setMediaId(MusicService.ROOT)
                    .setMediaMetadata(
                        MediaMetadata
                            .Builder()
                            .setIsPlayable(false)
                            .setIsBrowsable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build(),
                    ).build(),
                params,
            ),
        )

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        scope.future(Dispatchers.IO) {
            val request = CarPage.from(parentId, page, pageSize)
                ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            val sourceId = request.parentId
            val offset = request.offset
            val limit = request.size + 1
            try {
                val items = when (sourceId) {
                    MusicService.ROOT -> {
                        val sectionsRaw = context.dataStore.get(
                            AndroidAutoSectionsOrderKey,
                            serializeSections(AndroidAutoSection.values().map { it to true })
                        )
                        val sections = deserializeSections(sectionsRaw)
                        val showYoutubePlaylists = context.dataStore.get(AndroidAutoYouTubePlaylistsKey, false)
                        val rootItems = sections
                            .filter { (_, enabled) -> enabled }
                            .ifEmpty { listOf(AndroidAutoSection.LIKED to true) }
                            .map { (section, _) ->
                                when (section) {
                                    AndroidAutoSection.LIKED -> browsableMediaItem(
                                        "${MusicService.PLAYLIST}/${PlaylistEntity.LIKED_PLAYLIST_ID}",
                                        context.getString(R.string.liked_songs),
                                        null,
                                        drawableUri(R.drawable.favorite),
                                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                                    )
                                    AndroidAutoSection.SONGS -> browsableMediaItem(
                                        MusicService.SONG,
                                        context.getString(R.string.songs),
                                        null,
                                        drawableUri(R.drawable.music_note),
                                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                                    )
                                    AndroidAutoSection.ARTISTS -> browsableMediaItem(
                                        MusicService.ARTIST,
                                        context.getString(R.string.artists),
                                        null,
                                        drawableUri(R.drawable.artist),
                                        MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                                    )
                                    AndroidAutoSection.ALBUMS -> browsableMediaItem(
                                        MusicService.ALBUM,
                                        context.getString(R.string.albums),
                                        null,
                                        drawableUri(R.drawable.album),
                                        MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                                    )
                                    AndroidAutoSection.PLAYLISTS -> browsableMediaItem(
                                        MusicService.PLAYLIST,
                                        context.getString(R.string.playlists),
                                        null,
                                        drawableUri(R.drawable.queue_music),
                                        MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                                    )
                                }
                            }
                        if (showYoutubePlaylists) {
                            (rootItems + browsableMediaItem(
                                MusicService.YOUTUBE_PLAYLIST,
                                context.getString(R.string.mixes),
                                null,
                                drawableUri(R.drawable.explore_outlined),
                                MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                            )).slicePage(offset, limit)
                        } else {
                            rootItems.slicePage(offset, limit)
                        }
                    }


                    MusicService.SONG -> carLibrary.songs(carLibrary.songIds(sourceId, limit, offset))
                        .map { it.toMediaItem(sourceId) }

                    MusicService.ARTIST ->
                        database.androidAutoArtists(limit, offset).map { artist ->
                            browsableMediaItem(
                                "${MusicService.ARTIST}/${artist.id}",
                                artist.artist.name,
                                context.resources.getQuantityString(
                                    R.plurals.n_song,
                                    artist.songCount,
                                    artist.songCount
                                ),
                                artist.artist.thumbnailUrl?.toUri(),
                                MediaMetadata.MEDIA_TYPE_ARTIST,
                            )
                        }

                    MusicService.ALBUM ->
                        database.androidAutoAlbums(limit, offset).map { album ->
                            browsableMediaItem(
                                "${MusicService.ALBUM}/${album.id}",
                                album.album.title,
                                album.artists.joinToString {
                                    it.name
                                },
                                album.album.thumbnailUrl?.toUri(),
                                MediaMetadata.MEDIA_TYPE_ALBUM,
                            )
                        }

                    MusicService.PLAYLIST -> {
                        val likedSongCount = database.likedSongsCount().first()
                        val downloadedSongCount = downloadUtil.downloads.value.values.count { it.state == Download.STATE_COMPLETED }

                        val prefix = listOf(
                            browsableMediaItem(
                                "${MusicService.PLAYLIST}/${PlaylistEntity.LIKED_PLAYLIST_ID}",
                                context.getString(R.string.liked_songs),
                                context.resources.getQuantityString(R.plurals.n_song, likedSongCount, likedSongCount),
                                drawableUri(R.drawable.favorite),
                                MediaMetadata.MEDIA_TYPE_PLAYLIST,
                            ),
                            browsableMediaItem(
                                "${MusicService.PLAYLIST}/${PlaylistEntity.DOWNLOADED_PLAYLIST_ID}",
                                context.getString(R.string.downloaded_songs),
                                context.resources.getQuantityString(R.plurals.n_song, downloadedSongCount, downloadedSongCount),
                                drawableUri(R.drawable.download),
                                MediaMetadata.MEDIA_TYPE_PLAYLIST,
                            ),
                        )
                        loadCarPage(prefix, offset, limit) { count, start ->
                            database.androidAutoPlaylists(count, start).map { playlist ->
                                browsableMediaItem(
                                    "${MusicService.PLAYLIST}/${playlist.id}",
                                    playlist.playlist.name,
                                    context.resources.getQuantityString(R.plurals.n_song, playlist.songCount, playlist.songCount),
                                    playlist.thumbnails.firstOrNull()?.toUri(),
                                    MediaMetadata.MEDIA_TYPE_PLAYLIST,
                                )
                            }
                        }
                    }

                    MusicService.YOUTUBE_PLAYLIST -> {
                        if (!context.dataStore.get(AndroidAutoYouTubePlaylistsKey, false)) {
                            emptyList()
                        } else {
                            try {
                                val allSections = mutableListOf<com.metrolist.innertube.pages.HomePage.Section>()
                                var continuation: String? = null
                                val maxPages = 4

                                for (page in 0 until maxPages) {
                                    val result = YouTube.home(continuation)
                                        .onFailure { reportException(it) }
                                        .getOrNull() ?: break
                                    allSections.addAll(result.sections)
                                    continuation = result.continuation
                                    if (continuation == null) break
                                }

                                // Drop playlists already saved to the local library,
                                // which are exposed under MusicService.PLAYLIST.
                                val savedBrowseIds = database.androidAutoSavedBrowseIds().toSet()

                                val playlists = allSections
                                    .flatMap { it.items }
                                    .filterIsInstance<PlaylistItem>()
                                    .filterNot { it.id in savedBrowseIds }
                                    .distinctBy { it.id }

                                playlists.slicePage(offset, limit).map { playlist ->
                                    browsableMediaItem(
                                        "${MusicService.YOUTUBE_PLAYLIST}/${playlist.id}",
                                        playlist.title,
                                        playlist.author?.name,
                                        playlist.thumbnail?.toUri(),
                                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                                    )
                                }
                            } catch (e: Exception) {
                                reportException(e)
                                emptyList()
                            }
                        }
                    }

                    else ->
                        when {
                            sourceId.startsWith("${MusicService.ARTIST}/") ||
                                sourceId.startsWith("${MusicService.ALBUM}/") ->
                                carLibrary.songs(carLibrary.songIds(sourceId, limit, offset))
                                    .map { it.toMediaItem(sourceId) }

                            sourceId.startsWith("${MusicService.PLAYLIST}/") ->
                                loadCarPage(listOf(shuffleItem(sourceId)), offset, limit) { count, start ->
                                    carLibrary.songs(carLibrary.songIds(sourceId, count, start))
                                        .map { it.toMediaItem(sourceId) }
                                }

                            sourceId.startsWith("${MusicService.YOUTUBE_PLAYLIST}/") -> {
                                val playlistId = sourceId.removePrefix("${MusicService.YOUTUBE_PLAYLIST}/")
                                try {
                                    val songs = YouTube.playlist(playlistId).getOrNull()?.songs
                                        ?.take(100)
                                        ?.filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                        ?.filterVideoSongs(context.dataStore.get(HideVideoSongsKey, false))
                                        ?: emptyList()

                                    // Add shuffle item at the top
                                    (listOf(shuffleItem(sourceId)) + songs.map { songItem ->
                                        MediaItem.Builder()
                                            .setMediaId("$sourceId/${songItem.id}")
                                            .setMediaMetadata(
                                                MediaMetadata.Builder()
                                                    .setTitle(songItem.title)
                                                    .setSubtitle(songItem.artists.joinToArtistString(getArtistSeparator(context)) { it.name })
                                                    .setArtist(songItem.artists.joinToArtistString(getArtistSeparator(context)) { it.name })
                                                    .setArtworkUri(songItem.thumbnail.toUri())
                                                    .setIsPlayable(true)
                                                    .setIsBrowsable(false)
                                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                                    .build()
                                            )
                                            .build()
                                    }).slicePage(offset, limit)
                                } catch (e: Exception) {
                                    reportException(e)
                                    emptyList()
                                }
                            }

                            else -> emptyList()
                        }
                }
                val result = if (request.showMore && items.size > request.size) {
                    items.take(request.size - 1) + browsableMediaItem(
                        request.nextId(request.size - 1),
                        context.getString(R.string.more_content),
                        null,
                        drawableUri(R.drawable.queue_music),
                        MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                    )
                } else items.take(request.size)
                LibraryResult.ofItemList(withCarArtwork(result, browser), params)
            } catch (e: Exception) {
                reportException(e)
                LibraryResult.ofItemList(emptyList(), params)
            }
        }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        scope.future(Dispatchers.IO) {
            try {
                database.song(mediaId).first()?.toMediaItem()?.let {
                    LibraryResult.ofItem(withCarArtwork(listOf(it), browser).first(), null)
                } ?: LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            } catch (e: Exception) {
                reportException(e)
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        session.notifySearchResultChanged(browser, query, 1, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return scope.future(Dispatchers.IO) {
            if (query.isEmpty()) {
                return@future LibraryResult.ofItemList(emptyList(), params)
            }

            try {
                val searchResults = mutableListOf<MediaItem>()

                val allLocalSongs = carLibrary.search(query)

                allLocalSongs.forEach { song ->
                    searchResults.add(song.toMediaItem(
                        path = "${MusicService.SEARCH}/$query",
                        isPlayable = true,
                        isBrowsable = true
                    ))
                }

                try {
                    val onlineResults = searchOnlineSongs(query)
                        .filter { onlineSong ->
                            !allLocalSongs.any { localSong ->
                                localSong.id == onlineSong.id ||
                                (localSong.song.title.equals(onlineSong.title, ignoreCase = true) &&
                                 localSong.artists.any { artist ->
                                     onlineSong.artists.any {
                                         it.name.equals(artist.name, ignoreCase = true)
                                     }
                                 })
                            }
                        }

                    onlineResults.forEach { songItem ->
                        try {
                            database.query { insert(songItem.toMediaMetadata()) }
                        } catch (e: Exception) {
                        }

                        searchResults.add(
                            MediaItem.Builder()
                                .setMediaId("${MusicService.SEARCH}/$query/${songItem.id}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(songItem.title)
                                        .setSubtitle(songItem.artists.joinToArtistString(getArtistSeparator(context)) { it.name })
                                        .setArtist(songItem.artists.joinToArtistString(getArtistSeparator(context)) { it.name })
                                        .setArtworkUri(songItem.thumbnail.toUri())
                                        .setIsPlayable(true)
                                        .setIsBrowsable(true)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                        .build()
                                )
                                .build()
                        )
                    }
                } catch (e: Exception) {
                    reportException(e)
                }

                val request = CarPage.from(MusicService.SEARCH, page, pageSize)
                    ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                LibraryResult.ofItemList(
                    withCarArtwork(searchResults.slicePage(request.offset, request.size), browser), params,
                )

            } catch (e: Exception) {
                reportException(e)
                LibraryResult.ofItemList(emptyList(), params)
            }
        }
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaItemsWithStartPosition> =
        scope.future(Dispatchers.IO) {
            val defaultResult = MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs)
            val voiceQuery = mediaItems.firstOrNull()?.requestMetadata?.searchQuery

            val path = if (!voiceQuery.isNullOrBlank()) {
                listOf(MusicService.SEARCH, voiceQuery, "")
            } else {
                mediaItems.firstOrNull()?.mediaId?.split("/")
            } ?: return@future defaultResult

            when (path.firstOrNull()) {
                MusicService.SONG, MusicService.ARTIST, MusicService.ALBUM, MusicService.PLAYLIST -> {
                    val songId = path.getOrNull(if (path.first() == MusicService.SONG) 1 else 2)
                        ?: return@future defaultResult
                    val parentId = path.dropLast(1).joinToString("/")
                    val ids = carLibrary.songIds(parentId)
                    val shuffle = songId == MusicService.SHUFFLE_ACTION
                    val items = carLibrary.queueItems(if (shuffle) ids.shuffled() else ids)
                    MediaItemsWithStartPosition(
                        items,
                        if (shuffle) 0 else items.indexOfFirst { it.mediaId == songId }.coerceAtLeast(0),
                        if (shuffle) C.TIME_UNSET else startPositionMs,
                    )
                }

                MusicService.YOUTUBE_PLAYLIST -> {
                    val songId = path.getOrNull(2) ?: return@future defaultResult
                    val playlistId = path.getOrNull(1) ?: return@future defaultResult

                    val songs = try {
                        YouTube.playlist(playlistId).getOrNull()?.songs?.map {
                            it.toMediaItem()
                        } ?: emptyList()
                    } catch (e: Exception) {
                        reportException(e)
                        return@future defaultResult
                    }

                    // Check if this is a shuffle action
                    if (songId == MusicService.SHUFFLE_ACTION) {
                        MediaItemsWithStartPosition(
                            songs.shuffled(),
                            0,
                            C.TIME_UNSET
                        )
                    } else {
                        MediaItemsWithStartPosition(
                            songs,
                            songs.indexOfFirst { it.mediaId.endsWith(songId) }.takeIf { it != -1 } ?: 0,
                            C.TIME_UNSET
                        )
                    }
                }

                MusicService.SEARCH -> {
                    val songId = path.getOrNull(2) ?: return@future defaultResult
                    val searchQuery = path.getOrNull(1) ?: return@future defaultResult
                    val isVoicePlay = songId.isBlank()

                    val allLocalSongs = carLibrary.search(searchQuery)

                    val onlineSongItems = try {
                        searchOnlineSongs(searchQuery)
                    } catch (e: Exception) {
                        reportException(e)
                        emptyList()
                    }
                    onlineSongItems.forEach { songItem ->
                        database.query { insert(songItem.toMediaMetadata()) }
                    }

                    val orderedMetadata =
                        orderSearchMetadata(
                            online = onlineSongItems.map { it.toMediaMetadata() },
                            local = allLocalSongs.map { it.toMediaMetadata() },
                            prioritizeOnline = isVoicePlay,
                        )
                    if (orderedMetadata.isEmpty()) {
                        return@future defaultResult
                    }

                    if (isVoicePlay && onlineSongItems.isNotEmpty()) {
                        service.buildVoiceRadioQueue(orderedMetadata.first())?.let {
                            return@future it
                        }
                    }

                    val searchResults = orderedMetadata.map { it.toMediaItem() }
                    val targetIndex = searchResults.indexOfFirst { it.mediaId == songId }

                    MediaItemsWithStartPosition(
                        searchResults,
                        if (targetIndex >= 0) targetIndex else 0,
                        C.TIME_UNSET
                    )
                }

                else -> defaultResult
            }
        }

    private suspend fun searchOnlineSongs(query: String): List<SongItem> {
        val summaries = YouTube.searchSummary(query).getOrNull()?.summaries ?: return emptyList()
        return rankVoiceSearchSongs(
            summaries = summaries,
            topResultSectionTitle = YouTubeConstants.DEFAULT_TOP_RESULT,
        )
            .filterExplicit(context.dataStore.get(HideExplicitKey, false))
            .filterVideoSongs(context.dataStore.get(HideVideoSongsKey, false))
    }

    private fun withCarArtwork(items: List<MediaItem>, browser: MediaSession.ControllerInfo): List<MediaItem> =
        items.map { item ->
            val artwork = carArtworkUri(context.packageName, item.mediaMetadata.artworkUri)
            if (artwork?.authority == "${context.packageName}.carArtwork") {
                context.grantUriPermission(browser.packageName, artwork, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(artwork).build()).build()
        }

    private fun shuffleItem(parentId: String): MediaItem = MediaItem.Builder()
        .setMediaId("$parentId/${MusicService.SHUFFLE_ACTION}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(context.getString(R.string.shuffle))
                .setArtworkUri(drawableUri(R.drawable.shuffle))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        ).build()

    private fun drawableUri(
        @DrawableRes id: Int,
    ) = Uri
        .Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.resources.getResourcePackageName(id))
        .appendPath(context.resources.getResourceTypeName(id))
        .appendPath(context.resources.getResourceEntryName(id))
        .build()

    private fun browsableMediaItem(
        id: String,
        title: String,
        subtitle: String?,
        iconUri: Uri?,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_MUSIC,
    ) = MediaItem
        .Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtist(subtitle)
                .setArtworkUri(iconUri)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setMediaType(mediaType)
                .build(),
        ).build()

    private fun Song.toMediaItem(path: String, isPlayable: Boolean = true, isBrowsable: Boolean = false): MediaItem =
        toCarMediaItem(path, getArtistSeparator(context), isPlayable, isBrowsable)
}
