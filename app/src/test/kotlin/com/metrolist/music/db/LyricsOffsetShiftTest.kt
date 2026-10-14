/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LyricsOffsetShiftTest {
    private lateinit var database: InternalDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertSong(id: String, lyricsOffset: Int = 0) =
        withContext(Dispatchers.IO) {
            database.dao.insert(SongEntity(id = id, title = "Song", lyricsOffset = lyricsOffset))
        }

    /** Room rejects blocking DAO calls on the main thread, so every write goes through IO. */
    private suspend fun shift(songId: String, delta: Int) =
        withContext(Dispatchers.IO) { database.dao.shiftLyricsOffset(songId, delta) }

    private suspend fun reset(songId: String) =
        withContext(Dispatchers.IO) { database.dao.resetLyricsOffset(songId) }

    private suspend fun offsetOf(songId: String): Int? =
        withContext(Dispatchers.IO) { database.dao.songEntity(songId)?.lyricsOffset }

    private suspend fun totalPlayTimeOf(songId: String): Long? =
        withContext(Dispatchers.IO) { database.dao.songEntity(songId)?.totalPlayTime }

    @Test
    fun `repeated shifts accumulate instead of overwriting`() =
        runBlocking {
            insertSong("song")

            shift("song", 50)
            shift("song", 50)
            shift("song", 50)

            assertEquals(150, offsetOf("song"))
        }

    @Test
    fun `shift clamps to the upper bound`() =
        runBlocking {
            insertSong("song", lyricsOffset = 2980)

            shift("song", 50)
            assertEquals(3000, offsetOf("song"))

            shift("song", 50)
            assertEquals(3000, offsetOf("song"))
        }

    @Test
    fun `shift clamps to the lower bound`() =
        runBlocking {
            insertSong("song", lyricsOffset = -2980)

            shift("song", -50)
            assertEquals(-3000, offsetOf("song"))

            shift("song", -50)
            assertEquals(-3000, offsetOf("song"))
        }

    @Test
    fun `shift leaves other columns untouched`() =
        runBlocking {
            insertSong("song")
            withContext(Dispatchers.IO) { database.dao.incrementTotalPlayTime("song", 30_000L) }

            shift("song", 50)

            assertEquals(50, offsetOf("song"))
            assertEquals(30_000L, totalPlayTimeOf("song"))
        }

    @Test
    fun `reset clears the offset`() =
        runBlocking {
            insertSong("song", lyricsOffset = -450)

            reset("song")

            assertEquals(0, offsetOf("song"))
        }

    @Test
    fun `shift and reset ignore an unknown song`() =
        runBlocking {
            shift("missing", 50)
            reset("missing")

            assertEquals(null, offsetOf("missing"))
        }
}