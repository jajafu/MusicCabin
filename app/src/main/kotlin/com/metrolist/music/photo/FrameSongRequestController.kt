package com.metrolist.music.photo

import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.extensions.move
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.extensions.metadata
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serves the song-request (KTV queueing) part of the photo transfer web page.
 * All methods run on the receiver's executor thread and block until done;
 * player mutations are dispatched to the main thread because ExoPlayer must
 * only be touched there. Queue state reads use PlayerConnection StateFlows,
 * which are safe to read from any thread.
 */
internal class FrameSongRequestController(
    private val playerProvider: () -> PlayerConnection?,
    private val guestProvider: () -> Boolean,
) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val searchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var searchJob: Job? = null

    /** Now playing plus the upcoming queue, taken from the player's queue flows. */
    fun state(): FrameSongRequestResult {
        val connection = readyConnection() ?: return FrameSongRequestResult.unavailable()
        val metadata = connection.mediaMetadata.value
        val windows = connection.queueWindows.value
        val currentIndex = connection.currentWindowIndex.value
        val nowPlaying = metadata?.let {
            FrameSongJson(
                index = currentIndex.takeIf { index -> index >= 0 },
                videoId = it.id,
                title = it.title,
                artists = it.artists.joinToString { artist -> artist.name },
                duration = it.duration.takeIf { duration -> duration > 0 },
                thumbnail = it.thumbnailUrl,
            )
        }
        val queue = windows.drop(currentIndex + 1).mapIndexed { offset, window ->
            val item = window.mediaItem.metadata
            FrameSongJson(
                index = currentIndex + 1 + offset,
                videoId = window.mediaItem.mediaId,
                title = item?.title.orEmpty(),
                artists = item?.artists?.joinToString { artist -> artist.name }.orEmpty(),
                duration = item?.duration?.takeIf { duration -> duration > 0 },
                thumbnail = item?.thumbnailUrl,
            )
        }
        return FrameSongRequestResult.ok(json.encodeToString(FramePlayerStateJson(nowPlaying, queue)))
    }

    /** Song search; a new request cancels the previous one before running on IO. */
    fun search(query: String): FrameSongRequestResult {
        val trimmed = query.trim().take(MAX_QUERY_LENGTH)
        if (trimmed.isEmpty()) {
            return FrameSongRequestResult.ok(json.encodeToString(FrameSearchJson(emptyList())))
        }
        searchJob?.cancel()
        val deferred = CompletableDeferred<FrameSongRequestResult>()
        searchJob = searchScope.launch {
            val outcome = try {
                YouTube.search(trimmed, YouTube.SearchFilter.FILTER_SONG).fold(
                    onSuccess = { page ->
                        val songs = page.items.filterIsInstance<SongItem>()
                            .take(MAX_SEARCH_RESULTS)
                            .map { song ->
                                FrameSongJson(
                                    videoId = song.id,
                                    title = song.title,
                                    artists = song.artists.joinToString { artist -> artist.name },
                                    duration = song.duration?.takeIf { duration -> duration > 0 },
                                    thumbnail = song.thumbnail,
                                )
                            }
                        FrameSongRequestResult.ok(json.encodeToString(FrameSearchJson(songs)))
                    },
                    onFailure = { FrameSongRequestResult(500, "Search failed") },
                )
            } catch (cancelled: CancellationException) {
                FrameSongRequestResult(503, "Search cancelled")
            } catch (_: Exception) {
                FrameSongRequestResult(500, "Search failed")
            }
            deferred.complete(outcome)
        }
        // withTimeoutOrNull also covers the race where close() cancels the
        // scope before the job body ever runs, so the executor cannot hang.
        return runBlocking {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) { deferred.await() }
                ?: FrameSongRequestResult(503, "Search timed out")
        }
    }

    /** Queue a song right after the current one. */
    fun playNext(videoId: String): FrameSongRequestResult = enqueue(videoId, playNext = true)

    /** Queue a song at the end of the queue. */
    fun addToEnd(videoId: String): FrameSongRequestResult = enqueue(videoId, playNext = false)

    /**
     * Remove a queued song by its position in the reported queue. The index is
     * revalidated against the latest queue: it must point past the current
     * song and still hold the expected video, so the currently playing song
     * and stale positions are rejected.
     */
    fun remove(index: Int, videoId: String?): FrameSongRequestResult {
        if (guestProvider()) return FrameSongRequestResult.guestDenied()
        val connection = readyConnection() ?: return FrameSongRequestResult.unavailable()
        val windows = connection.queueWindows.value
        val currentIndex = connection.currentWindowIndex.value
        if (index <= currentIndex || index !in windows.indices) {
            return FrameSongRequestResult(400, "Invalid queue index")
        }
        val window = windows[index]
        if (videoId != null && videoId != window.mediaItem.mediaId) {
            return FrameSongRequestResult(400, "Queue changed, please refresh")
        }
        val periodIndex = window.firstPeriodIndex
        return runBlocking {
            withContext(Dispatchers.Main) {
                try {
                    connection.player.removeMediaItem(periodIndex)
                    FrameSongRequestResult.ok("Removed")
                } catch (_: Exception) {
                    FrameSongRequestResult(500, "Could not remove the song")
                }
            }
        }
    }

    /** Move a queued song to another position in the upcoming queue. */
    fun move(from: Int, to: Int): FrameSongRequestResult {
        if (guestProvider()) return FrameSongRequestResult.guestDenied()
        val connection = readyConnection() ?: return FrameSongRequestResult.unavailable()
        val windows = connection.queueWindows.value
        val currentIndex = connection.currentWindowIndex.value
        if (from == to) return FrameSongRequestResult.ok("Moved")
        if (from <= currentIndex || to <= currentIndex ||
            from !in windows.indices || to !in windows.indices
        ) {
            return FrameSongRequestResult(400, "Invalid queue index")
        }
        val shuffle = connection.shuffleModeEnabled.value
        return runBlocking {
            withContext(Dispatchers.Main) {
                try {
                    val player = connection.player
                    if (!shuffle) {
                        // Timeline index (firstPeriodIndex), not queue position:
                        // the playing song may sit anywhere in the timeline.
                        player.moveMediaItem(windows[from].firstPeriodIndex, windows[to].firstPeriodIndex)
                    } else {
                        // Playback order is the shuffle order itself; rebuild it
                        // with the song moved between queue positions, the same
                        // way the in-app queue does.
                        player.setShuffleOrder(
                            DefaultShuffleOrder(
                                windows.map { it.firstPeriodIndex }.toMutableList()
                                    .move(from, to).toIntArray(),
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                    FrameSongRequestResult.ok("Moved")
                } catch (_: Exception) {
                    FrameSongRequestResult(500, "Could not move the song")
                }
            }
        }
    }

    override fun close() {
        searchScope.cancel()
    }

    private fun readyConnection(): PlayerConnection? {
        val connection = playerProvider() ?: return null
        if (!connection.service.isPlayerReady.value) return null
        return connection
    }

    private fun enqueue(videoId: String, playNext: Boolean): FrameSongRequestResult {
        val id = videoId.trim()
        if (id.isEmpty() || !VIDEO_ID.matches(id)) {
            return FrameSongRequestResult(400, "Invalid videoId")
        }
        if (guestProvider()) return FrameSongRequestResult.guestDenied()
        val connection = readyConnection() ?: return FrameSongRequestResult.unavailable()
        val songs = runBlocking {
            // YouTube's client timeouts are 30s connect / 60s read; a slow
            // fetch must not stall the single receiver thread that also
            // serves photo uploads and state polls.
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(QUEUE_FETCH_TIMEOUT_MS) { YouTube.queue(listOf(id)).getOrNull() }
            }
        } ?: return FrameSongRequestResult(500, "Could not fetch the song")
        val song = songs.firstOrNull { it.id == id } ?: songs.firstOrNull()
            ?: return FrameSongRequestResult(404, "Song not found")
        val item = song.toMediaItem()
        return runBlocking {
            withContext(Dispatchers.Main) {
                try {
                    if (playNext) connection.playNext(item) else connection.addToQueue(item)
                    FrameSongRequestResult.ok(if (playNext) "Playing next" else "Added")
                } catch (_: Exception) {
                    FrameSongRequestResult(500, "Could not queue the song")
                }
            }
        }
    }

    companion object {
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{6,32}")
        private const val MAX_QUERY_LENGTH = 100
        private const val MAX_SEARCH_RESULTS = 20
        private const val SEARCH_TIMEOUT_MS = 8_000L
        private const val QUEUE_FETCH_TIMEOUT_MS = 10_000L
    }
}

/** Song entry shared by the state and search payloads. */
@Serializable
internal data class FrameSongJson(
    val index: Int? = null,
    val videoId: String,
    val title: String,
    val artists: String,
    val duration: Int? = null,
    val thumbnail: String? = null,
)

@Serializable
internal data class FramePlayerStateJson(
    val nowPlaying: FrameSongJson?,
    val queue: List<FrameSongJson>,
)

@Serializable
internal data class FrameSearchJson(
    val songs: List<FrameSongJson>,
)

@Serializable
internal data class FrameVideoIdBody(
    val videoId: String,
)

@Serializable
internal data class FrameRemoveBody(
    val index: Int,
    val videoId: String? = null,
)

@Serializable
internal data class FrameMoveBody(
    val from: Int,
    val to: Int,
)

/** Status and plain-text body the receiver turns into an HTTP response. */
internal data class FrameSongRequestResult(
    val status: Int,
    val body: String,
) {
    companion object {
        fun ok(body: String) = FrameSongRequestResult(200, body)
        fun guestDenied() = FrameSongRequestResult(409, "Listen Together guests cannot change the queue")
        fun unavailable() = FrameSongRequestResult(503, "Player is not running")
    }
}
