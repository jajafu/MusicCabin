/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LikedSongsOrderTest {
    private lateinit var database: InternalDatabase
    private lateinit var dao: DatabaseDao
    private val now = LocalDateTime.of(2026, 9, 1, 0, 0)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InternalDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.dao
    }

    @After
    fun tearDown() = database.close()

    private fun likedSong(
        id: String,
        minutesAgo: Long,
        order: Int? = null,
    ) = SongEntity(
        id = id,
        title = "Song $id",
        liked = true,
        likedDate = now.minusMinutes(minutesAgo),
        likedOrder = order,
    )

    private suspend fun likedOrderSequence(): List<String> =
        dao.likedSongsByLikedOrderAsc().first().map { it.song.id }

    @Test
    fun `custom order puts arranged songs first and unarranged songs after them`() = runBlocking {
        dao.insert(likedSong("arranged-old", 40, order = 1))
        dao.insert(likedSong("arranged-new", 50, order = 0))
        dao.insert(likedSong("fresh-newest", 10))
        dao.insert(likedSong("fresh-older", 20))

        assertEquals(
            listOf("arranged-new", "arranged-old", "fresh-newest", "fresh-older"),
            likedOrderSequence(),
        )
    }

    @Test
    fun `backfill appends unarranged songs after the arranged ones by liked date`() = runBlocking {
        dao.insert(likedSong("arranged", 40, order = 0))
        dao.insert(likedSong("fresh-newest", 10))
        dao.insert(likedSong("fresh-older", 20))

        dao.backfillLikedOrders()

        assertEquals(
            listOf("arranged", "fresh-newest", "fresh-older"),
            likedOrderSequence(),
        )
        assertEquals(0, dao.likedOrderOf("arranged"))
        assertEquals(1, dao.likedOrderOf("fresh-newest"))
        assertEquals(2, dao.likedOrderOf("fresh-older"))
    }

    @Test
    fun `move works in both directions when the order has gaps`() = runBlocking {
        // A(0) B(1) C(2) D(3); unliking C leaves a gap between B and D.
        dao.insert(likedSong("A", 40, order = 0))
        dao.insert(likedSong("B", 30, order = 1))
        dao.insert(likedSong("C", 20, order = 2))
        dao.insert(likedSong("D", 10, order = 3))
        dao.update(dao.songEntity("C")!!.copy(liked = false, likedDate = null, likedOrder = null))
        assertEquals(listOf("A", "B", "D"), likedOrderSequence())

        // Drag D up onto B's position: D lands before B.
        dao.moveLikedSong("D", "B")
        assertEquals(listOf("A", "D", "B"), likedOrderSequence())

        // Drag A (first) to the very bottom of [A, D, B]: its neighbor is B,
        // the song that ends up right above A.
        dao.moveLikedSong("A", "B")
        assertEquals(listOf("D", "B", "A"), likedOrderSequence())
    }

    @Test
    fun `move to the list edges keeps a stable total order`() = runBlocking {
        dao.insert(likedSong("A", 40, order = 0))
        dao.insert(likedSong("B", 30, order = 1))
        dao.insert(likedSong("C", 20, order = 2))

        // Drag C to the very top: its neighbor is A.
        dao.moveLikedSong("C", "A")
        assertEquals(listOf("C", "A", "B"), likedOrderSequence())

        // Drag C (now first) to the very bottom: its neighbor is B.
        dao.moveLikedSong("C", "B")
        assertEquals(listOf("A", "B", "C"), likedOrderSequence())
    }

    @Test
    fun `moving a song onto itself is a no-op`() = runBlocking {
        dao.insert(likedSong("A", 40, order = 0))
        dao.insert(likedSong("B", 30, order = 1))

        dao.moveLikedSong("B", "B")

        assertEquals(listOf("A", "B"), likedOrderSequence())
    }
}
