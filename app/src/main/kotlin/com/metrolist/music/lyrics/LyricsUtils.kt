/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.text.format.DateUtils
import java.util.Locale

val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.*)".toRegex()
val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

// Regex for rich sync format: [MM:SS.mm]<MM:SS.mm> word <MM:SS.mm> word ...
private val RICH_SYNC_LINE_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.*)".toRegex()
private val RICH_SYNC_WORD_REGEX = "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>([^<]+)".toRegex()
private val MULTI_TIME_LINE_REGEX = "^\\[\\d{2}:\\d{2}\\.\\d{2,3}\\]\\s*\\[".toRegex()
private val ANGLE_TIME_REGEX = "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>".toRegex()
private val SQUARE_TIME_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\]".toRegex()

// Regex for Paxsenix v1/v2/bg format
// [00:00.000]v1: <00:00.000>I <00:00.154>promise...
// [bg: <02:18.078>Yeah<02:19.341>]
private val PAXSENIX_AGENT_LINE_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](v\\d+):\\s*(.*)".toRegex()
private val PAXSENIX_BG_LINE_REGEX = "^\\[bg:\\s*(.*)\\]$".toRegex()

// Regex for agent and background markers (existing format)
private val AGENT_REGEX = "\\{agent:([^}]+)\\}".toRegex()
private val BACKGROUND_REGEX = "^\\{bg\\}".toRegex()

@Suppress("RegExpRedundantEscape")
object LyricsUtils {
    /** Serializes edited entries to the rich LRC format understood by [parseLyrics]. */
    fun serializeRichSyncLyrics(entries: List<LyricsEntry>): String = buildString {
        entries.forEach { entry ->
            val words = entry.words.orEmpty()
            val lineTime = words.firstOrNull()?.startTime ?: entry.time / 1000.0
            if (entry.isBackground && words.isNotEmpty()) {
                append("[bg: ")
                appendWordTimings(words)
                append(']')
            } else {
                append('[').append(formatSyncTime(lineTime)).append(']')
                if (!entry.agent.isNullOrBlank() && entry.agent != "bg") {
                    append("{agent:").append(entry.agent).append('}')
                }
                if (words.isEmpty()) {
                    if (entry.isBackground) append("{bg}")
                    append(entry.text)
                } else {
                    appendWordTimings(words)
                }
            }
            append('\n')
        }
    }.trimEnd()

    private fun StringBuilder.appendWordTimings(words: List<WordTimestamp>) {
        words.forEachIndexed { index, word ->
            val start = word.startTime.coerceAtLeast(0.0)
            val end = word.endTime.coerceAtLeast(start)
            append('<').append(formatSyncTime(start)).append('>')
            append(word.text)
            if (word.hasTrailingSpace) append(' ')
            // Only write an end tag when it differs from the next word's start,
            // which keeps contiguous words in the standard rich-sync shape.
            val nextStart = words.getOrNull(index + 1)?.startTime
            if (nextStart == null || toRoundedMillis(nextStart) != toRoundedMillis(end)) {
                append('<').append(formatSyncTime(end)).append('>')
            }
        }
    }

    private fun formatSyncTime(seconds: Double): String {
        val roundedMillis = toRoundedMillis(seconds)
        val minutes = roundedMillis / 60000
        val remainder = (roundedMillis % 60000) / 1000.0
        return String.format(Locale.US, "%02d:%06.3f", minutes, remainder)
    }

    private fun toRoundedMillis(seconds: Double): Long =
        kotlin.math.floor(seconds.coerceAtLeast(0.0) * 1000.0 + 0.5).toLong()

    /**
     * Splits [text] into editable word timings for the word-timing editor skeleton.
     * Whitespace-separated words stay whole, while CJK characters get one entry each
     * so their timings can be adjusted independently.
     */
    fun buildSkeletonWords(text: String, startTime: Double, endTime: Double): List<WordTimestamp> {
        val tokens = splitSkeletonTokens(text)
        if (tokens.isEmpty()) return emptyList()
        val safeStart = startTime.coerceAtLeast(0.0)
        val step = (endTime - safeStart).coerceAtLeast(0.1) / tokens.size
        return tokens.mapIndexed { index, token ->
            WordTimestamp(
                text = token.text,
                startTime = safeStart + step * index,
                endTime = safeStart + step * (index + 1),
                hasTrailingSpace = token.hasTrailingSpace,
            )
        }
    }

    private data class SkeletonToken(val text: String, val hasTrailingSpace: Boolean)

