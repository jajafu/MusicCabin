package com.metrolist.music.photo

import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.WordTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameLyricsS2TTest {
    @Test
    fun simplifiedLyricsDetected() {
        assertTrue(isSimplifiedLyrics("爱你是我奋斗的目标\n国语歌曲龙飞凤舞"))
    }

    @Test
    fun traditionalLyricsNotConverted() {
        assertFalse(isSimplifiedLyrics("愛你是我奮鬥的目標\n國語歌曲龍飛鳳舞"))
    }

    @Test
    fun nonChineseNotConverted() {
        assertFalse(isSimplifiedLyrics("Hello world\nAnother line here"))
        assertFalse(isSimplifiedLyrics(""))
    }

    @Test
    fun singleSimplifiedCharIgnored() {
        assertFalse(isSimplifiedLyrics("a"))
    }

    @Test
    fun conversionPreservesTimestampsAndLineCount() {
        val entries = listOf(
            LyricsEntry(
                time = 1000L,
                text = "爱你奋斗",
                words = listOf(
                    WordTimestamp("爱", 1.0, 1.2),
                    WordTimestamp("你", 1.2, 1.4),
                    WordTimestamp("奋", 1.4, 1.6),
                    WordTimestamp("斗", 1.6, 1.8),
                ),
            ),
            LyricsEntry(time = 5000L, text = "Hello world"),
        )
        val converted = convertFrameLyricsToTraditional(entries)
        assertEquals(2, converted.size)
        assertEquals("愛你奮鬥", converted[0].text)
        assertEquals(1000L, converted[0].time)
        assertEquals(4, converted[0].words?.size)
        assertEquals(1.0, converted[0].words?.get(0)?.startTime ?: -1.0, 0.0)
        assertEquals(1.8, converted[0].words?.get(3)?.endTime ?: -1.0, 0.0)
        assertEquals("Hello world", converted[1].text)
    }

    @Test
    fun traditionalEntriesUnchanged() {
        val entries = listOf(LyricsEntry(time = 1000L, text = "愛你奮鬥"))
        val converted = convertFrameLyricsToTraditional(entries)
        assertEquals("愛你奮鬥", converted[0].text)
    }
}
