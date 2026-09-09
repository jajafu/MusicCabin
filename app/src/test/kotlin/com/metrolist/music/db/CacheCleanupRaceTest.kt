package com.metrolist.music.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CacheCleanupRaceTest {
    private lateinit var database: InternalDatabase
    private val originalDate = LocalDateTime.of(2026, 9, 9, 10, 0)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InternalDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `stale cache cleanup preserves concurrent download completion`() = runBlocking {
        val downloaded = SongEntity(id = "song", title = "Edited", liked = true,
            dateDownload = originalDate.plusMinutes(1), isDownloaded = true)
        database.dao.insert(downloaded)
        database.dao.clearStaleCacheDate(downloaded.id, originalDate)
        database.dao.clearCacheDate(downloaded.id)
        assertEquals(downloaded, database.dao.songEntity(downloaded.id))
    }

    @Test
    fun `stale cache cleanup only clears the observed date`() = runBlocking {
        val song = SongEntity(id = "song", title = "Edited", liked = true, dateDownload = originalDate)
        database.dao.insert(song)
        database.dao.clearStaleCacheDate(song.id, originalDate.minusMinutes(1))
        assertEquals(song, database.dao.songEntity(song.id))
        database.dao.clearStaleCacheDate(song.id, originalDate)
        assertEquals(song.copy(dateDownload = null), database.dao.songEntity(song.id))
    }

    @Test
    fun `editing a title preserves download and like state`() = runBlocking {
        val song = SongEntity(id = "song", title = "Original", liked = true,
            dateDownload = originalDate, isDownloaded = true)
        database.dao.insert(song)
        database.dao.updateSongTitle(song.id, "Edited")
        assertEquals(song.copy(title = "Edited"), database.dao.songEntity(song.id))
    }

    @Test
    fun `subscribing preserves the artist page cache`() {
        val artist = ArtistEntity(id = "artist", name = "Artist", cachedPageJson = "{\"title\":\"Page\"}")
        database.dao.insert(artist)
        database.dao.updateArtistBookmark(artist.id, originalDate)
        val saved = database.dao.getArtistById(artist.id)!!
        assertEquals(artist.cachedPageJson, saved.cachedPageJson)
        assertTrue(saved.bookmarkedAt != null)
    }
}
