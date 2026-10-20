/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.voice

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.YouTubeConstants
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.music.R
import com.metrolist.music.constants.AllowVoiceFavoriteEditKey
import com.metrolist.music.constants.AllowVoicePlaybackControlKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.MediaSessionConstants
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.AndroidAutoLibrary
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.cleanVoiceArtistQuery
import com.metrolist.music.playback.findVoiceArtistMatch
import com.metrolist.music.playback.orderSearchMetadata
import com.metrolist.music.playback.rankVoiceArtistSongs
import com.metrolist.music.playback.rankVoiceSearchSongs
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoiceControlEntryPoint {
    fun musicDatabase(): MusicDatabase
}

/**
 * Public broadcast entry for any voice assistant. Exported without any
 * caller restriction; access is gated by the user's privacy switches.
 *
 * Callers should use sendOrderedBroadcast() and read the result:
 * resultCode (OK / CANCELED), resultData (human message) and the result
 * extras ([VoiceControl.EXTRA_STATUS], plus title/mode when relevant).
 */
class VoiceControlReceiver : BroadcastReceiver() {
    // MediaController requires every method call to happen on the thread of
    // its application looper, while the voice handling also touches the
    // database and the network, which must stay off the main thread. Running
    // the whole flow on one dedicated looper thread satisfies both.
    private val scope = CoroutineScope(voiceHandler.asCoroutineDispatcher() + SupervisorJob())

