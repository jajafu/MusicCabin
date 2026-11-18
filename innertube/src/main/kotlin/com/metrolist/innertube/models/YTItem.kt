package com.metrolist.innertube.models

import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV

sealed class YTItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnail: String?
    abstract val explicit: Boolean
    abstract val shareLink: String
}

data class Artist(
    val name: String,
    val id: String?,
)

data class Album(
    val name: String,
    val id: String,
)

data class SongItem(
    override val id: String,
    override val title: String,
    val artists: List<Artist>,
    val album: Album? = null,
    val duration: Int? = null,
    val musicVideoType: String? = null,
    val chartPosition: Int? = null,
    val chartChange: String? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val endpoint: WatchEndpoint? = null,
    val setVideoId: String? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val historyRemoveToken: String? = null,
    val isEpisode: Boolean = false,
    val uploadEntityId: String? = null
) : YTItem() {
    val isVideoSong: Boolean
        get() = musicVideoType != null && musicVideoType != MUSIC_VIDEO_TYPE_ATV

    override val shareLink: String
        get() = "https://music.youtube.com/watch?v=$id"
}

data class AlbumItem(
    val browseId: String,
    val playlistId: String,
    override val id: String = browseId,
    override val title: String,
    val artists: List<Artist>?,
    val year: Int? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$playlistId"
}

data class PlaylistItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val songCountText: String?,
    override val thumbnail: String?,
    val playEndpoint: WatchEndpoint?,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val isEditable: Boolean = false,
    val isPodcast: Boolean = false,
    val description: String? = null,
    val authorAvatarUrl: String? = null,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$id"
}

data class ArtistItem(
    override val id: String,
    override val title: String,
    override val thumbnail: String?,
    val channelId: String? = null,
    val playEndpoint: WatchEndpoint? = null,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val isProfile: Boolean = false,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/channel/$id"
}

data class PodcastItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val episodeCountText: String?,
    override val thumbnail: String?,
    val playEndpoint: WatchEndpoint?,
    val shuffleEndpoint: WatchEndpoint?,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val channelId: String? = null,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$id"

    fun asPlaylistItem() = PlaylistItem(
        id = id,
        title = title,
        author = author,
        songCountText = episodeCountText,
        thumbnail = thumbnail,
        playEndpoint = playEndpoint,
        shuffleEndpoint = shuffleEndpoint,
        radioEndpoint = null,
        isEditable = false,
        isPodcast = true
    )
}

data class EpisodeItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val podcast: Album? = null,
    val duration: Int? = null,
    val publishDateText: String? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val endpoint: WatchEndpoint? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val markAsPlayedToken: String? = null,
    val markAsUnplayedToken: String? = null,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/watch?v=$id"

    fun asSongItem() = SongItem(
        id = id,
        title = title,
        artists = listOfNotNull(author),
        album = podcast,
        duration = duration,
        thumbnail = thumbnail,
        explicit = explicit,
        endpoint = endpoint,
        isEpisode = true,
        libraryAddToken = libraryAddToken,
        libraryRemoveToken = libraryRemoveToken,
    )
}

fun <T : YTItem> List<T>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filter { !it.explicit }
    } else {
        this
    }

fun <T : YTItem> List<T>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it is SongItem && it.isVideoSong }
    } else {
        this
    }

fun <T : YTItem> List<T>.filterYoutubeShorts(enabled: Boolean = false) =
    if (enabled) {
        filterNot { it is PlaylistItem && it.id.startsWith("SS") }
    } else {
        this
    }

private val aiArtistNamePattern = Regex(
    """(?<![A-Za-z0-9])(?:ai|a[.\s]*i)[\s.\u3000_-]*(?:(?:generated|music|artist|cover)(?![A-Za-z0-9])|歌手|生成|翻唱|語音|音樂|音乐|语音|作曲|演唱|合成|虚拟|人工智能)""",
    RegexOption.IGNORE_CASE,
)

private fun Artist?.isHidden(hiddenArtistIds: Set<String>) = this?.id in hiddenArtistIds

private fun YTItem.hasHiddenArtist(hiddenArtistIds: Set<String>): Boolean = when (this) {
    is AlbumItem -> artists.orEmpty().any { it.id in hiddenArtistIds }
    is ArtistItem -> id in hiddenArtistIds
    is EpisodeItem -> author.isHidden(hiddenArtistIds)
    is PlaylistItem -> author.isHidden(hiddenArtistIds)
    is PodcastItem -> author.isHidden(hiddenArtistIds)
    is SongItem -> artists.any { it.id in hiddenArtistIds }
}

private fun YTItem.hasAiArtistName(): Boolean = when (this) {
    is AlbumItem -> artists.orEmpty().any { aiArtistNamePattern.containsMatchIn(it.name) }
    is ArtistItem -> aiArtistNamePattern.containsMatchIn(title)
    is EpisodeItem -> author?.let { aiArtistNamePattern.containsMatchIn(it.name) } == true
    is PlaylistItem -> author?.let { aiArtistNamePattern.containsMatchIn(it.name) } == true
    is PodcastItem -> author?.let { aiArtistNamePattern.containsMatchIn(it.name) } == true
    is SongItem -> artists.any { aiArtistNamePattern.containsMatchIn(it.name) }
}

fun <T : YTItem> List<T>.filterPodcasts(enabled: Boolean = false) =
    if (enabled) filterNot { it is PodcastItem || it is EpisodeItem || (it is SongItem && it.isEpisode) || (it is PlaylistItem && it.isPodcast) } else this

fun <T : YTItem> List<T>.filterHiddenArtists(hiddenArtistIds: Set<String> = emptySet()) =
    if (hiddenArtistIds.isEmpty()) this else filterNot { it.hasHiddenArtist(hiddenArtistIds) }

fun <T : YTItem> List<T>.filterAiArtists(enabled: Boolean = false) =
    if (enabled) filterNot { it.hasAiArtistName() } else this
