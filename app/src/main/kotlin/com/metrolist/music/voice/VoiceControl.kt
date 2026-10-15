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
    const val COMMAND_PLAY_LIKED = "play_liked"
    const val COMMAND_PLAY = "play"
    const val COMMAND_PAUSE = "pause"
    const val COMMAND_NEXT = "next"
    const val COMMAND_PREVIOUS = "previous"
    const val COMMAND_LIKE = "like"
    const val COMMAND_UNLIKE = "unlike"
    const val COMMAND_REPEAT_TOGGLE = "repeat_toggle"

    const val EXTRA_STATUS = "status"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_TITLE = "title"
    const val EXTRA_MODE = "mode"

    const val STATUS_PLAYED = "played"
    const val STATUS_PLAYING = "playing"
    const val STATUS_PAUSED = "paused"
    const val STATUS_NEXT = "next"
    const val STATUS_PREVIOUS = "previous"
    const val STATUS_LIKED = "liked"
    const val STATUS_UNLIKED = "unliked"
    const val STATUS_REPEAT = "repeat"
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
