package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import com.metrolist.music.models.MediaMetadata
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoRadioSelectionTest {
    @Test
    fun `radio excludes old songs across video IDs and duplicates within its response`() {
        val oldSong = item("old-video-1", "Same Song", "The Artist")
        val freshSong = item("new-video-1", "Fresh Song", "The Artist")
        val candidates = listOf(
            item("old-video-2", " same  song ", "the artist"),
            freshSong,
            freshSong,
            item("new-video-2", "Fresh Song", "The Artist"),
            item("other-video", "Another Song", "Other Artist"),
        )

        val selected = QueueNoveltyFilter(listOf(oldSong)).select(candidates)

        assertEquals(listOf("new-video-1", "other-video"), selected.map(MediaItem::mediaId))
    }

    @Test
    fun `playlist continuation also excludes old songs under new video IDs`() {
        val oldSong = item("old-video-1", "Same Song", "The Artist")
        val candidates = listOf(
            item("new-video-1", "Same Song", "The Artist"),
            item("fresh-video", "New Song", "The Artist"),
        )

        val selected = QueueNoveltyFilter(listOf(oldSong)).select(candidates)

        assertEquals(listOf("fresh-video"), selected.map(MediaItem::mediaId))
    }

    @Test
    fun `radio seed selection skips used local and podcast items`() {
        val songs = listOf(
            song("AAAAAAAAAAA", "A", "Artist"),
            song("BBBBBBBBBBB", "B", "Artist"),
            song("CCCCCCCCCCC", "C", "Artist", isEpisode = true),
            song("local:track", "Local", "Artist"),
            song("BBBBBBBBBBB", "B", "Artist"),
            song("DDDDDDDDDDD", "D", "Artist"),
        )

        val selected = selectRadioSeeds(songs, setOf("AAAAAAAAAAA"), limit = 3, random = Random(7))

        assertEquals(setOf("BBBBBBBBBBB", "DDDDDDDDDDD"), selected.map(MediaMetadata::id).toSet())
        assertEquals(2, selected.size)
    }

    private fun item(id: String, title: String, artist: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(id)
            .setTag(song(id, title, artist))
            .build()

    private fun song(id: String, title: String, artist: String, isEpisode: Boolean = false) =
        MediaMetadata(
            id = id,
            title = title,
            artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
            duration = 180,
            isEpisode = isEpisode,
        )
}
