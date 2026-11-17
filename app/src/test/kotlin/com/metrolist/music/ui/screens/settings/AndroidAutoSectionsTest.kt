package com.metrolist.music.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Format/compatibility tests for the Android Auto section order serialization: the stored string
 * is user data, so a section introduced later (PINNED in 13.8.2) must be appended enabled instead
 * of silently dropped, and saved enabled/disabled states must survive round trips.
 */
class AndroidAutoSectionsTest {

    @Test
    fun `blank raw deserializes to all sections enabled`() {
        val sections = deserializeSections("")
        assertEquals(AndroidAutoSection.values().toList(), sections.map { (section, _) -> section })
        assertTrue(sections.all { (_, enabled) -> enabled })
    }

    @Test
    fun `round trip preserves order and enabled states`() {
        // A complete list round-trips exactly; a partial list would trigger the
        // append-missing-sections compatibility behavior (covered by the next test).
        val sections = listOf(
            AndroidAutoSection.PLAYLISTS to true,
            AndroidAutoSection.LIKED to false,
            AndroidAutoSection.PINNED to false,
            AndroidAutoSection.SONGS to true,
            AndroidAutoSection.ARTISTS to true,
            AndroidAutoSection.ALBUMS to true,
        )
        assertEquals(sections, deserializeSections(serializeSections(sections)))
    }

    @Test
    fun `stored order missing a new section appends it enabled`() {
        // A 13.8.1-era stored order has no "pinned" token.
        val sections = deserializeSections("liked:true,songs:false,artists:true,albums:true,playlists:true")
        assertEquals(AndroidAutoSection.PINNED, sections.last().first)
        assertTrue(sections.last().second)
        // Everything the user saved is untouched, including disabled states.
        assertEquals(false, sections.first { it.first == AndroidAutoSection.SONGS }.second)
    }

    @Test
    fun `unknown tokens are skipped`() {
        val sections = deserializeSections("liked:true,bogus:true,songs:false")
        assertEquals(
            listOf(AndroidAutoSection.LIKED, AndroidAutoSection.SONGS) + AndroidAutoSection.values()
                .filter { it != AndroidAutoSection.LIKED && it != AndroidAutoSection.SONGS },
            sections.map { (section, _) -> section },
        )
        assertTrue(sections.last().second)
    }
}
