package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-format tests for the Android Auto offline caches/contracts introduced in 13.8.1:
 * the Mixes cache JSON round-trip (corrupted input must degrade to an empty list, never throw)
 * and the `youtube_playlist/<browseId>/<videoId>` mediaId scheme that the car browse tree and
 * the onSetMediaItems playback resolution rely on.
 */
class AndroidAutoMixesCacheTest {

    private fun entry(id: String) =
        AndroidAutoMixesCache.Entry(id = id, title = "Title $id", author = "Author $id", thumbnail = "https://i/$id.jpg")

    @Test
    fun `round trip preserves entries`() {
        val items = listOf(entry("PL1"), entry("PL2"), entry("PL3"))
        val decoded = AndroidAutoMixesCache.decodeCacheEntries(AndroidAutoMixesCache.encodeCacheEntries(items))
        assertEquals(items, decoded)
    }

    @Test
    fun `corrupted or blank json decodes to empty list`() {
        assertTrue(AndroidAutoMixesCache.decodeCacheEntries(null).isEmpty())
        assertTrue(AndroidAutoMixesCache.decodeCacheEntries("").isEmpty())
        assertTrue(AndroidAutoMixesCache.decodeCacheEntries("   ").isEmpty())
        assertTrue(AndroidAutoMixesCache.decodeCacheEntries("not json at all").isEmpty())
        assertTrue(AndroidAutoMixesCache.decodeCacheEntries("""[{"id": 1}]""").isEmpty())
    }

    @Test
    fun `encode caps at max entries keeping the first ones`() {
        val items = (1..(AndroidAutoMixesCache.MAX_ENTRIES + 5)).map { entry("PL$it") }
        val decoded = AndroidAutoMixesCache.decodeCacheEntries(AndroidAutoMixesCache.encodeCacheEntries(items))
        assertEquals(AndroidAutoMixesCache.MAX_ENTRIES, decoded.size)
        assertEquals("PL1", decoded.first().id)
        assertEquals("PL${AndroidAutoMixesCache.MAX_ENTRIES}", decoded.last().id)
    }

    @Test
    fun `online song mediaId resolves back through the YOUTUBE_PLAYLIST path`() {
        val mediaId = carOnlineSongMediaId("PLabc123", "dQw4w9WgXcQ")
        assertEquals("youtube_playlist/PLabc123/dQw4w9WgXcQ", mediaId)

        // onSetMediaItems splits the mediaId by "/" and dispatches on path[0],
        // reads playlistId from path[1] and songId from path[2].
        val path = mediaId.split("/")
        assertEquals(MusicService.YOUTUBE_PLAYLIST, path[0])
        assertEquals("PLabc123", path[1])
        assertEquals("dQw4w9WgXcQ", path[2])
    }
}
