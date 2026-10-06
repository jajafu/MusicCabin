/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.music.constants.ManualLyricsKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.flow.first

private const val MAX_MANUAL_LYRICS_IDS = 500

/**
 * Records that the user explicitly picked lyrics for [mediaId]. The photo
 * frame KTV auto-refetch must yield to these picks instead of overwriting
 * them with another provider's result. No database schema change needed.
 */
suspend fun Context.markManualLyrics(mediaId: String) {
    safeDataStoreEdit { settings ->
        val current = settings[ManualLyricsKey] ?: emptySet()
        settings[ManualLyricsKey] = if (current.size >= MAX_MANUAL_LYRICS_IDS) {
            setOf(mediaId)
        } else {
            current + mediaId
        }
    }
}

suspend fun Context.isManualLyrics(mediaId: String): Boolean =
    dataStore.data.first()[ManualLyricsKey]?.contains(mediaId) == true