    // The ordered broadcast's pending result returned by goAsync(). Once
    // goAsync() has been called, the receiver's own setResultCode() and
    // friends throw IllegalStateException("Call while result is not
    // pending"), so the result must be written to this object instead.
    private var pendingResult: PendingResult? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != VoiceControl.ACTION) return
        val pending = goAsync()
        pendingResult = pending
        val command = intent.getStringExtra(VoiceControl.EXTRA_COMMAND).orEmpty()
        val query = intent.getStringExtra(VoiceControl.EXTRA_QUERY).orEmpty()
        scope.launch {
            try {
                // Broadcast receivers only get a few seconds; never leave the
                // caller waiting past this limit.
                val finished: Unit? = withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                    handle(context, command, query)
                }
                if (finished == null) {
                    finishWith(
                        Activity.RESULT_CANCELED,
                        VoiceControl.STATUS_ERROR,
                        context.voiceMessage(R.string.voice_result_error, R.string.voice_result_error_zh_tw),
                        null,
                    )
                }
            } catch (t: Throwable) {
                reportException(t)
                finishWith(
                    Activity.RESULT_CANCELED,
                    VoiceControl.STATUS_ERROR,
                    context.voiceMessage(R.string.voice_result_error, R.string.voice_result_error_zh_tw),
                    null,
                )
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, command: String, query: String) {
        val favoriteCommand = command == VoiceControl.COMMAND_LIKE || command == VoiceControl.COMMAND_UNLIKE
        if (favoriteCommand) {
            if (!context.dataStore.get(AllowVoiceFavoriteEditKey, false)) {
                finishWith(
                    Activity.RESULT_CANCELED,
                    VoiceControl.STATUS_DENIED,
                    context.voiceMessage(
                        R.string.voice_result_denied_favorite,
                        R.string.voice_result_denied_favorite_zh_tw,
                    ),
                    null,
                )
                return
            }
        } else {
            if (!context.dataStore.get(AllowVoicePlaybackControlKey, true)) {
                finishWith(
                    Activity.RESULT_CANCELED,
                    VoiceControl.STATUS_DENIED,
                    context.voiceMessage(
                        R.string.voice_result_denied_playback,
                        R.string.voice_result_denied_playback_zh_tw,
                    ),
                    null,
                )
                return
            }
        }

        val database = EntryPointAccessors.fromApplication(
            context.applicationContext,
            VoiceControlEntryPoint::class.java,
        ).musicDatabase()

        // Connecting a MediaController also cold-starts the service, so no
        // manual playback is required beforehand. The receiver's onReceive
        // context is a ReceiverRestrictedContext and must not bind services
        // (bindService throws ReceiverCallNotAllowedException there), so the
        // controller is built against the application context.
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
        val controller = withTimeoutOrNull(CONTROLLER_TIMEOUT_MS) {
            MediaController.Builder(appContext, token)
                .setApplicationLooper(voiceThread.looper)
                .buildAsync()
                .await()
        }
        if (controller == null) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_ERROR,
                context.voiceMessage(R.string.voice_result_error, R.string.voice_result_error_zh_tw),
                null,
            )
            return
        }
        try {
            when (command) {
                VoiceControl.COMMAND_PLAY_QUERY -> playQuery(context, database, controller, query)
                VoiceControl.COMMAND_PLAY_ARTIST -> playArtist(context, database, controller, query)
                VoiceControl.COMMAND_PLAY_LIKED -> playLiked(context, database, controller)
                VoiceControl.COMMAND_PLAY -> resume(context, database, controller)
                VoiceControl.COMMAND_PAUSE -> pause(context, controller)
                VoiceControl.COMMAND_STOP -> stop(context, controller)
                VoiceControl.COMMAND_NOW_PLAYING -> nowPlaying(context, controller)
                VoiceControl.COMMAND_NEXT -> skipNext(context, controller)
                VoiceControl.COMMAND_PREVIOUS -> skipPrevious(context, controller)
                VoiceControl.COMMAND_LIKE -> setLiked(context, database, controller, liked = true)
                VoiceControl.COMMAND_UNLIKE -> setLiked(context, database, controller, liked = false)
                VoiceControl.COMMAND_REPEAT_TOGGLE -> toggleRepeat(context, controller)
                VoiceControl.COMMAND_REPEAT_OFF -> setRepeat(context, controller, Player.REPEAT_MODE_OFF)
                VoiceControl.COMMAND_REPEAT_ALL -> setRepeat(context, controller, Player.REPEAT_MODE_ALL)
                VoiceControl.COMMAND_REPEAT_ONE -> setRepeat(context, controller, Player.REPEAT_MODE_ONE)
                VoiceControl.COMMAND_REPEAT_STATE -> queryRepeat(context, controller)
                else -> finishWith(
                    Activity.RESULT_CANCELED,
                    VoiceControl.STATUS_ERROR,
                    context.voiceMessage(R.string.voice_result_error, R.string.voice_result_error_zh_tw),
                    null,
                )
            }
        } finally {
            runCatching { controller.release() }
        }
    }

    private suspend fun playQuery(
        context: Context,
        database: MusicDatabase,
        controller: MediaController,
        query: String,
    ) {
        if (query.isBlank()) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_NOT_FOUND,
                context.voiceMessage(
                    R.string.voice_result_not_found,
                    R.string.voice_result_not_found_zh_tw,
                    query,
                ),
                null,
            )
            return
        }
        searchAndPlay(context, database, controller, query, artistMode = false)
    }

    /**
     * 只講歌手時的點歌：先把「的歌」「歌」這類尾巴清掉，再用乾淨的歌手名去找，
     * 找到就播該歌手的歌，並回真的播了哪一首。
     */
    private suspend fun playArtist(
        context: Context,
        database: MusicDatabase,
        controller: MediaController,
        query: String,
    ) {
        val artist = cleanVoiceArtistQuery(query)
        if (artist.isBlank()) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_NOT_FOUND,
                context.voiceMessage(
                    R.string.voice_result_not_found,
                    R.string.voice_result_not_found_zh_tw,
                    query,
                ),
                null,
            )
            return
        }
        searchAndPlay(context, database, controller, artist, artistMode = true)
    }

    private suspend fun searchAndPlay(
        context: Context,
        database: MusicDatabase,
        controller: MediaController,
        query: String,
        artistMode: Boolean,
    ) {
        // 跟車機語音同一套找法：線上結果排前面，後面補本地收藏。
        val library = AndroidAutoLibrary(database) { emptyList() }
        val localSongs = runCatching { library.search(query) }.getOrDefault(emptyList())

        val hasNetwork = context.isOnline()
        val onlineResult = if (hasNetwork) {
            runCatching { searchOnlineSongs(context, query) }
        } else null
        val onlineSongs = onlineResult?.getOrDefault(emptyList()).orEmpty()

        onlineSongs.forEach { songItem ->
            runCatching { database.insert(songItem.toMediaMetadata()) }
        }
        val ordered = orderSearchMetadata(
            online = onlineSongs.map { it.toMediaMetadata() },
            local = localSongs.map { it.toMediaMetadata() },
            prioritizeOnline = true,
        )
        if (ordered.isEmpty()) {
            // Local is empty here as well. A failed online search (or no
            // network at all) means "no network", not "not found".
            val searchFailed = !hasNetwork || onlineResult?.isFailure == true
            if (searchFailed) {
                finishWith(
                    Activity.RESULT_CANCELED,
                    VoiceControl.STATUS_NO_NETWORK,
                    context.voiceMessage(
                        R.string.voice_result_no_network,
                        R.string.voice_result_no_network_zh_tw,
                    ),
                    null,
                )
            } else {
                finishWith(
                    Activity.RESULT_CANCELED,
                    VoiceControl.STATUS_NOT_FOUND,
                    context.voiceMessage(
                        R.string.voice_result_not_found,
                        R.string.voice_result_not_found_zh_tw,
                        query,
                    ),
                    null,
                )
            }
            return
        }
        val candidates: List<MediaMetadata>
        val artistMatched: Boolean
        if (artistMode) {
            // 只要歌手名對得上的歌，完全相同的排前面；一個都對不上才退回
            // 全部結果，至少播最相關的一首（這時要照實告訴小助手沒對上）。
            val ranked = rankVoiceArtistSongs(ordered, query) { meta ->
                meta.artists.map { it.name }
            }
            candidates = ranked.ifEmpty { ordered }
            artistMatched = ranked.isNotEmpty()
        } else {
            candidates = ordered
            artistMatched = true
        }
        // The session's media item resolution only understands its library
        // paths and voice search queries; bare song IDs resolve to an empty
        // queue and would clear whatever was queued. Hand the query over as
        // a voice search request so the session builds its own radio queue
        // around the best match.
        val items = candidates.map { metadata ->
            metadata.toMediaItem().buildUpon()
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setSearchQuery(query)
                        .build(),
                )
                .build()
        }
        if (!applyMediaItemsAndPlay(controller, items, candidates.first().id)) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_ERROR,
                context.voiceMessage(R.string.voice_result_error, R.string.voice_result_error_zh_tw),
                null,
            )
            return
        }
        val first = candidates.first()
        // title 欄位只放歌名，歌手走 artist 欄位；但念出來的訊息在知道歌手時
        // 要帶上「歌名 - 歌手」，因為文件叫小助手直接念訊息、不要自己拼字。
        val title = first.title
        val artist = if (artistMode) {
            findVoiceArtistMatch(first.artists.map { it.name }, query)
                ?: first.artists.firstOrNull()?.name.orEmpty()
        } else {
            first.artists.firstOrNull()?.name.orEmpty()
        }
        val spoken = if (artist.isBlank()) title else "$title - $artist"
        if (artistMode && !artistMatched) {
            finishWith(
                Activity.RESULT_OK,
                VoiceControl.STATUS_PLAYED,
                context.voiceMessage(
                    R.string.voice_result_played_artist_fallback,
                    R.string.voice_result_played_artist_fallback_zh_tw,
                    query,
                    spoken,
                ),
                Bundle().apply {
                    putString(VoiceControl.EXTRA_TITLE, title)
                    putString(VoiceControl.EXTRA_ARTIST, artist)
                    putBoolean(VoiceControl.EXTRA_ARTIST_MATCHED, false)
                },
            )
            return
        }
        if (artistMode) {
            finishWith(
                Activity.RESULT_OK,
                VoiceControl.STATUS_PLAYED,
                context.voiceMessage(
                    R.string.voice_result_played_artist,
                    R.string.voice_result_played_artist_zh_tw,
                    artist.ifBlank { query },
                    title,
                ),
                Bundle().apply {
                    putString(VoiceControl.EXTRA_TITLE, title)
                    putString(VoiceControl.EXTRA_ARTIST, artist)
                    putBoolean(VoiceControl.EXTRA_ARTIST_MATCHED, true)
                },
            )
        } else {
            finishWith(
                Activity.RESULT_OK,
                VoiceControl.STATUS_PLAYED,
                context.voiceMessage(
                    R.string.voice_result_played,
                    R.string.voice_result_played_zh_tw,
                    spoken,
                ),
                Bundle().apply {
                    putString(VoiceControl.EXTRA_TITLE, title)
                    putString(VoiceControl.EXTRA_ARTIST, artist)
                },
            )
        }
    }

    private suspend fun playLiked(
        context: Context,
        database: MusicDatabase,
        controller: MediaController,
    ) {
        val songs = runCatching { database.likedSongsByCreateDateAsc().first() }.getOrDefault(emptyList())
        if (songs.isEmpty()) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_EMPTY,
                context.voiceMessage(R.string.voice_result_empty, R.string.voice_result_empty_zh_tw),
                null,
            )
            return
        }
        // Bare song IDs resolve to an empty queue in the session; address
        // the liked-playlist node instead so the session builds the queue
        // and starts at the first liked song.
        val firstSongId = songs.first().song.id
        val items = songs.map { it.toMediaItem() }.toMutableList()
        items[0] = items[0].buildUpon()
            .setMediaId("${MusicService.PLAYLIST}/${PlaylistEntity.LIKED_PLAYLIST_ID}/$firstSongId")
            .build()
        if (!applyMediaItemsAndPlay(controller, items, firstSongId)) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_ERROR,
                context.voiceMessage(R.string.voice_result_error, R.string.voice_result_error_zh_tw),
                null,
            )
            return
        }
        val first = songs.firstOrNull()
        val firstTitle = first?.song?.title.orEmpty()
        val firstArtist = first?.orderedArtists?.firstOrNull()?.name.orEmpty()
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_PLAYED,
            context.voiceMessage(
                R.string.voice_result_played_liked,
                R.string.voice_result_played_liked_zh_tw,
                songs.size,
                firstTitle,
            ),
            Bundle().apply {
                putString(VoiceControl.EXTRA_TITLE, firstTitle)
                putString(VoiceControl.EXTRA_ARTIST, firstArtist)
            },
        )
    }

    private suspend fun resume(context: Context, database: MusicDatabase, controller: MediaController) {
        if (controller.mediaItemCount == 0) {
            // 冷啟動時服務還在背後把上次的清單讀回來，先等一下；
            // 沒等到才當作真的沒歌，不然會蓋掉使用者原本的清單。
            awaitRestoredQueue(controller)
        }
        if (controller.mediaItemCount == 0) {
            // 沒歌可繼續時直接播喜愛清單；連喜愛都是空的才明確請對方先點歌。
            val liked = runCatching { database.likedSongsByCreateDateAsc().first() }.getOrDefault(emptyList())
            if (liked.isNotEmpty()) {
                // Bare song IDs resolve to an empty queue in the session;
                // address the liked-playlist node instead.
                val items = liked.map { it.toMediaItem() }.toMutableList()
                items[0] = items[0].buildUpon()
                    .setMediaId(
                        "${MusicService.PLAYLIST}/${PlaylistEntity.LIKED_PLAYLIST_ID}/${liked.first().song.id}",
                    )
                    .build()
                if (!applyMediaItemsAndPlay(controller, items, liked.first().song.id)) {
                    finishWith(
                        Activity.RESULT_CANCELED,
                        VoiceControl.STATUS_ERROR,
                        context.voiceMessage(R.string.voice_result_error, R.string.voice_result_error_zh_tw),
                        null,
                    )
                    return
                }
                val first = liked.firstOrNull()
                val firstTitle = first?.song?.title.orEmpty()
                val firstArtist = first?.orderedArtists?.firstOrNull()?.name.orEmpty()
                finishWith(
                    Activity.RESULT_OK,
                    VoiceControl.STATUS_PLAYED,
                    context.voiceMessage(
                        R.string.voice_result_played_liked,
                        R.string.voice_result_played_liked_zh_tw,
                        liked.size,
                        firstTitle,
                    ),
                    Bundle().apply {
                        putString(VoiceControl.EXTRA_TITLE, firstTitle)
                        putString(VoiceControl.EXTRA_ARTIST, firstArtist)
                    },
                )
                return
            }
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_NO_SONG,
                context.voiceMessage(
                    R.string.voice_result_no_queue_hint,
                    R.string.voice_result_no_queue_hint_zh_tw,
                ),
                null,
            )
            return
        }
        // stop() 之後播放器會回到閒置狀態，要先 prepare()，play() 才會真的出聲。
        playAfterPossibleStop(controller)
        val metadata = controller.currentMediaItem?.mediaMetadata
        val currentTitle = metadata?.title?.toString().orEmpty()
        val currentArtist = metadata?.artist?.toString().orEmpty()
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_PLAYING,
            context.voiceMessage(R.string.voice_result_playing, R.string.voice_result_playing_zh_tw),
            Bundle().apply {
                putString(VoiceControl.EXTRA_TITLE, currentTitle)
                putString(VoiceControl.EXTRA_ARTIST, currentArtist)
            },
        )
    }

    private suspend fun awaitRestoredQueue(controller: MediaController) {
        repeat(RESTORE_POLL_TIMES) {
            if (controller.mediaItemCount > 0) return
            delay(RESTORE_POLL_INTERVAL_MS)
        }
    }

    /**
     * Applies [items] through the session and starts playback. The session
     * resolves the request asynchronously (it may hit the network) and a
     * failure silently leaves the queue empty, so playback of the requested
     * first item is polled for before reporting success.
     */
    private suspend fun applyMediaItemsAndPlay(
        controller: MediaController,
        items: List<MediaItem>,
        expectedFirstMediaId: String,
    ): Boolean {
        controller.setMediaItems(items)
        playAfterPossibleStop(controller)
        var started = false
        withTimeoutOrNull(PLAYBACK_START_TIMEOUT_MS) {
            while (!started) {
                started = controller.currentMediaItem?.mediaId == expectedFirstMediaId &&
                    (
                        controller.isPlaying ||
                            controller.playbackState == Player.STATE_BUFFERING ||
                            controller.playbackState == Player.STATE_READY
                        )
                if (!started) delay(PLAYBACK_START_POLL_INTERVAL_MS)
            }
        }
        return started
    }

    private fun playAfterPossibleStop(controller: MediaController) {
        if (controller.playbackState == Player.STATE_IDLE) {
            controller.prepare()
        }
        controller.play()
    }

    private fun stop(context: Context, controller: MediaController) {
        if (controller.mediaItemCount == 0) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_NO_SONG,
                context.voiceMessage(R.string.voice_result_no_song, R.string.voice_result_no_song_zh_tw),
                null,
            )
            return
        }
        // 真正停下來（通知列也會收掉），清單留著，之後用 play 還能繼續播。
        controller.stop()
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_STOPPED,
            context.voiceMessage(R.string.voice_result_stopped, R.string.voice_result_stopped_zh_tw),
            null,
        )
    }

    private fun nowPlaying(context: Context, controller: MediaController) {
        val item = controller.currentMediaItem
        val title = item?.mediaMetadata?.title?.toString().orEmpty()
        if (controller.mediaItemCount == 0 || item == null || title.isBlank()) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_NO_SONG,
                context.voiceMessage(R.string.voice_result_no_song, R.string.voice_result_no_song_zh_tw),
                null,
            )
            return
        }
        val artist = item.mediaMetadata.artist?.toString().orEmpty()
        val spoken = if (artist.isBlank()) title else "$title - $artist"
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_NOW_PLAYING,
            context.voiceMessage(
                R.string.voice_result_now_playing,
                R.string.voice_result_now_playing_zh_tw,
                spoken,
            ),
            Bundle().apply {
                putString(VoiceControl.EXTRA_TITLE, title)
                putString(VoiceControl.EXTRA_ARTIST, artist)
                putBoolean(VoiceControl.EXTRA_IS_PLAYING, controller.isPlaying)
            },
        )
    }

    private fun pause(context: Context, controller: MediaController) {
        if (controller.mediaItemCount == 0) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_NO_SONG,
                context.voiceMessage(R.string.voice_result_no_song, R.string.voice_result_no_song_zh_tw),
                null,
            )
            return
        }
        controller.pause()
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_PAUSED,
            context.voiceMessage(R.string.voice_result_paused, R.string.voice_result_paused_zh_tw),
            null,
        )
    }

    private fun skipNext(context: Context, controller: MediaController) {
        if (!controller.hasNextMediaItem()) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_NO_SONG,
                context.voiceMessage(R.string.voice_result_no_next, R.string.voice_result_no_next_zh_tw),
                null,
            )
            return
        }
        val target = runCatching {
            controller.getMediaItemAt(controller.nextMediaItemIndex).mediaMetadata
        }.getOrNull()
        val targetTitle = target?.title?.toString().orEmpty()
        val targetArtist = target?.artist?.toString().orEmpty()
        controller.seekToNext()
        playAfterPossibleStop(controller)
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_NEXT,
            context.voiceMessage(
                R.string.voice_result_next,
                R.string.voice_result_next_zh_tw,
                targetTitle,
            ),
            Bundle().apply {
                putString(VoiceControl.EXTRA_TITLE, targetTitle)
                putString(VoiceControl.EXTRA_ARTIST, targetArtist)
            },
        )
    }

    private fun skipPrevious(context: Context, controller: MediaController) {
        if (!controller.hasPreviousMediaItem()) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_NO_SONG,
                context.voiceMessage(R.string.voice_result_no_previous, R.string.voice_result_no_previous_zh_tw),
                null,
            )
            return
        }
        val target = runCatching {
            controller.getMediaItemAt(controller.previousMediaItemIndex).mediaMetadata
        }.getOrNull()
        val targetTitle = target?.title?.toString().orEmpty()
        val targetArtist = target?.artist?.toString().orEmpty()
        controller.seekToPrevious()
        playAfterPossibleStop(controller)
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_PREVIOUS,
            context.voiceMessage(
                R.string.voice_result_previous,
                R.string.voice_result_previous_zh_tw,
                targetTitle,
            ),
            Bundle().apply {
                putString(VoiceControl.EXTRA_TITLE, targetTitle)
                putString(VoiceControl.EXTRA_ARTIST, targetArtist)
            },
        )
    }

    private suspend fun setLiked(
        context: Context,
        database: MusicDatabase,
        controller: MediaController,
        liked: Boolean,
    ) {
        val mediaId = controller.currentMediaItem?.mediaId?.substringAfterLast("/").orEmpty()
        if (mediaId.isBlank()) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_NO_SONG,
                context.voiceMessage(R.string.voice_result_no_song, R.string.voice_result_no_song_zh_tw),
                null,
            )
            return
        }
        val current = runCatching { database.song(mediaId).first() }.getOrNull()
        if (current == null) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_NO_SONG,
                context.voiceMessage(R.string.voice_result_no_song, R.string.voice_result_no_song_zh_tw),
                null,
            )
            return
        }
        val title = current.song.title
        val artist = current.orderedArtists.firstOrNull()?.name.orEmpty()
        // 已經是目標狀態就直接回「已經收藏過了」，不用再送一次切換。
        if (current.song.liked == liked) {
            finishWith(
                Activity.RESULT_OK,
                if (liked) VoiceControl.STATUS_ALREADY_LIKED else VoiceControl.STATUS_ALREADY_UNLIKED,
                if (liked) {
                    context.voiceMessage(
                        R.string.voice_result_already_liked,
                        R.string.voice_result_already_liked_zh_tw,
                        title,
                    )
                } else {
                    context.voiceMessage(
                        R.string.voice_result_already_unliked,
                        R.string.voice_result_already_unliked_zh_tw,
                        title,
                    )
                },
                Bundle().apply {
                    putString(VoiceControl.EXTRA_TITLE, title)
                    putString(VoiceControl.EXTRA_ARTIST, artist)
                },
            )
            return
        }
        // Only ask the service to toggle when a change is actually needed, so
        // the existing playback logic stays the single writer (it also
        // refreshes the notification and widgets). The command result is
        // checked: a denial must not be reported as success.
        val result = runCatching {
            controller.sendCustomCommand(MediaSessionConstants.CommandToggleLike, Bundle.EMPTY).await()
        }.getOrNull()
        if (result?.resultCode != SessionResult.RESULT_SUCCESS) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_ERROR,
                context.voiceMessage(R.string.voice_result_error, R.string.voice_result_error_zh_tw),
                null,
            )
            return
        }
        finishWith(
            Activity.RESULT_OK,
            if (liked) VoiceControl.STATUS_LIKED else VoiceControl.STATUS_UNLIKED,
            if (liked) {
                context.voiceMessage(R.string.voice_result_liked, R.string.voice_result_liked_zh_tw, title)
            } else {
                context.voiceMessage(R.string.voice_result_unliked, R.string.voice_result_unliked_zh_tw, title)
            },
            Bundle().apply {
                putString(VoiceControl.EXTRA_TITLE, title)
                putString(VoiceControl.EXTRA_ARTIST, artist)
            },
        )
    }

    private fun toggleRepeat(context: Context, controller: MediaController) {
        val next = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = next
        finishRepeat(context, next)
    }

    private fun setRepeat(context: Context, controller: MediaController, mode: Int) {
        controller.repeatMode = mode
        finishRepeat(context, mode)
    }

    private fun queryRepeat(context: Context, controller: MediaController) {
        finishRepeat(context, controller.repeatMode)
    }

    private fun finishRepeat(context: Context, mode: Int) {
        val (modeId, modeName) = when (mode) {
            Player.REPEAT_MODE_ALL -> VoiceControl.MODE_ALL to context.getString(R.string.repeat_mode_all)
            Player.REPEAT_MODE_ONE -> VoiceControl.MODE_ONE to context.getString(R.string.repeat_mode_one)
            else -> VoiceControl.MODE_OFF to context.getString(R.string.repeat_mode_off)
        }
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_REPEAT,
            context.voiceMessage(
                R.string.voice_result_repeat,
                R.string.voice_result_repeat_zh_tw,
                modeName,
            ),
            Bundle().apply { putString(VoiceControl.EXTRA_MODE, modeId) },
        )
    }

    private suspend fun searchOnlineSongs(context: Context, query: String): List<SongItem> {
        val summaries = YouTube.searchSummary(query).getOrNull()?.summaries ?: return emptyList()
        return rankVoiceSearchSongs(
            summaries = summaries,
            topResultSectionTitle = YouTubeConstants.DEFAULT_TOP_RESULT,
        )
            .filterExplicit(context.dataStore.get(HideExplicitKey, false))
            .filterVideoSongs(context.dataStore.get(HideVideoSongsKey, false))
    }

    private fun Context.isOnline(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.activeNetwork != null
    }

    private fun Context.voiceMessage(@StringRes english: Int, @StringRes chinese: Int, vararg args: Any): String =
        voiceLocalizedString(this, english, chinese, *args)

    private fun finishWith(code: Int, status: String, message: String, extras: Bundle?) {
        // Results go to the goAsync() PendingResult: after goAsync() the
        // receiver's own setters are no longer usable (they throw), and the
        // values set there would never reach the caller.
        val pending = pendingResult ?: return
        pending.setResultCode(code)
        pending.setResultData(message)
        pending.setResultExtras(
            (extras ?: Bundle()).apply {
                putString(VoiceControl.EXTRA_STATUS, status)
                putString(VoiceControl.EXTRA_MESSAGE, message)
            },
        )
    }

    private companion object {
        // Shared looper thread: see the scope comment above. The controller's
        // application looper must be this thread's looper.
        val voiceThread = HandlerThread("VoiceControl").apply { start() }
        val voiceHandler = Handler(voiceThread.looper)

        const val CONTROLLER_TIMEOUT_MS = 8_000L
        const val RECEIVER_TIMEOUT_MS = 9_000L
        // The session resolves setMediaItems asynchronously (network) before
        // the requested content reaches the player; wait for it to start.
        const val PLAYBACK_START_TIMEOUT_MS = 5_000L
        const val PLAYBACK_START_POLL_INTERVAL_MS = 200L
        // 上次的清單是服務在背後讀回來的，通常幾百毫秒內會出現；
        // 最多等 1 秒，播報入口整體 9 秒的上限還夠用。
        const val RESTORE_POLL_TIMES = 10
        const val RESTORE_POLL_INTERVAL_MS = 100L
    }
}
