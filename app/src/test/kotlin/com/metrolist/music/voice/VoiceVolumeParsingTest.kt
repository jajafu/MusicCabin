/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract of the documented `volume_set` query format: whole numbers 0–100
 * are percent, decimals 0–1 are a fraction, 最大/max and 靜音/mute are spoken
 * words, everything else is invalid and must make the receiver answer with
 * the "say a volume between 0 and 100" hint (voice_result_volume_invalid).
 */
class VoiceVolumeParsingTest {
    @Test
    fun `whole numbers are treated as percent`() {
        assertEquals(0.8f, parseVoiceVolume("80"))
        assertEquals(1.0f, parseVoiceVolume("100"))
        assertEquals(0.0f, parseVoiceVolume("0"))
        // 「音量調到 1」→ 1%，不是 100%
        assertEquals(0.01f, parseVoiceVolume("1"))
    }

    @Test
    fun `decimals within 0 to 1 are treated as a fraction`() {
        assertEquals(0.8f, parseVoiceVolume("0.8"))
        assertEquals(0.0f, parseVoiceVolume("0.0"))
        assertEquals(1.0f, parseVoiceVolume("1.0"))
        assertEquals(0.05f, parseVoiceVolume(".05"))
    }

    @Test
    fun `percent sign and surrounding whitespace are ignored`() {
        assertEquals(0.8f, parseVoiceVolume("80%"))
        assertEquals(0.8f, parseVoiceVolume(" 80 "))
        assertEquals(0.8f, parseVoiceVolume("0.8%"))
    }

    @Test
    fun `spoken max and mute words map to the boundaries`() {
        assertEquals(1.0f, parseVoiceVolume("最大"))
        assertEquals(1.0f, parseVoiceVolume("MAX"))
        assertEquals(0.0f, parseVoiceVolume("靜音"))
        assertEquals(0.0f, parseVoiceVolume("mute"))
    }

    @Test
    fun `mixed decimals outside 0 to 1 are invalid`() {
        assertNull(parseVoiceVolume("1.5"))
        assertNull(parseVoiceVolume("80.5"))
        assertNull(parseVoiceVolume("80.0"))
    }

    @Test
    fun `out of range and non numeric input is invalid`() {
        assertNull(parseVoiceVolume("101"))
        assertNull(parseVoiceVolume("-5"))
        assertNull(parseVoiceVolume("abc"))
        assertNull(parseVoiceVolume(""))
        assertNull(parseVoiceVolume("   "))
        assertNull(parseVoiceVolume("NaN"))
        assertNull(parseVoiceVolume("Infinity"))
    }
}
