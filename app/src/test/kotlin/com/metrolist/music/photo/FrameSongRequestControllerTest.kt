package com.metrolist.music.photo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for the request-validation rules of the song request
 * controller. Paths that need a real PlayerConnection (queue state, removal
 * validation, actual queueing) require Android, so they stay untested here.
 */
class FrameSongRequestControllerTest {
    private fun controller(guest: Boolean = false) =
        FrameSongRequestController(playerProvider = { null }, guestProvider = { guest })

    @Test
    fun `state reports unavailable when no player is attached`() {
        val result = controller().state()
        assertEquals(503, result.status)
    }

    @Test
    fun `queue changes are refused for listen together guests`() {
        val controller = controller(guest = true)
        assertEquals(409, controller.playNext("dQw4w9WgXcQ").status)
        assertEquals(409, controller.addToEnd("dQw4w9WgXcQ").status)
        assertEquals(409, controller.remove(2, null).status)
    }

    @Test
    fun `invalid video ids are rejected before touching the player`() {
        val controller = controller()
        assertEquals(400, controller.playNext("").status)
        assertEquals(400, controller.playNext("bad id!").status)
    }

    @Test
    fun `unattached player makes queue changes unavailable`() {
        val controller = controller()
        assertEquals(503, controller.playNext("dQw4w9WgXcQ").status)
        assertEquals(503, controller.addToEnd("dQw4w9WgXcQ").status)
        assertEquals(503, controller.remove(2, null).status)
    }

    @Test
    fun `blank search query returns an empty song list`() {
        val result = controller().search("   ")
        assertEquals(200, result.status)
        assertEquals("""{"songs":[]}""", result.body)
    }
}
