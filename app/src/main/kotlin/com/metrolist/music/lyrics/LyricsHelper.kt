/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import android.util.LruCache
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

private const val MAX_LYRICS_FETCH_MS = 25000L
private const val PER_PROVIDER_TIMEOUT_MS = 8000L
private const val PROVIDER_NONE = ""

/**
 * How long the frame waits after the first synced answer before picking one, so
 * a better source that lands a moment later still wins.
 */
private const val FRAME_EARLY_GRACE_MS = 400L

/**
 * Word-synced beats line-synced, then the user's provider order decides; a
 * provider outside the configured order sorts last.
 */
private val FRAME_CANDIDATE_PREFERENCE: Comparator<FrameLyricsCandidate> =
    compareByDescending<FrameLyricsCandidate> { LyricsUtils.isWordSynced(it.lyrics) }
        .thenBy { it.priority }

private data class FrameLyricsCandidate(
    val provider: String,
    val lyrics: String,
    val isKtv: Boolean,
    val priority: Int,
)

/** One streamed candidate for the frame's two-stage lyrics fetch. */
data class FrameLyricsResult(
    val early: LyricsWithProvider?,
    val upgrade: LyricsWithProvider?,
)

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    val preferred =
        context.dataStore.data
            .map { preferences ->
                resolveLyricsProviders(preferences)
            }.distinctUntilChanged()

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val preferences = context.dataStore.data.first()
        val orderedProviders = resolveLyricsProviders(preferences)

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        val result = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(mediaMetadata.title)
            val enabledProviders = orderedProviders.filter { it.isEnabled(preferences) }

            Timber.tag("LyricsHelper").d("Starting sequential fetch for: $cleanedTitle by ${mediaMetadata.artists.joinToString { it.name }}")
            Timber.tag("LyricsHelper").d("Enabled providers in order: ${enabledProviders.joinToString { it.name }}")

            for (provider in enabledProviders) {
                Timber.tag("LyricsHelper").d("Trying provider: ${provider.name}")
                val providerResult = try {
                    withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                        provider.getLyrics(
                            context,
                            mediaMetadata.id,
                            cleanedTitle,
                            mediaMetadata.artists.joinToString { it.name },
                            mediaMetadata.duration,
                            mediaMetadata.album?.title,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag("LyricsHelper").w("${provider.name} threw: ${e.message}")
                    null
                }

                if (providerResult != null && providerResult.isSuccess) {
                    Timber.tag("LyricsHelper").i("Got lyrics from ${provider.name}")
                    val filtered = LyricsUtils.filterLyricsCreditLines(providerResult.getOrNull()!!)
                    return@withTimeoutOrNull LyricsWithProvider(filtered, provider.name)
                } else {
                    val errorMsg = providerResult?.exceptionOrNull()?.message ?: "timeout or exception"
                    Timber.tag("LyricsHelper").w("${provider.name} failed: $errorMsg")
                }
            }

            Timber.tag("LyricsHelper").w("No lyrics found after checking all providers")
            LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        return result ?: LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
    }

    /**
     * Frame two-stage fetch in a single parallel pass. Every enabled provider is
     * searched exactly once (KTV providers even while their switch is off, so
     * they join this pass instead of starting a second round of the same
     * requests), and each time-synced candidate is streamed back as it arrives:
     *
     *  - [onEarlySynced] receives the best candidate of a short grace window, so
     *    a higher-priority or word-synced result arriving just after the fastest
     *    one still wins instead of whichever straggler answered first.
     *  - The returned [FrameLyricsResult] carries that early pick plus the first
     *    word-synced LyricsPlus/Paxsenix result for the caller to upgrade to.
     *
     * Everything shares the 25s budget; without a connection the pass is skipped
     * outright so an offline head unit does not burn provider retries per track.
     */
    suspend fun getFrameLyrics(
        mediaMetadata: MediaMetadata,
        onEarlySynced: suspend (LyricsWithProvider) -> Unit,
    ): FrameLyricsResult {
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        if (!isNetworkAvailable) return FrameLyricsResult(early = null, upgrade = null)

        val cleanedTitle = LyricsUtils.cleanTitleForSearch(mediaMetadata.title)
        val artists = mediaMetadata.artists.joinToString { it.name }
        val duration = mediaMetadata.duration
        val album = mediaMetadata.album?.title
        val mediaId = mediaMetadata.id
        val preferences = context.dataStore.data.first()
        val orderedProviders = resolveLyricsProviders(preferences)
        val searchProviders =
            (orderedProviders.filter { it.isEnabled(preferences) } +
                KTV_PROVIDERS.mapNotNull { LyricsProviderRegistry.getProviderByName(it) })
                .distinct()
        val priorityOf = { name: String ->
            orderedProviders.indexOfFirst { it.name == name }.takeIf { it >= 0 } ?: orderedProviders.size
        }

        return withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            coroutineScope {
                val channel = Channel<FrameLyricsCandidate>(Channel.UNLIMITED)
                val stream = launch {
                    try {
                        searchProviders.map { provider ->
                            launch {
                                try {
                                    withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                                        provider.getAllLyrics(context, mediaId, cleanedTitle, artists, duration, album) { lyrics ->
                                            val filtered = LyricsUtils.filterLyricsCreditLines(lyrics)
                                            if (filtered.isBlank() || filtered == LYRICS_NOT_FOUND) return@getAllLyrics
                                            if (!lyricsTextLooksSynced(filtered)) return@getAllLyrics
                                            channel.trySend(
                                                FrameLyricsCandidate(
                                                    provider = provider.name,
                                                    lyrics = filtered,
                                                    isKtv = provider.name in KTV_PROVIDERS,
                                                    priority = priorityOf(provider.name),
                                                ),
                                            )
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                }
                            }
                        }.joinAll()
                    } finally {
                        channel.close()
                    }
                }

                // Collect a short window after the first answer so source order
                // and word-sync level can beat raw arrival order.
                val collected = mutableListOf<FrameLyricsCandidate>()
                channel.receiveCatching().getOrNull()?.let { first ->
                    collected += first
                    withTimeoutOrNull(FRAME_EARLY_GRACE_MS) {
                        while (true) {
                            collected += channel.receiveCatching().getOrNull() ?: break
                        }
                    }
                }

                val early = collected.minWithOrNull(FRAME_CANDIDATE_PREFERENCE)
                    ?.let { LyricsWithProvider(it.lyrics, it.provider) }
                if (early != null) {
                    Timber.tag("LyricsHelper").d("Frame lyrics from ${early.provider} (early)")
                    try {
                        onEarlySynced(early)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }

                var upgrade = collected
                    .filter { it.isKtv && LyricsUtils.isWordSynced(it.lyrics) }
                    .minByOrNull { it.priority }
                    ?.let { LyricsWithProvider(it.lyrics, it.provider) }
                if (upgrade == null) {
                    for (candidate in channel) {
                        if (candidate.isKtv && LyricsUtils.isWordSynced(candidate.lyrics)) {
                            upgrade = LyricsWithProvider(candidate.lyrics, candidate.provider)
                            break
                        }
                    }
                }
                if (upgrade != null) {
                    Timber.tag("LyricsHelper").i("Frame KTV upgrade from ${upgrade.provider}")
                } else if (early != null) {
                    Timber.tag("LyricsHelper").d("Frame early pick from ${early.provider}; no KTV upgrade")
                } else {
                    Timber.tag("LyricsHelper").w("Frame found no synced lyrics")
                }
                stream.cancel()
                FrameLyricsResult(early = early, upgrade = upgrade)
            }
        } ?: FrameLyricsResult(early = null, upgrade = null)
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach { callback(it) }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) return

        val allResult = mutableListOf<LyricsResult>()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            val completed = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
                val cleanedTitle = LyricsUtils.cleanTitleForSearch(songTitle)
                val preferences = context.dataStore.data.first()
                val allProviders = resolveLyricsProviders(preferences)
                val enabledProviders = allProviders.filter { it.isEnabled(preferences) }

                val otherProviders = enabledProviders.filter { it.name != "LyricsPlus" }
                val lyricsPlusProvider = enabledProviders.find { it.name == "LyricsPlus" }

                val callbackMutex = Any()

                val fetchProviderResults: suspend (LyricsProvider) -> Unit = { provider ->
                    try {
                        val providerCompleted = withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                            provider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                                val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                                val result = LyricsResult(provider.name, filteredLyrics)
                                synchronized(callbackMutex) {
                                    allResult += result
                                    callback(result)
                                }
                            }
                            true
                        }
                        if (providerCompleted == null) {
                            Timber.tag("LyricsHelper").w("${provider.name} all-lyrics search timed out")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }

                val otherJobs = otherProviders.map { provider ->
                    launch { fetchProviderResults(provider) }
                }
                otherJobs.forEach { it.join() }

                // Always query LyricsPlus when enabled; KuGou can return many candidates
                // and must not suppress another provider from the manual source list.
                lyricsPlusProvider?.let { provider ->
                    fetchProviderResults(provider)
                }

                cache.put(cacheKey, allResult)
                true
            }
            if (completed == null) {
                Timber.tag("LyricsHelper").w("All-lyrics search timed out")
            }
        }

        currentLyricsJob?.join()
    }

    private fun resolveLyricsProviders(preferences: androidx.datastore.preferences.core.Preferences): List<LyricsProvider> {
        val providerOrder = preferences[LyricsProviderOrderKey].orEmpty()
        if (providerOrder.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(providerOrder)
        }

        return LyricsProviderRegistry.getDefaultProviderOrder()
            .mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
        private val KTV_PROVIDERS = listOf("LyricsPlus", "Paxsenix")
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
