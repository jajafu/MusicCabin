package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.SearchSummary
import com.metrolist.music.models.MediaMetadata

internal fun rankVoiceSearchSongs(
    summaries: List<SearchSummary>,
    topResultSectionTitle: String,
): List<SongItem> =
    summaries
        .asSequence()
        .filterNot { it.title == topResultSectionTitle }
        .flatMap { it.items.asSequence() }
        .filterIsInstance<SongItem>()
        .filterNot(SongItem::isEpisode)
        .distinctBy(SongItem::id)
        .toList()

internal fun orderSearchMetadata(
    online: List<MediaMetadata>,
    local: List<MediaMetadata>,
    prioritizeOnline: Boolean,
): List<MediaMetadata> =
    (if (prioritizeOnline) online + local else local + online)
        .distinctBy(MediaMetadata::id)

/**
 * 純字串處理：把「周杰倫的歌」「周杰倫歌」這類結尾清掉，只留歌手名。
 * 放這裡（而不是 Receiver 裡）是為了能寫單元測試。
 */
internal fun cleanVoiceArtistQuery(query: String): String {
    var name = query.trim()
    for (suffix in listOf("的歌曲", "的音樂", "的歌", "歌曲", "音樂")) {
        if (name.endsWith(suffix) && name.length > suffix.length) {
            name = name.removeSuffix(suffix).trim()
            break
        }
    }
    // 「周杰倫歌」這種少一個「的」的口語也要吃；名字本來就只有一個字時不動。
    if (name.endsWith("歌") && name.length > 1) {
        name = name.removeSuffix("歌").trim()
    }
    return name
}

/**
 * 從一首歌的歌手名單裡，找出跟 [needle] 對上的那一位。
 * 完全相同優先，其次才接受包含；只有一個字時只接受完全相同，
 * 否則一個字會對上幾乎所有歌手。
 */
internal fun findVoiceArtistMatch(artistNames: List<String>, needle: String): String? {
    val want = needle.trim().lowercase()
    if (want.isEmpty()) return null
    val names = artistNames.map { it.trim() }.filter { it.isNotEmpty() }
    names.firstOrNull { it.lowercase() == want }?.let { return it }
    if (want.length < 2) return null
    return names.firstOrNull { it.lowercase().contains(want) }
}

/**
 * 只留歌手對得上的歌，並把完全相同的排前面；
 * 一首都對不上就回空集合，由呼叫方決定要不要退回全部結果。
 */
internal fun <T> rankVoiceArtistSongs(
    songs: List<T>,
    needle: String,
    artistNamesOf: (T) -> List<String>,
): List<T> =
    songs
        .filter { findVoiceArtistMatch(artistNamesOf(it), needle) != null }
        .sortedByDescending { song ->
            artistNamesOf(song).any { it.trim().lowercase() == needle.trim().lowercase() }
        }

internal data class VoiceRadioMediaPlan(
    val items: List<MediaItem>,
    val startIndex: Int,
)

internal fun buildVoiceRadioMediaPlan(
    seed: MediaItem,
    radioItems: List<MediaItem>,
): VoiceRadioMediaPlan =
    VoiceRadioMediaPlan(
        items = listOf(seed) + radioItems.filterNot { it.mediaId == seed.mediaId },
        startIndex = 0,
    )
