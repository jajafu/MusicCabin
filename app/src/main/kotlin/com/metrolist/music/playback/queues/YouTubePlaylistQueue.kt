/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubePlaylistQueue(
    private val playlistId: String,
    private val playlistTitle: String? = null,
    private val initialSongs: List<SongItem> = emptyList(),
    private val initialContinuation: String? = null,
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = initialContinuation
    private val seenContinuations = mutableSetOf<String>()

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            if (initialSongs.isNotEmpty()) {
                Queue.Status(
                    title = playlistTitle,
                    items = initialSongs.map { it.toMediaItem() },
                    mediaItemIndex = startIndex,
                )
            } else {
                val playlistPage = YouTube.playlist(playlistId).getOrThrow()
                continuation = playlistPage.songsContinuation
                Queue.Status(
                    title = playlistPage.playlist.title,
                    items = playlistPage.songs.map { it.toMediaItem() },
                    mediaItemIndex = startIndex,
                )
            }
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            val currentContinuation = continuation ?: return@withContext emptyList()
            if (currentContinuation in seenContinuations) {
                continuation = null
                return@withContext emptyList()
            }
            var lastException: Throwable? = null
            
            repeat(MAX_ATTEMPTS) {
                try {
                    val continuationPage = YouTube.playlistContinuation(currentContinuation).getOrThrow()
                    seenContinuations += currentContinuation
                    continuation = continuationPage.continuation?.takeUnless { it in seenContinuations }
                    return@withContext continuationPage.songs.map { it.toMediaItem() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastException = e
                }
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
    }
}
