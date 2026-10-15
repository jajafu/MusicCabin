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
import androidx.annotation.StringRes
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
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.AndroidAutoLibrary
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.orderSearchMetadata
import com.metrolist.music.playback.rankVoiceSearchSongs
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != VoiceControl.ACTION) return
        val pending = goAsync()
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
        // manual playback is required beforehand.
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controller = withTimeoutOrNull(CONTROLLER_TIMEOUT_MS) {
            MediaController.Builder(context, token).buildAsync().await()
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
                VoiceControl.COMMAND_PLAY_LIKED -> playLiked(context, database, controller)
                VoiceControl.COMMAND_PLAY -> resume(context, controller)
                VoiceControl.COMMAND_PAUSE -> pause(context, controller)
                VoiceControl.COMMAND_NEXT -> skipNext(context, controller)
                VoiceControl.COMMAND_PREVIOUS -> skipPrevious(context, controller)
                VoiceControl.COMMAND_LIKE -> setLiked(context, database, controller, liked = true)
                VoiceControl.COMMAND_UNLIKE -> setLiked(context, database, controller, liked = false)
                VoiceControl.COMMAND_REPEAT_TOGGLE -> toggleRepeat(context, controller)
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
        // Same search flow as the car/voice path: local first, then online.
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
        val items = ordered.map { it.toMediaItem() }
        controller.setMediaItems(items)
        controller.prepare()
        controller.play()
        val first = ordered.first()
        val artist = first.artists.firstOrNull()?.name
        val title = if (artist.isNullOrBlank()) first.title else "${first.title} - $artist"
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_PLAYED,
            context.voiceMessage(
                R.string.voice_result_played,
                R.string.voice_result_played_zh_tw,
                title,
            ),
            Bundle().apply { putString(VoiceControl.EXTRA_TITLE, title) },
        )
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
        controller.setMediaItems(songs.map { it.toMediaItem() })
        controller.prepare()
        controller.play()
        val firstTitle = songs.firstOrNull()?.song?.title.orEmpty()
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_PLAYED,
            context.voiceMessage(
                R.string.voice_result_played_liked,
                R.string.voice_result_played_liked_zh_tw,
                songs.size,
                firstTitle,
            ),
            Bundle().apply { putString(VoiceControl.EXTRA_TITLE, firstTitle) },
        )
    }

    private fun resume(context: Context, controller: MediaController) {
        if (controller.mediaItemCount == 0) {
            finishWith(
                Activity.RESULT_CANCELED,
                VoiceControl.STATUS_NO_SONG,
                context.voiceMessage(R.string.voice_result_no_song, R.string.voice_result_no_song_zh_tw),
                null,
            )
            return
        }
        controller.play()
        val currentTitle = controller.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_PLAYING,
            context.voiceMessage(R.string.voice_result_playing, R.string.voice_result_playing_zh_tw),
            Bundle().apply { putString(VoiceControl.EXTRA_TITLE, currentTitle) },
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
        val targetTitle = runCatching {
            controller.getMediaItemAt(controller.nextMediaItemIndex).mediaMetadata.title?.toString()
        }.getOrNull().orEmpty()
        controller.seekToNext()
        controller.play()
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_NEXT,
            context.voiceMessage(
                R.string.voice_result_next,
                R.string.voice_result_next_zh_tw,
                targetTitle,
            ),
            Bundle().apply { putString(VoiceControl.EXTRA_TITLE, targetTitle) },
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
        val targetTitle = runCatching {
            controller.getMediaItemAt(controller.previousMediaItemIndex).mediaMetadata.title?.toString()
        }.getOrNull().orEmpty()
        controller.seekToPrevious()
        controller.play()
        finishWith(
            Activity.RESULT_OK,
            VoiceControl.STATUS_PREVIOUS,
            context.voiceMessage(
                R.string.voice_result_previous,
                R.string.voice_result_previous_zh_tw,
                targetTitle,
            ),
            Bundle().apply { putString(VoiceControl.EXTRA_TITLE, targetTitle) },
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
        // Only ask the service to toggle when a change is actually needed, so
        // the existing playback logic stays the single writer (it also
        // refreshes the notification and widgets). The command result is
        // checked: a denial must not be reported as success.
        if (current.song.liked != liked) {
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
        }
        finishWith(
            Activity.RESULT_OK,
            if (liked) VoiceControl.STATUS_LIKED else VoiceControl.STATUS_UNLIKED,
            if (liked) {
                context.voiceMessage(R.string.voice_result_liked, R.string.voice_result_liked_zh_tw, title)
            } else {
                context.voiceMessage(R.string.voice_result_unliked, R.string.voice_result_unliked_zh_tw, title)
            },
            Bundle().apply { putString(VoiceControl.EXTRA_TITLE, title) },
        )
    }

    private fun toggleRepeat(context: Context, controller: MediaController) {
        val next = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = next
        val (mode, modeName) = when (next) {
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
            Bundle().apply { putString(VoiceControl.EXTRA_MODE, mode) },
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
        runCatching {
            setResultCode(code)
            setResultData(message)
            setResultExtras(
                (extras ?: Bundle()).apply {
                    putString(VoiceControl.EXTRA_STATUS, status)
                    putString(VoiceControl.EXTRA_MESSAGE, message)
                },
            )
        }
    }

    private companion object {
        const val CONTROLLER_TIMEOUT_MS = 8_000L
        const val RECEIVER_TIMEOUT_MS = 9_000L
    }
}
