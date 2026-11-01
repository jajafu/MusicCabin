/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks (videoId, clientName) pairs whose stream was cut mid-delivery (EOF) during playback, so
 * the next resolution for that video skips the failed client and falls through to the next client
 * in the fallback chain. Same idea as the WEB_REMIX 403 marker in [YTPlayerUtils], but covering
 * every client: YouTube sometimes serves only the first portion of a stream from a client it no
 * longer fully trusts (per-track/per-CDN), which surfaces as an EOFException under
 * ERROR_CODE_IO_UNSPECIFIED and previously retried the same client until the retry limit hit.
 *
 * Entries expire after [ttlMs] (the cutting is often transient) and the map is bounded, so memory
 * stays negligible and a healthy client is retried again after a while.
 */
internal class StreamClientFailureTracker(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val failures = ConcurrentHashMap<String, Long>()

    fun markFailed(videoId: String, clientName: String) {
        if (videoId.isBlank() || clientName.isBlank()) return
        pruneExpired()
        if (failures.size >= maxEntries) return // bounded: drop new marks under extreme churn
        failures[key(videoId, clientName)] = clock() + ttlMs
    }

    fun isFailed(videoId: String, clientName: String): Boolean {
        if (videoId.isBlank() || clientName.isBlank()) return false
        val expiry = failures[key(videoId, clientName)] ?: return false
        if (expiry <= clock()) {
            failures.remove(key(videoId, clientName), expiry)
            return false
        }
        return true
    }

    /**
     * The client list to resolve with: clients marked failed for this video are filtered out so
     * playback falls through to the next one. If that would leave the chain empty (every client
     * marked), the full list is returned instead — retrying a possibly-recovered client beats
     * failing the song outright.
     */
    fun <T> filterFailed(
        videoId: String,
        clients: List<T>,
        clientName: (T) -> String,
    ): List<T> {
        if (videoId.isBlank() || clients.isEmpty()) return clients
        val usable = clients.filter { !isFailed(videoId, clientName(it)) }
        return usable.ifEmpty { clients }
    }

    private fun pruneExpired() {
        if (failures.isEmpty() || failures.size < maxEntries) return
        val now = clock()
        failures.entries.removeIf { it.value <= now }
    }

    private fun key(
        videoId: String,
        clientName: String,
    ): String = "$videoId|$clientName"

    companion object {
        const val DEFAULT_TTL_MS = 10 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES = 500
    }
}
