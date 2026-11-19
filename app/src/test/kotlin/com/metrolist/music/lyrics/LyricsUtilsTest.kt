package com.metrolist.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {
    @Test
    fun richSyncLyricsRoundTripPreservesWordTimings() {
        val source = "[00:01.000]v1: <00:01.000>Hello <00:01.500>world<00:02.000>\n"
        val parsed = LyricsUtils.parseLyrics(source)
        assertEquals(1, parsed.size)
        val words = parsed.single().words.orEmpty()
        assertEquals(listOf("Hello", "world"), words.map { it.text })

        val serialized = LyricsUtils.serializeRichSyncLyrics(parsed)
        val reparsed = LyricsUtils.parseLyrics(serialized).single()
        assertEquals("Hello world", reparsed.text)
        val reparsedWords = reparsed.words.orEmpty()
        assertEquals(words.size, reparsedWords.size)
        assertEquals(words[0].startTime, reparsedWords[0].startTime, 0.001)
        assertEquals(words[1].endTime, reparsedWords[1].endTime, 0.001)
        assertTrue(reparsedWords[0].hasTrailingSpace)
        assertFalse(reparsedWords[1].hasTrailingSpace)
        assertTrue(serialized.contains("{agent:v1}"))
    }

    @Test
    fun cjkWordsDoNotGainSpacesDuringRoundTrip() {
        val entry = LyricsEntry(
            time = 1000L,
            text = "你好",
            words = listOf(
                WordTimestamp("你", 1.0, 1.5, false),
                WordTimestamp("好", 1.5, 2.0, false),
            ),
        )
        val reparsed = LyricsUtils.parseLyrics(LyricsUtils.serializeRichSyncLyrics(listOf(entry))).single()
        assertEquals("你好", reparsed.text)
        val reparsedWords = reparsed.words.orEmpty()
        assertEquals(2, reparsedWords.size)
        assertEquals(1.0, reparsedWords[0].startTime, 0.001)
        assertEquals(1.5, reparsedWords[0].endTime, 0.001)
        assertEquals(1.5, reparsedWords[1].startTime, 0.001)
        assertEquals(2.0, reparsedWords[1].endTime, 0.001)
        assertFalse(reparsedWords[0].hasTrailingSpace)
        assertFalse(reparsedWords[1].hasTrailingSpace)
    }

    @Test
    fun backgroundWordsKeepBackgroundMarker() {
        val source = "[00:00.000]<00:00.000>main<00:01.000>\n[bg: <00:01.000>echo<00:02.000>]"
        val parsed = LyricsUtils.parseLyrics(source).first { it.isBackground }
        val serialized = LyricsUtils.serializeRichSyncLyrics(listOf(parsed))
        assertTrue(serialized.startsWith("[bg:"))
        assertTrue(serialized.contains("echo"))
        val reparsed = LyricsUtils.parseLyrics(serialized).single()
        assertTrue(reparsed.isBackground)
        assertEquals("echo", reparsed.text)
        val reparsedWords = reparsed.words.orEmpty()
        assertEquals(1, reparsedWords.size)
        assertEquals(1.0, reparsedWords.single().startTime, 0.001)
        assertEquals(2.0, reparsedWords.single().endTime, 0.001)
    }

    @Test
    fun backgroundLineWithoutWordsKeepsBackgroundMarker() {
        val entry = LyricsEntry(1000L, "echo", isBackground = true)
        val serialized = LyricsUtils.serializeRichSyncLyrics(listOf(entry))
        val reparsed = LyricsUtils.parseLyrics(serialized).single()
        assertTrue(reparsed.isBackground)
        assertEquals("echo", reparsed.text)
    }

    @Test
    fun blankSeparatorLineSurvivesSaving() {
        val entries = listOf(
            LyricsEntry(
                time = 1000L,
                text = "hello",
                words = listOf(WordTimestamp("hello", 1.0, 1.5, false)),
            ),
            LyricsEntry(2000L, ""),
        )
        val serialized = LyricsUtils.serializeRichSyncLyrics(entries)
        assertTrue(serialized.endsWith("[00:02.000]"))
        val reparsed = LyricsUtils.parseLyrics(serialized)
        assertEquals(listOf("hello", ""), reparsed.map { it.text })
    }

    @Test
    fun backgroundLyricsKeepMultiTimestampLines() {
        val source = "[00:01.000][00:05.000]Chorus\n[bg: <00:02.000>echo<00:03.000>]"
        val parsed = LyricsUtils.parseLyrics(source)
        val chorusLines = parsed.filter { !it.isBackground }
        assertEquals(listOf(1000L, 5000L), chorusLines.map { it.time })
        assertEquals(listOf("Chorus", "Chorus"), chorusLines.map { it.text })
        assertTrue(parsed.any { it.isBackground })
    }

    @Test
    fun serializerPreservesContiguousWordBoundariesAndMinuteRollover() {
        val entry = LyricsEntry(
            time = 59999L,
            text = "abc",
            words = listOf(
                WordTimestamp("a", 59.9996, 60.1, false),
                WordTimestamp("b", 60.1, 60.2, false),
                WordTimestamp("c", 60.2, 60.3, false),
            ),
        )
        val serialized = LyricsUtils.serializeRichSyncLyrics(listOf(entry))
        assertTrue(serialized.startsWith("[01:00.000]"))
        // Contiguous boundaries need one tag per word, not adjacent duplicates.
        assertEquals(4, Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>").findAll(serialized).count())
        val reparsed = LyricsUtils.parseLyrics(serialized).single()
        val reparsedWords = reparsed.words.orEmpty()
        assertEquals(3, reparsedWords.size)
        assertEquals(60.1, reparsedWords[0].endTime, 0.001)
        assertEquals(60.2, reparsedWords[1].endTime, 0.001)
    }

    @Test
    fun richSyncRoundTripPreservesIntentionalWordGaps() {
        val entry = LyricsEntry(
            time = 1000L,
            text = "one two",
            words = listOf(
                WordTimestamp("one", 1.0, 1.4, true),
                WordTimestamp("two", 2.0, 2.5, false),
            ),
        )
        val serialized = LyricsUtils.serializeRichSyncLyrics(listOf(entry))
        assertTrue(serialized.contains("<00:01.400><00:02.000>"))
        val reparsed = LyricsUtils.parseLyrics(serialized).single()
        assertEquals("one two", reparsed.text)
        val words = reparsed.words.orEmpty()
        assertEquals(2, words.size)
        assertEquals(1.0, words[0].startTime, 0.001)
        assertEquals(1.4, words[0].endTime, 0.001)
        assertEquals(2.0, words[1].startTime, 0.001)
        assertEquals(2.5, words[1].endTime, 0.001)
        assertTrue(words[0].hasTrailingSpace)
        assertFalse(words[1].hasTrailingSpace)
    }

    @Test
    fun skeletonKeepsWordsWholeAndCjkPerCharacter() {
        val words = LyricsUtils.buildSkeletonWords("Hi 你好 😀!", 1.0, 3.0)
        assertEquals(listOf("Hi", "你", "好", "😀!"), words.map { it.text })
        assertTrue(words[0].hasTrailingSpace)
        assertFalse(words[1].hasTrailingSpace)
        assertTrue(words[2].hasTrailingSpace)
        assertFalse(words[3].hasTrailingSpace)
        assertEquals(1.0, words.first().startTime, 0.001)
        assertEquals(3.0, words.last().endTime, 0.001)

        val entry = LyricsEntry(1000L, "Hi 你好 😀!", words)
        val reparsed = LyricsUtils.parseLyrics(LyricsUtils.serializeRichSyncLyrics(listOf(entry))).single()
        assertEquals("Hi 你好 😀!", reparsed.text)
    }

    @Test
    fun halfMillisecondBoundariesRoundUpLikeTheEditorDisplay() {
        val words = LyricsUtils.buildSkeletonWords("竹篱上停留着蜻蜓", 23.48, 28.86)
        assertEquals("24.153", String.format(java.util.Locale.US, "%.3f", words[0].endTime))
        val entry = LyricsEntry(23480L, "竹篱上停留着蜻蜓", words)
        val serialized = LyricsUtils.serializeRichSyncLyrics(listOf(entry))
        assertTrue(serialized.contains("<00:24.153>篱"))
        assertFalse(serialized.contains("<00:24.152>"))
        val reparsed = LyricsUtils.parseLyrics(serialized).single().words.orEmpty()
        assertEquals(reparsed[0].endTime, reparsed[1].startTime, 0.0)
    }

    @Test
    fun lineAgentRoundTripKeepsAgentAndText() {
        val parsed = LyricsUtils.parseLyrics("[00:01.000]{agent:v1}hello").single()
        val reparsed = LyricsUtils.parseLyrics(LyricsUtils.serializeRichSyncLyrics(listOf(parsed))).single()
        assertEquals("v1", reparsed.agent)
        assertEquals("hello", reparsed.text)
    }
}
