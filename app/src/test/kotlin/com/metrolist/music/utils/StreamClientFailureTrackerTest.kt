/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamClientFailureTrackerTest {
    private data class Client(val name: String)

    @Test
    fun `marked client is reported failed`() {
        val tracker = StreamClientFailureTracker(clock = { 1_000L })
        tracker.markFailed("video1", "VISIONOS")
        assertTrue(tracker.isFailed("video1", "VISIONOS"))
    }

    @Test
    fun `other videos and clients are unaffected`() {
        val tracker = StreamClientFailureTracker(clock = { 1_000L })
        tracker.markFailed("video1", "VISIONOS")
        assertFalse(tracker.isFailed("video2", "VISIONOS"))
        assertFalse(tracker.isFailed("video1", "ANDROID_VR"))
    }

    @Test
    fun `marks expire after ttl`() {
        var now = 1_000L
        val tracker = StreamClientFailureTracker(ttlMs = 10_000L, clock = { now })
        tracker.markFailed("video1", "VISIONOS")
        now = 11_000L
        assertFalse(tracker.isFailed("video1", "VISIONOS"))
    }

    @Test
    fun `blank ids are ignored`() {
        val tracker = StreamClientFailureTracker(clock = { 1_000L })
        tracker.markFailed("", "VISIONOS")
        tracker.markFailed("video1", " ")
        assertFalse(tracker.isFailed("", "VISIONOS"))
        assertFalse(tracker.isFailed("video1", " "))
        assertFalse(tracker.isFailed("video1", ""))
    }

    @Test
    fun `filterFailed drops marked clients and keeps the rest in order`() {
        val tracker = StreamClientFailureTracker(clock = { 1_000L })
        val clients = listOf(Client("VISIONOS"), Client("ANDROID_VR"), Client("WEB_REMIX"))
        tracker.markFailed("video1", "VISIONOS")
        assertEquals(
            listOf("ANDROID_VR", "WEB_REMIX"),
            tracker.filterFailed("video1", clients) { it.name }.map { it.name },
        )
    }

    @Test
    fun `filterFailed returns the full chain when every client is marked`() {
        val tracker = StreamClientFailureTracker(clock = { 1_000L })
        val clients = listOf(Client("VISIONOS"), Client("ANDROID_VR"))
        tracker.markFailed("video1", "VISIONOS")
        tracker.markFailed("video1", "ANDROID_VR")
        assertEquals(
            listOf("VISIONOS", "ANDROID_VR"),
            tracker.filterFailed("video1", clients) { it.name }.map { it.name },
        )
    }

    @Test
    fun `filterFailed with blank video id keeps the full chain`() {
        val tracker = StreamClientFailureTracker(clock = { 1_000L })
        val clients = listOf(Client("VISIONOS"), Client("ANDROID_VR"))
        assertEquals(clients, tracker.filterFailed("", clients) { it.name })
    }

    @Test
    fun `map is bounded under churn`() {
        var now = 1_000L
        val tracker = StreamClientFailureTracker(ttlMs = 500L, maxEntries = 3, clock = { now })
        repeat(10) { index ->
            tracker.markFailed("video$index", "VISIONOS")
        }
        // All 10 marks are still valid, so the bound must have dropped the newest ones.
        assertTrue(tracker.isFailed("video2", "VISIONOS"))
        assertFalse(tracker.isFailed("video9", "VISIONOS"))
        now = 1_600L
        // After expiry the bound no longer blocks new marks.
        tracker.markFailed("video9", "ANDROID_VR")
        assertTrue(tracker.isFailed("video9", "ANDROID_VR"))
        assertFalse(tracker.isFailed("video2", "VISIONOS"))
    }
}
