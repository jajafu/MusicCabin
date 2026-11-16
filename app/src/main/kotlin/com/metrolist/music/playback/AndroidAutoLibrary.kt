package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.sqlite.db.SimpleSQLiteQuery
import com.metrolist.music.db.DatabaseDao
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem

internal const val CAR_PAGE_SIZE = 100

internal data class CarPage(val parentId: String, val offset: Long, val size: Int, val showMore: Boolean) {
    fun nextId(itemCount: Int): String = "$PREFIX${offset + itemCount}/${Uri.encode(parentId)}"

    companion object {
        private const val PREFIX = "android_auto_page/"

        fun from(parentId: String, page: Int, pageSize: Int): CarPage? {
            if (page < 0 || pageSize <= 0) return null
            val parts = parentId.removePrefix(PREFIX).split('/', limit = 2)
            val baseOffset = if (parentId.startsWith(PREFIX)) {
                parts.first().toLongOrNull()?.takeIf { it >= 0 } ?: return null
            } else 0L
            val source = if (parentId.startsWith(PREFIX)) {
                parts.getOrNull(1)?.let(Uri::decode) ?: return null
            } else parentId
            val pageOffset = page.toLong() * pageSize
            if (baseOffset > Long.MAX_VALUE - pageOffset - CAR_PAGE_SIZE) return null
            return CarPage(source, baseOffset + pageOffset, pageSize.coerceAtMost(CAR_PAGE_SIZE), pageSize > CAR_PAGE_SIZE)
        }
    }
}

internal class AndroidAutoLibrary(
    private val database: DatabaseDao,
    private val completedDownloadIds: () -> List<String>,
) {
    suspend fun songIds(parentId: String, limit: Int = -1, offset: Long = 0): List<String> {
        if (parentId == "${MusicService.PLAYLIST}/${PlaylistEntity.DOWNLOADED_PLAYLIST_ID}") {
            return completedDownloadIds().slicePage(offset, limit)
        }
        val args = mutableListOf<Any>()
        val sql = when {
            parentId == MusicService.SONG ->
                "SELECT id FROM song WHERE inLibrary IS NOT NULL ORDER BY inLibrary, rowId"
            parentId == "${MusicService.PLAYLIST}/${PlaylistEntity.LIKED_PLAYLIST_ID}" ->
                // Follow the local custom order when one exists; fall back to liked date.
                "SELECT id FROM song WHERE liked ORDER BY likedOrder IS NULL, likedOrder, likedDate DESC, rowId DESC"
            parentId.startsWith("${MusicService.ARTIST}/") -> {
                args.add(parentId.removePrefix("${MusicService.ARTIST}/"))
                """SELECT song.id FROM song_artist_map JOIN song ON song.id = songId
                    WHERE artistId = ? AND inLibrary IS NOT NULL ORDER BY inLibrary, song.rowId"""
            }
            parentId.startsWith("${MusicService.ALBUM}/") -> {
                args.add(parentId.removePrefix("${MusicService.ALBUM}/"))
                """SELECT song.id FROM song_album_map JOIN song ON song.id = songId
                    WHERE song_album_map.albumId = ? ORDER BY song_album_map.`index`, song.rowId"""
            }
            parentId.startsWith("${MusicService.PLAYLIST}/") -> {
                args.add(parentId.removePrefix("${MusicService.PLAYLIST}/"))
                """SELECT songId FROM playlist_song_map JOIN song ON song.id = songId
                    WHERE playlistId = ? ORDER BY position, playlist_song_map.id"""
            }
            else -> return emptyList()
        }
        args.add(limit)
        args.add(offset)
        return database.androidAutoSongIds(SimpleSQLiteQuery("$sql LIMIT ? OFFSET ?", args.toTypedArray()))
    }

    suspend fun songs(ids: List<String>): List<Song> {
        require(ids.size <= CAR_PAGE_SIZE + 1)
        val songsById = database.getSongsByIds(ids).associateBy { it.id }
        // A playlist can contain the same song more than once; the ID query's order is authoritative.
        return ids.mapNotNull(songsById::get)
    }

    suspend fun queueItems(ids: List<String>): List<MediaItem> = buildList {
        // Keep only one batch of Room relation objects alive while constructing the playback queue.
        for (offset in ids.indices step CAR_PAGE_SIZE) {
            addAll(songs(ids.subList(offset, minOf(offset + CAR_PAGE_SIZE, ids.size))).map { it.toMediaItem() })
        }
    }

    suspend fun search(query: String, limit: Int = CAR_PAGE_SIZE, offset: Long = 0): List<Song> {
        require(limit in 1..CAR_PAGE_SIZE)
        val pattern = "%${query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        val ids = database.androidAutoSongIds(
            SimpleSQLiteQuery(
                """
                    SELECT s.id FROM song s WHERE s.title LIKE ? ESCAPE '\'
                    OR EXISTS (SELECT 1 FROM song_artist_map m JOIN artist a ON a.id = m.artistId
                        WHERE m.songId = s.id AND a.name LIKE ? ESCAPE '\')
                    OR EXISTS (SELECT 1 FROM song_album_map m JOIN album a ON a.id = m.albumId
                        WHERE m.songId = s.id AND a.title LIKE ? ESCAPE '\')
                    OR EXISTS (SELECT 1 FROM playlist_song_map m JOIN playlist p ON p.id = m.playlistId
                        WHERE m.songId = s.id AND p.name LIKE ? ESCAPE '\')
                    ORDER BY s.rowId LIMIT ? OFFSET ?
                """.trimIndent(),
                arrayOf<Any>(pattern, pattern, pattern, pattern, limit, offset),
            )
        )
        return songs(ids)
    }
}

internal fun <T> List<T>.slicePage(offset: Long, limit: Int): List<T> {
    if (offset < 0 || offset >= size) return emptyList()
    val end = if (limit < 0) size else minOf(size.toLong(), offset + limit).toInt()
    return subList(offset.toInt(), end)
}

internal suspend fun <T> loadCarPage(
    prefix: List<T>,
    offset: Long,
    limit: Int,
    load: suspend (limit: Int, offset: Long) -> List<T>,
): List<T> {
    val header = prefix.slicePage(offset, limit)
    return if (header.size == limit) header else {
        header + load(limit - header.size, (offset - prefix.size).coerceAtLeast(0))
    }
}
