package com.metrolist.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Test

class YTItemFilterTest {
    private val artist = Artist("AI Generated", "artist-ai")
    private val song = SongItem(
        id = "song",
        title = "Song",
        artists = listOf(artist),
        thumbnail = "https://example.test/song.jpg",
    )

    @Test
    fun `podcast filter removes podcast items episodes and podcast songs`() {
        val items = listOf(
            PodcastItem("podcast", "Podcast", artist, null, null, null, null, null),
            EpisodeItem("episode", "Episode", artist, thumbnail = "https://example.test/episode.jpg"),
            song.copy(isEpisode = true),
            song.copy(isEpisode = false),
        )

        assertEquals(listOf(song.copy(isEpisode = false)), items.filterPodcasts(true))
    }

    @Test
    fun `podcast filter removes podcast playlists`() {
        val podcastPlaylist = PlaylistItem(
            id = "podcast-playlist",
            title = "Podcast playlist",
            author = artist,
            songCountText = null,
            thumbnail = null,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
            isPodcast = true,
        )
        assertEquals(emptyList<PlaylistItem>(), listOf(podcastPlaylist).filterPodcasts(true))
    }

    @Test
    fun `hidden artist filter removes matching artist and songs`() {
        val matchingArtist = ArtistItem("artist-ai", "AI Generated", null, null, null, null, null)
        assertEquals(listOf(matchingArtist, song), listOf(matchingArtist, song).filterHiddenArtists(setOf("other")))
        assertEquals(emptyList<SongItem>(), listOf(song).filterHiddenArtists(setOf("artist-ai")))
    }

    @Test
    fun `ai filter matches explicit generated artist wording without hiding normal artists`() {
        val normal = song.copy(artists = listOf(Artist("The Band", "normal")))
        assertEquals(listOf(normal), listOf(song, normal).filterAiArtists(true))
    }

    @Test
    fun `ai filter does not hide real artist named Ai`() {
        val realArtist = song.copy(artists = listOf(Artist("Ai", "real-ai")))
        assertEquals(listOf(realArtist), listOf(realArtist).filterAiArtists(true))
    }

    @Test
    fun `ai filter matches Chinese generated artist phrases`() {
        val items = listOf(
            song.copy(artists = listOf(Artist("AI生成音樂", "generated"))),
            song.copy(artists = listOf(Artist("AI歌手小美", "singer"))),
            song.copy(artists = listOf(Artist("某AI翻唱", "cover"))),
        )
        assertEquals(emptyList<SongItem>(), items.filterAiArtists(true))
    }

    @Test
    fun `ai filter matches common simplified and dotted forms`() {
        val items = listOf(
            song.copy(artists = listOf(Artist("AI音乐", "music"))),
            song.copy(artists = listOf(Artist("AI虚拟歌手", "virtual"))),
            song.copy(artists = listOf(Artist("A.I. Music", "dotted"))),
            song.copy(artists = listOf(Artist("AI　歌手", "full-width-space"))),
        )
        assertEquals(emptyList<SongItem>(), items.filterAiArtists(true))
    }
}
