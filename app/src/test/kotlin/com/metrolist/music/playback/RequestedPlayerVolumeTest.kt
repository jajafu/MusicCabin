/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.music.constants.MediaSessionConstants.VOLUME_MODE_DOWN
import com.metrolist.music.constants.MediaSessionConstants.VOLUME_MODE_GET
import com.metrolist.music.constants.MediaSessionConstants.VOLUME_MODE_SET
import com.metrolist.music.constants.MediaSessionConstants.VOLUME_MODE_UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Boundary behavior of the SET_VOLUME server-side computation: applied
 * values are quantized to percent precision and clamped to 0..1, while a
 * state query (or an unknown mode) must never produce a write.
 */
class RequestedPlayerVolumeTest {
    @Test
    fun `set quantizes to percent precision`() {
        assertEquals(0.8f, requestedPlayerVolume(VOLUME_MODE_SET, current = 0.437f, requested = 0.804f))
        assertEquals(0.81f, requestedPlayerVolume(VOLUME_MODE_SET, current = 0.437f, requested = 0.805f))
    }

    @Test
    fun `set clamps into 0 to 1`() {
        assertEquals(1.0f, requestedPlayerVolume(VOLUME_MODE_SET, current = 0.5f, requested = 1.5f))
        assertEquals(0.0f, requestedPlayerVolume(VOLUME_MODE_SET, current = 0.5f, requested = -0.2f))
    }

    @Test
    fun `up steps by 10 percent and clamps at full volume`() {
        assertEquals(0.54f, requestedPlayerVolume(VOLUME_MODE_UP, current = 0.437f, requested = 0f))
        assertEquals(1.0f, requestedPlayerVolume(VOLUME_MODE_UP, current = 0.95f, requested = 0f))
        assertEquals(1.0f, requestedPlayerVolume(VOLUME_MODE_UP, current = 1.0f, requested = 0f))
    }

    @Test
    fun `down steps by 10 percent and clamps at silence`() {
        assertEquals(0.2f, requestedPlayerVolume(VOLUME_MODE_DOWN, current = 0.3f, requested = 0f))
        assertEquals(0.0f, requestedPlayerVolume(VOLUME_MODE_DOWN, current = 0.05f, requested = 0f))
        assertEquals(0.0f, requestedPlayerVolume(VOLUME_MODE_DOWN, current = 0.0f, requested = 0f))
    }

    @Test
    fun `state query and unknown modes never write`() {
        assertNull(requestedPlayerVolume(VOLUME_MODE_GET, current = 0.437f, requested = 0f))
        assertNull(requestedPlayerVolume("unknown", current = 0.437f, requested = 0.9f))
    }
}
