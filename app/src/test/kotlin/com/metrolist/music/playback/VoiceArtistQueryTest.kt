package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceArtistQueryTest {
    @Test
    fun `strips song suffixes and keeps the artist name`() {
        assertEquals("周杰倫", cleanVoiceArtistQuery("周杰倫的歌"))
        assertEquals("周杰倫", cleanVoiceArtistQuery("周杰倫歌"))
        assertEquals("周杰倫", cleanVoiceArtistQuery("周杰倫的歌曲"))
        assertEquals("周杰倫", cleanVoiceArtistQuery("周杰倫的音樂"))
        assertEquals("周杰倫", cleanVoiceArtistQuery("  周杰倫  "))
    }

    @Test
    fun `single character name is kept as is`() {
        assertEquals("歌", cleanVoiceArtistQuery("歌"))
    }

    @Test
    fun `exact artist match wins over partial match`() {
        val songs =
            listOf(
                "周杰倫合唱團" to listOf("周杰倫合唱團"),
                "周杰倫" to listOf("周杰倫"),
            )
        val ranked = rankVoiceArtistSongs(songs, "周杰倫") { it.second }

        assertEquals("周杰倫", ranked.first().first)
    }

    @Test
    fun `reports the matched co-artist instead of the first one`() {
        val match = findVoiceArtistMatch(listOf("陳奕迅", "周杰倫"), "周杰倫")

        assertEquals("周杰倫", match)
    }

    @Test
    fun `single character only matches exact artist`() {
        assertNull(findVoiceArtistMatch(listOf("田馥甄"), "甄"))
        assertEquals("甄", findVoiceArtistMatch(listOf("甄"), "甄"))
    }

    @Test
    fun `no match returns empty list for caller fallback`() {
        val songs = listOf("夜曲" to listOf("周杰倫"))

        assertEquals(
            emptyList<Pair<String, List<String>>>(),
            rankVoiceArtistSongs(songs, "五月天") { it.second },
        )
    }
}
