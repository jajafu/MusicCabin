/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.voice

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource

/**
 * Public entry point for any voice assistant or external app.
 *
 * No caller restriction is applied on purpose: any app can send this
 * broadcast. Access is gated only by the two user switches under
 * Privacy settings ("Voice control"). See docs/voice-control.md.
 */
object VoiceControl {
    const val ACTION = "com.metrolist.music.action.VOICE_CONTROL"

    const val EXTRA_COMMAND = "command"
    const val EXTRA_QUERY = "query"

    const val COMMAND_PLAY_QUERY = "play_query"
    const val COMMAND_PLAY_ARTIST = "play_artist"
    const val COMMAND_PLAY_LIKED = "play_liked"
    const val COMMAND_PLAY = "play"
    const val COMMAND_PAUSE = "pause"
    const val COMMAND_STOP = "stop"
    const val COMMAND_NOW_PLAYING = "now_playing"
    const val COMMAND_NEXT = "next"
    const val COMMAND_PREVIOUS = "previous"
    const val COMMAND_LIKE = "like"
    const val COMMAND_UNLIKE = "unlike"
    const val COMMAND_REPEAT_TOGGLE = "repeat_toggle"
    const val COMMAND_REPEAT_OFF = "repeat_off"
    const val COMMAND_REPEAT_ALL = "repeat_all"
    const val COMMAND_REPEAT_ONE = "repeat_one"
    const val COMMAND_REPEAT_STATE = "repeat_state"
    const val COMMAND_VOLUME_UP = "volume_up"
    const val COMMAND_VOLUME_DOWN = "volume_down"
    const val COMMAND_VOLUME_SET = "volume_set"
    const val COMMAND_VOLUME_GET = "volume_get"

    const val EXTRA_STATUS = "status"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_TITLE = "title"
    const val EXTRA_ARTIST = "artist"
    const val EXTRA_ARTIST_MATCHED = "artist_matched"
    const val EXTRA_IS_PLAYING = "is_playing"
    const val EXTRA_MODE = "mode"
    const val EXTRA_VOLUME = "volume"

    const val STATUS_PLAYED = "played"
    const val STATUS_PLAYING = "playing"
    const val STATUS_PAUSED = "paused"
    const val STATUS_STOPPED = "stopped"
    const val STATUS_NOW_PLAYING = "now_playing"
    const val STATUS_NEXT = "next"
    const val STATUS_PREVIOUS = "previous"
    const val STATUS_LIKED = "liked"
    const val STATUS_UNLIKED = "unliked"
    const val STATUS_ALREADY_LIKED = "already_liked"
    const val STATUS_ALREADY_UNLIKED = "already_unliked"
    const val STATUS_REPEAT = "repeat"
    const val STATUS_VOLUME = "volume"
    const val STATUS_NOT_FOUND = "not_found"
    const val STATUS_EMPTY = "empty"
    const val STATUS_NO_NETWORK = "no_network"
    const val STATUS_DENIED = "denied"
    const val STATUS_NO_SONG = "no_song"
    const val STATUS_ERROR = "error"

    const val MODE_OFF = "off"
    const val MODE_ALL = "all"
    const val MODE_ONE = "one"
}

/**
 * Picks the English or Chinese text based on the current locale, so voice
 * strings only need to live in metrolist_strings.xml as an English base
 * plus a _zh_tw pair (same convention as the TV strings).
 */
@Composable
fun voiceLocalizedString(@StringRes english: Int, @StringRes chinese: Int, vararg args: Any): String {
    val id = if (LocalConfiguration.current.locales[0].language == "zh") chinese else english
    return if (args.isEmpty()) stringResource(id) else stringResource(id, *args)
}

fun voiceLocalizedString(context: Context, @StringRes english: Int, @StringRes chinese: Int, vararg args: Any): String {
    val id = if (context.resources.configuration.locales[0].language == "zh") chinese else english
    return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
}

/**
 * Turns the volume target a voice assistant forwards in `query` into a
 * 0..1 fraction. Accepts whole numbers 0–100 as percent ("80" → 0.8),
 * decimals 0–1 as a fraction ("0.8" → 0.8), plus the spoken words
 * 最大/max (100%) and 靜音/mute (0%). Everything else — including mixed
 * forms like "1.5" or "80.5" that the docs declare invalid — returns null
 * so the caller answers with the "say a volume between 0 and 100" hint.
 */
internal fun parseVoiceVolume(raw: String): Float? {
    val text = raw.trim().trimEnd('%').trim()
    if (text.isEmpty()) return null
    when (text.lowercase()) {
        "max", "最大" -> return 1f
        "mute", "靜音" -> return 0f
    }
    val value = text.toFloatOrNull() ?: return null
    if (!value.isFinite()) return null
    return if (text.contains('.')) {
        if (value in 0f..1f) value else null
    } else {
        if (value in 0f..100f) value / 100f else null
    }
}