    private fun splitSkeletonTokens(text: String): List<SkeletonToken> {
        val tokens = mutableListOf<SkeletonToken>()
        Regex("\\S+\\s*").findAll(text).forEach { match ->
            val chunk = match.value
            val word = chunk.trimEnd()
            val hasTrailingSpace = chunk.length > word.length
            val units = splitSkeletonChunk(word)
            units.forEachIndexed { index, unit ->
                tokens.add(SkeletonToken(unit, hasTrailingSpace && index == units.lastIndex))
            }
        }
        return tokens
    }

    private fun splitSkeletonChunk(chunk: String): List<String> {
        val units = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < chunk.length) {
            val codePoint = chunk.codePointAt(index)
            if (isCjkCodePoint(codePoint)) {
                if (current.isNotEmpty()) {
                    units.add(current.toString())
                    current.clear()
                }
                units.add(String(Character.toChars(codePoint)))
            } else {
                current.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        if (current.isNotEmpty()) units.add(current.toString())
        return units
    }

    private fun isCjkCodePoint(codePoint: Int): Boolean = when (Character.UnicodeScript.of(codePoint)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.BOPOMOFO,
        -> true
        else -> false
    }

    fun cleanTitleForSearch(title: String): String {
        return title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim()
    }

    fun filterLyricsCreditLines(lyrics: String): String {
        return lyrics.lines().filter { line ->
            var textContent = line.trim()
            var stripping = true
            while (stripping) {
                val prevLength = textContent.length
                textContent = textContent
                    .replaceFirst(Regex("^\\[\\d\\d:\\d\\d\\.\\d{2,3}\\]"), "")
                    .replaceFirst(Regex("^\\{agent:[^}]+\\}"), "")
                    .replaceFirst(Regex("^\\{bg\\}"), "")
                    .replaceFirst(Regex("^\\[bg:.*\\]"), "")
                    .replaceFirst(Regex("^v\\d+:"), "")
                    .trim()
                stripping = textContent.length < prevLength
            }
            val lowerText = textContent.lowercase(Locale.getDefault())
            val isCredit = lowerText.startsWith("synced by") ||
                    lowerText.startsWith("lyrics by") ||
                    lowerText.startsWith("music by") ||
                    lowerText.startsWith("arranged by") ||
                    (lowerText.startsWith("[") && lowerText.endsWith("]") && lowerText.length < 40 && lowerText.contains("synced by"))
            !isCredit
        }.joinToString("\n")
    }

    private val HEX_ENTITY_REGEX = "&#x([0-9a-fA-F]+);".toRegex()
    private val DEC_ENTITY_REGEX = "&#(\\d+);".toRegex()

    private fun decodeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val end = text.indexOf(';', i + 1)
                if (end != -1 && end - i < 12) {
                    val entity = text.substring(i, end + 1)
                    val decoded = when {
                        entity == "&apos;" -> "'"
                        entity == "&quot;" -> "\""
                        entity == "&lt;" -> "<"
                        entity == "&gt;" -> ">"
                        entity == "&nbsp;" -> " "
                        entity == "&amp;" -> "&"
                        entity.startsWith("&#x") -> {
                            entity.substring(3, entity.length - 1).toIntOrNull(16)?.let { codePoint ->
                                if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else "\uFFFD"
                            }
                        }
                        entity.startsWith("&#") -> {
                            entity.substring(2, entity.length - 1).toIntOrNull()?.let { codePoint ->
                                if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else "\uFFFD"
                            }
                        }
                        else -> null
                    }
                    if (decoded != null) {
                        sb.append(decoded)
                        i = end + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        if (lyrics.isBlank()) return emptyList()
        val unescapedLyrics = if (lyrics.contains('\\') || lyrics.startsWith("\"")) {
            val s = lyrics.trim().removePrefix("\"").removeSuffix("\"")
            val sb = StringBuilder(s.length)
            var j = 0
            while (j < s.length) {
                val c = s[j]
                if (c == '\\' && j + 1 < s.length) {
                    when (val next = s[j + 1]) {
                        '\\' -> sb.append('\\')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> sb.append(c).append(next)
                    }
                    j += 2
                } else {
                    sb.append(c)
                    j++
                }
            }
            sb.toString()
        } else lyrics
        val decodedLyrics = decodeHtmlEntities(unescapedLyrics)
        val lines = decodedLyrics.lines()
            .filter {
                it.isNotBlank() || it.trim().startsWith("[") || it.trim().startsWith("<")
            }
            .filter { !it.trim().startsWith("[offset:") }
        val isRichSync = lines.any { line ->
            (RICH_SYNC_LINE_REGEX.matches(line.trim()) && RICH_SYNC_WORD_REGEX.containsMatchIn(line)) ||
                PAXSENIX_BG_LINE_REGEX.matches(line.trim())
        }
        return if (isRichSync) {
            parseRichSyncLyrics(lines)
        } else {
            parseStandardLyrics(lines)
        }
    }

    private fun parseRichSyncLyrics(lines: List<String>): List<LyricsEntry> {
        val result = mutableListOf<LyricsEntry>()
        var lastNonBgAgent: String? = null
        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            // A file with background lines may still contain ordinary LRC lines
            // carrying several timestamps; parse those with the standard parser.
            if (MULTI_TIME_LINE_REGEX.containsMatchIn(trimmedLine)) {
                parseLine(trimmedLine)?.let { result.addAll(it) }
                return@forEachIndexed
            }
            val bgMatch = PAXSENIX_BG_LINE_REGEX.find(trimmedLine)
            if (bgMatch != null) {
                val content = bgMatch.groupValues[1]
                val wordTimings = parseRichSyncWords(content, index, lines)
                    ?: run {
                        val nextLine = lines.getOrNull(index + 1)?.trim() ?: ""
                        if (nextLine.startsWith("<") && nextLine.endsWith(">")) {
                            parseWordTimestamps(nextLine.removeSurrounding("<", ">"))
                        } else null
                    }
                val plainText = content.replace(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*"), "").trim()
                val lineTimeMs = wordTimings?.firstOrNull()?.startTime?.let { (it * 1000).toLong() } ?: 0L
                result.add(LyricsEntry(lineTimeMs, plainText, wordTimings, agent = lastNonBgAgent ?: "bg", isBackground = true))
                return@forEachIndexed
            }
            val agentMatch = PAXSENIX_AGENT_LINE_REGEX.find(trimmedLine)
            if (agentMatch != null) {
                val minutes = agentMatch.groupValues[1].toLongOrNull() ?: 0L
                val seconds = agentMatch.groupValues[2].toLongOrNull() ?: 0L
                val centiseconds = agentMatch.groupValues[3].toLongOrNull() ?: 0L
                val agent = agentMatch.groupValues[4]
                val content = agentMatch.groupValues[5]
                val millisPart = if (agentMatch.groupValues[3].length == 3) centiseconds else centiseconds * 10
                val lineTimeMs = minutes * DateUtils.MINUTE_IN_MILLIS + seconds * DateUtils.SECOND_IN_MILLIS + millisPart
                val wordTimings = parseRichSyncWords(content, index, lines)
                    ?: run {
                        val nextLine = lines.getOrNull(index + 1)?.trim() ?: ""
                        if (nextLine.startsWith("<") && nextLine.endsWith(">")) {
                            parseWordTimestamps(nextLine.removeSurrounding("<", ">"))
                        } else null
                    }
                val plainText = content.replace(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*"), "").trim()
                if (!agent.isNullOrBlank()) {
                    lastNonBgAgent = agent
                }
                result.add(LyricsEntry(lineTimeMs, plainText, wordTimings, agent = agent, isBackground = false))
                return@forEachIndexed
            }
            val matchResult = RICH_SYNC_LINE_REGEX.matchEntire(trimmedLine)
            if (matchResult != null) {
                val minutes = matchResult.groupValues[1].toLongOrNull() ?: 0L
                val seconds = matchResult.groupValues[2].toLongOrNull() ?: 0L
                val centiseconds = matchResult.groupValues[3].toLongOrNull() ?: 0L
                val millisPart = if (matchResult.groupValues[3].length == 3) centiseconds else centiseconds * 10
                val lineTimeMs = minutes * DateUtils.MINUTE_IN_MILLIS + seconds * DateUtils.SECOND_IN_MILLIS + millisPart
                var content = matchResult.groupValues[4].trimStart()
                val oldAgentMatch = AGENT_REGEX.find(content)
                val agent = oldAgentMatch?.groupValues?.get(1)
                if (oldAgentMatch != null) {
                    content = content.replaceFirst(AGENT_REGEX, "")
                }
                val isBackground = BACKGROUND_REGEX.containsMatchIn(content)
                if (isBackground) {
                    content = content.replaceFirst(BACKGROUND_REGEX, "")
                }
                val wordTimings = parseRichSyncWords(content, index, lines)
                    ?: run {
                        val nextLine = lines.getOrNull(index + 1)?.trim() ?: ""
                        if (nextLine.startsWith("<") && nextLine.endsWith(">")) {
                            parseWordTimestamps(nextLine.removeSurrounding("<", ">"))
                        } else null
                    }
                val plainText = content.replace(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*"), "").trim()
                if (!isBackground && !agent.isNullOrBlank()) {
                    lastNonBgAgent = agent
                }
                result.add(LyricsEntry(lineTimeMs, plainText, wordTimings, agent = if (isBackground) lastNonBgAgent ?: "bg" else agent, isBackground = isBackground))
            }
        }
        return result.sorted()
    }

    private fun parseRichSyncWords(content: String, currentIndex: Int, allLines: List<String>): List<WordTimestamp>? {
        val wordMatches = RICH_SYNC_WORD_REGEX.findAll(content).toList()
        if (wordMatches.isEmpty()) return null
        val lastMatchEnd = wordMatches.last().range.last
        val trailingContent = content.substring(lastMatchEnd + 1).trim()
        val trailingEndTime = findExplicitEndTime(trailingContent)
        val wordTimings = mutableListOf<WordTimestamp>()
        wordMatches.forEachIndexed { index, match ->
            val startTimeSeconds = match.toLyricSeconds()
            val rawText = match.groupValues[4]
            val hasTrailingSpace = rawText.endsWith(" ")
            val words = rawText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val isLastGroup = index == wordMatches.lastIndex
            val nextWordStart = wordMatches.getOrNull(index + 1)?.toLyricSeconds()
            // The serializer writes an end tag after each word, so an intentional
            // gap shows up as an end tag before the next word's start tag.
            val explicitEndTime = if (isLastGroup) {
                trailingEndTime
            } else {
                val nextMatch = wordMatches[index + 1]
                findExplicitEndTime(content.substring(match.range.last + 1, nextMatch.range.first))
            }
            val nextLineTime = if (isLastGroup) getNextLineStartTime(currentIndex, allLines) else null
            val groupEndTime = explicitEndTime ?: nextWordStart ?: nextLineTime ?: (startTimeSeconds + 0.5)
            words.forEachIndexed { wordIndex, word ->
                val isLastWordInGroup = wordIndex == words.lastIndex
                val isLastWordOverall = isLastGroup && isLastWordInGroup
                val wordStartTime = startTimeSeconds + (groupEndTime - startTimeSeconds) * wordIndex / words.size
                val wordEndTime = if (!isLastWordInGroup) {
                    startTimeSeconds + (groupEndTime - startTimeSeconds) * (wordIndex + 1) / words.size
                } else {
                    groupEndTime
                }
                val wordHasTrailingSpace = when {
                    !isLastWordInGroup -> true
                    !isLastWordOverall -> hasTrailingSpace
                    else -> hasTrailingSpace || (trailingEndTime == null && trailingContent.isNotBlank())
                }
                if (word.isNotBlank()) {
                    wordTimings.add(WordTimestamp(word, wordStartTime, wordEndTime, wordHasTrailingSpace))
                }
            }
        }
        return if (wordTimings.isNotEmpty()) wordTimings else null
    }

    private fun MatchResult.toLyricSeconds(): Double {
        val minutes = groupValues[1].toLongOrNull() ?: 0L
        val seconds = groupValues[2].toLongOrNull() ?: 0L
        val fraction = groupValues[3].toLongOrNull() ?: 0L
        val fractionPart = if (groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
        return minutes * 60.0 + seconds + fractionPart
    }

    /** Reads a closing timestamp only when nothing but blanks (or a line bracket) follows it. */
    private fun findExplicitEndTime(segment: String): Double? {
        val match = ANGLE_TIME_REGEX.find(segment) ?: SQUARE_TIME_REGEX.find(segment) ?: return null
        val remainder = segment.substring(match.range.last + 1).trim().removeSuffix("]").trim()
        return if (remainder.isBlank()) match.toLyricSeconds() else null
    }

    private fun getNextLineStartTime(currentIndex: Int, allLines: List<String>): Double? {
        if (currentIndex + 1 >= allLines.size) return null
        val nextLine = allLines[currentIndex + 1].trim()
        val matchResult = RICH_SYNC_LINE_REGEX.matchEntire(nextLine)
        if (matchResult != null) {
            val minutes = matchResult.groupValues[1].toLongOrNull() ?: return null
            val seconds = matchResult.groupValues[2].toLongOrNull() ?: return null
            val fraction = matchResult.groupValues[3].toLongOrNull() ?: 0L
            val fractionPart = if (matchResult.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
            return minutes * 60.0 + seconds + fractionPart
        }
        val bgMatch = PAXSENIX_BG_LINE_REGEX.matchEntire(nextLine)
        if (bgMatch != null) {
            val content = bgMatch.groupValues[1]
            val wordMatch = RICH_SYNC_WORD_REGEX.find(content) ?: return null
            val minutes = wordMatch.groupValues[1].toLongOrNull() ?: return null
            val seconds = wordMatch.groupValues[2].toLongOrNull() ?: return null
            val fraction = wordMatch.groupValues[3].toLongOrNull() ?: 0L
            val fractionPart = if (wordMatch.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
            return minutes * 60.0 + seconds + fractionPart
        }
        return null
    }

    private fun parseStandardLyrics(lines: List<String>): List<LyricsEntry> {
        val result = mutableListOf<LyricsEntry>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.trim().startsWith("<") || !line.trim().endsWith(">")) {
                val entries = parseLine(line, null)
                if (entries != null) {
                    val wordTimestamps = if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1]
                        if (nextLine.trim().startsWith("<") && nextLine.trim().endsWith(">")) {
                            parseWordTimestamps(nextLine.trim().removeSurrounding("<", ">"))
                        } else null
                    } else null
                    if (wordTimestamps != null) {
                        result.addAll(entries.map { entry ->
                            LyricsEntry(entry.time, entry.text, wordTimestamps, agent = entry.agent, isBackground = entry.isBackground)
                        })
                    } else {
                        result.addAll(entries)
                    }
                }
            }
            i++
        }
        return result.sorted()
    }

    private fun parseWordTimestamps(data: String): List<WordTimestamp>? {
        if (data.isBlank()) return null
        return try {
            data.split("|").mapNotNull { wordData ->
                val parts = wordData.split(":")
                if (parts.size >= 3) {
                    val text = parts.dropLast(2).joinToString(":")
                    val startTime = parts[parts.size - 2].toDoubleOrNull() ?: 0.0
                    val endTime = parts[parts.size - 1].toDoubleOrNull() ?: 0.0
                    val isLast = wordData == data.split("|").last()
                    WordTimestamp(
                        text = text,
                        startTime = startTime,
                        endTime = endTime,
                        hasTrailingSpace = !isLast
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLine(line: String, words: List<WordTimestamp>? = null): List<LyricsEntry>? {
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        var text = matchResult.groupValues[3]
        val timeMatchResults = TIME_REGEX.findAll(times)
        val agentMatch = AGENT_REGEX.find(text)
        val agent = agentMatch?.groupValues?.get(1)
        if (agentMatch != null) {
            text = text.replaceFirst(AGENT_REGEX, "")
        }
        val isBackground = BACKGROUND_REGEX.containsMatchIn(text)
        if (isBackground) {
            text = text.replaceFirst(BACKGROUND_REGEX, "")
        }
        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLong()
                if (milString.length == 2) {
                    mil *= 10
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                LyricsEntry(time, text, words, agent = agent, isBackground = isBackground)
            }.toList()
    }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
    ): Int {
        val threshold = 100L
        for (index in lines.indices) {
            if (lines[index].time >= position + threshold) {
                return index - 1
            }
        }
        return lines.lastIndex
    }

    fun findActiveLineIndices(
        lines: List<LyricsEntry>,
        position: Long,
    ): Set<Int> {
        val active = mutableSetOf<Int>()
        val hasWordTimings = lines.any { !it.words.isNullOrEmpty() }
        for (index in lines.indices) {
            val line = lines[index]
            if (line.time > position) break
            val lineEndMs: Long = if (!line.words.isNullOrEmpty()) {
                (line.words.last().endTime * 1000).toLong()
            } else {
                if (index + 1 < lines.size) lines[index + 1].time else Long.MAX_VALUE
            }
            if (position <= lineEndMs) {
                active.add(index)
            }
        }
        if (!hasWordTimings && active.size > 1) {
            val mainActive = active.filter { lines[it].isBackground == false }
            if (mainActive.size > 1) {
                val maxTime = mainActive.maxOf { lines[it].time }
                active.removeAll { it in mainActive && lines[it].time < maxTime }
            }
        }
        return active
    }

    fun isWordSynced(lyrics: String): Boolean {
        return (lyrics.contains("<") && lyrics.contains(">") && (lyrics.contains("|") || lyrics.contains(":"))) ||
                lyrics.contains(RICH_SYNC_WORD_REGEX)
    }

    fun isLineSynced(lyrics: String): Boolean {
        return lyrics.contains(TIME_REGEX) ||
                lyrics.contains(PAXSENIX_AGENT_LINE_REGEX) ||
                lyrics.contains(PAXSENIX_BG_LINE_REGEX)
    }

    fun getLyricsQuality(lyrics: String): Int {
        if (lyrics.isBlank() || lyrics == "Lyrics not found") return 0
        if (isWordSynced(lyrics)) return 3
        if (isLineSynced(lyrics)) return 2
        return 1
    }
}
