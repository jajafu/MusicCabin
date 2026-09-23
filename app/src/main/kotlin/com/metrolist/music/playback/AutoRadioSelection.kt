package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.MediaMetadata
import java.util.Locale
import kotlin.random.Random

internal class QueueNoveltyFilter(existingItems: List<MediaItem>) {
    private val existingIds = existingItems.mapTo(mutableSetOf()) { it.mediaId }
    private val existingSongs = existingItems.mapNotNullTo(mutableSetOf()) { it.metadata?.songKey() }

    fun select(candidates: List<MediaItem>): List<MediaItem> {
        val seenIds = existingIds.toMutableSet()
        val seenSongs = existingSongs.toMutableSet()
        return candidates.filter { item ->
            val songKey = item.metadata?.songKey()
            if (item.mediaId in seenIds || (songKey != null && songKey in seenSongs)) {
                false
            } else {
                seenIds += item.mediaId
                if (songKey != null) seenSongs += songKey
                true
            }
        }
    }
}

internal fun selectRadioSeeds(
    songs: List<MediaMetadata>,
    usedIds: Set<String>,
    limit: Int,
    random: Random = Random.Default,
): List<MediaMetadata> =
    songs
        .asReversed()
        .distinctBy(MediaMetadata::id)
        .filter { it.id !in usedIds && !it.isEpisode && YOUTUBE_VIDEO_ID.matches(it.id) }
        .shuffled(random)
        .take(limit)

private data class SongKey(val title: String, val artists: List<String>)

private fun MediaMetadata.songKey(): SongKey? {
    val normalizedTitle = title.normalizedSongText()
    val normalizedArtists = artists.map { it.name.normalizedSongText() }.filter(String::isNotEmpty).sorted()
    return if (normalizedTitle.isEmpty() || normalizedArtists.isEmpty()) null else SongKey(normalizedTitle, normalizedArtists)
}

private fun String.normalizedSongText(): String = trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

private val WHITESPACE = Regex("\\s+")
private val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
