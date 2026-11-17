/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import com.metrolist.music.constants.AndroidAutoMixesCacheKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Offline cache of the Android Auto "Mixes" (YouTube suggested playlists) list.
 *
 * The Mixes node is normally built from live `YouTube.home()` calls and is empty when the
 * device is offline. This cache keeps the last successfully fetched list (DataStore JSON,
 * no Room schema involved) so the car can still show the node without a network connection.
 */
object AndroidAutoMixesCache {
    @Serializable
    data class Entry(
        val id: String,
        val title: String,
        val author: String?,
        val thumbnail: String?,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Entry.serializer())

    /** Bounded so the DataStore value stays small even if YouTube returns many sections. */
    const val MAX_ENTRIES = 30

    /** Pure JSON round-trip halves, kept internal so the format contract stays unit-testable. */
    internal fun decodeCacheEntries(raw: String?): List<Entry> =
        runCatching {
            if (raw.isNullOrBlank()) emptyList() else json.decodeFromString(serializer, raw)
        }.getOrDefault(emptyList())

    internal fun encodeCacheEntries(items: List<Entry>): String =
        json.encodeToString(serializer, items.take(MAX_ENTRIES))

    suspend fun load(context: Context): List<Entry> =
        runCatching {
            decodeCacheEntries(context.dataStore.data.first()[AndroidAutoMixesCacheKey])
        }.onFailure { reportException(it) }
            .getOrDefault(emptyList())

    suspend fun save(context: Context, items: List<Entry>) {
        if (items.isEmpty()) return
        // safeDataStoreEdit re-creates the datastore directory (Samsung One UI 8.5+/Android 16
        // deep-sleep issue) and reports IOExceptions instead of failing silently.
        context.safeDataStoreEdit { prefs ->
            prefs[AndroidAutoMixesCacheKey] = encodeCacheEntries(items)
        }
    }
}
