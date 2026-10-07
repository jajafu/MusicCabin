package com.metrolist.music.photo

import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import com.metrolist.music.lyrics.LyricsEntry

private const val FRAME_S2T_MIN_SIMPLE_CHARS = 2

private fun isChineseChar(c: Char): Boolean =
    c in '一'..'鿿' || c in '豈'..'﫿'

/**
 * Returns true when [text] looks like Simplified Chinese lyrics worth converting.
 * Many characters are shared between Simplified and Traditional, so an absolute
 * count of Simplified-specific characters (rather than a high ratio) is used:
 * at least [FRAME_S2T_MIN_SIMPLE_CHARS] Simplified-specific chars, more than
 * the Traditional-specific ones.
 */
fun isSimplifiedLyrics(text: String): Boolean {
    if (text.isBlank()) return false
    val simpleCount = runCatching { ZhTwConverterUtil.simpleList(text).size }.getOrNull() ?: return false
    if (simpleCount < FRAME_S2T_MIN_SIMPLE_CHARS) return false
    val traditionalCount = runCatching { ZhTwConverterUtil.traditionalList(text).size }.getOrNull() ?: 0
    if (simpleCount <= traditionalCount) return false
    var chineseCount = 0
    for (c in text) if (isChineseChar(c)) chineseCount++
    return chineseCount > 0 && simpleCount * 20 >= chineseCount
}

/**
 * Converts parsed lyric lines from Simplified to Taiwan Traditional, preserving
 * timestamps and line count so the shared KTV word-level engine keeps working.
 * Only the display text ([LyricsEntry.text] and per-word text) is replaced;
 * the stored [LyricsEntity] keeps the original text.
 */
fun convertFrameLyricsToTraditional(entries: List<LyricsEntry>): List<LyricsEntry> {
    if (entries.isEmpty()) return entries
    val fullText = entries.joinToString("\n") { it.text }
    if (!isSimplifiedLyrics(fullText)) return entries
    return entries.map { entry ->
        val convertedText = runCatching { ZhTwConverterUtil.toTraditional(entry.text) }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: return@map entry
        if (convertedText == entry.text) return@map entry
        val convertedWords = entry.words?.map { word ->
            val converted = runCatching { ZhTwConverterUtil.toTraditional(word.text) }
                .getOrNull()?.takeIf { it.isNotEmpty() } ?: word.text
            word.copy(text = converted)
        }
        entry.copy(text = convertedText, words = convertedWords)
    }
}
