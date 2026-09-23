/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.QueueData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
    private var continuation: String? = null,
    private var restoredStatus: Queue.Status? = null,
) : Queue {
    private class EmptyRadioQueueException : IllegalStateException()
    private val seenContinuations = mutableSetOf<String>()
    private var radioQueueRequested = endpoint.playlistId?.startsWith("RDAMVM") == true

    val isRadioQueue: Boolean
        get() = radioQueueRequested

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            restoredStatus?.let { status ->
                restoredStatus = null
                return@withContext status
            }

            var lastException: Throwable? = null

            if (endpoint.videoId != null && endpoint.playlistId == null) {
                endpoint = WatchEndpoint(
                    videoId = endpoint.videoId,
                    playlistId = "RDAMVM${endpoint.videoId}"
                )
                radioQueueRequested = true
            }

            val isRadioRequest =
                endpoint.playlistId?.startsWith("RDAMVM") == true ||
                (endpoint.videoId != null && endpoint.playlistId == null)

            repeat(MAX_ATTEMPTS) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    
                    var items = nextResult.items
                    val relEndpoint = nextResult.relatedEndpoint
                    
                    if (isRadioRequest && continuation == null && items.size <= 1) {
                        if (endpoint.playlistId?.startsWith("RDAMVM") == true) {
                            throw EmptyRadioQueueException()
                        } else if (relEndpoint != null) {
                            val relatedPage = YouTube.related(relEndpoint).getOrNull()
                            if (relatedPage != null && relatedPage.songs.isNotEmpty()) {
                                val relatedSongs = relatedPage.songs.filter { it.id != endpoint.videoId }
                                items = items + relatedSongs
                            }
                        }
                    }

                    endpoint = nextResult.endpoint
                    radioQueueRequested =
                        radioQueueRequested || endpoint.playlistId?.startsWith("RDAMVM") == true
                    continuation = nextResult.continuation
                    return@withContext Queue.Status(
                        title = nextResult.title,
                        items = items.map { it.toMediaItem() },
                        mediaItemIndex = nextResult.currentIndex ?: 0,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastException = e
                    if (
                        e is EmptyRadioQueueException &&
                        endpoint.playlistId?.startsWith("RDAMVM") == true &&
                        endpoint.videoId != null
                    ) {
                        endpoint = WatchEndpoint(videoId = endpoint.videoId)
                        // It will loop again and try with just videoId
                    }
                }
            }
            throw lastException ?: Exception("Failed to get initial status")
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
                    val nextResult = YouTube.next(endpoint, currentContinuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    radioQueueRequested =
                        radioQueueRequested || endpoint.playlistId?.startsWith("RDAMVM") == true
                    seenContinuations += currentContinuation
                    continuation = nextResult.continuation?.takeUnless { it in seenContinuations }
                    return@withContext nextResult.items.map { it.toMediaItem() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastException = e
                }
            }
            continuation = null
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    fun persistenceData() =
        QueueData.YouTubeDataV2(
            videoId = endpoint.videoId,
            playlistId = endpoint.playlistId,
            playlistSetVideoId = endpoint.playlistSetVideoId,
            params = endpoint.params,
            index = endpoint.index,
            continuation = continuation,
        )

    companion object {
        private const val MAX_ATTEMPTS = 3

        /**
         * Creates a radio queue based on a song.
         * Explicitly requests the RDAMVM playlist to trigger automotive/radio mixing.
         */
        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(
                    videoId = song.id,
                    playlistId = "RDAMVM${song.id}"
                ),
                song
            )
        }

        fun restore(
            data: QueueData.YouTubeDataV2,
            status: Queue.Status,
        ): YouTubeQueue =
            YouTubeQueue(
                endpoint =
                    WatchEndpoint(
                        videoId = data.videoId,
                        playlistId = data.playlistId,
                        playlistSetVideoId = data.playlistSetVideoId,
                        params = data.params,
                        index = data.index,
                    ),
                continuation = data.continuation,
                restoredStatus = status,
            )
    }
}
