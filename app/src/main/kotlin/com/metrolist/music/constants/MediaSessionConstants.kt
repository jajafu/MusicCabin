/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.constants

import android.os.Bundle
import androidx.media3.session.SessionCommand

object MediaSessionConstants {
    const val ACTION_TOGGLE_LIBRARY = "TOGGLE_LIBRARY"
    const val ACTION_TOGGLE_START_RADIO = "TOGGLE_START_RADIO"
    const val ACTION_TOGGLE_LIKE = "TOGGLE_LIKE"
    const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
    const val ACTION_TOGGLE_REPEAT_MODE = "TOGGLE_REPEAT_MODE"
    const val ACTION_ADD_TO_TARGET_PLAYLIST = "ADD_TO_TARGET_PLAYLIST"
    const val ACTION_SET_VOLUME = "SET_VOLUME"

    val CommandToggleLibrary = SessionCommand(ACTION_TOGGLE_LIBRARY, Bundle.EMPTY)
    val CommandToggleLike = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    val CommandToggleStartRadio = SessionCommand(ACTION_TOGGLE_START_RADIO, Bundle.EMPTY)
    val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val CommandToggleRepeatMode = SessionCommand(ACTION_TOGGLE_REPEAT_MODE, Bundle.EMPTY)
    val CommandAddToTargetPlaylist = SessionCommand(ACTION_ADD_TO_TARGET_PLAYLIST, Bundle.EMPTY)
    val CommandSetVolume = SessionCommand(ACTION_SET_VOLUME, Bundle.EMPTY)

    // Wire format of the SET_VOLUME custom command: the caller passes a mode
    // (absolute set / relative step / state query) and, for absolute sets, the
    // target as a 0..1 fraction; the result extras always carry the applied
    // volume so callers can report the new level.
    const val EXTRA_VOLUME_MODE = "volume_mode"
    const val EXTRA_VOLUME = "volume"
    const val VOLUME_MODE_SET = "set"
    const val VOLUME_MODE_UP = "up"
    const val VOLUME_MODE_DOWN = "down"
    const val VOLUME_MODE_GET = "get"

    // Voice "louder"/"quieter" step: 10 percent of the app volume range.
    const val VOLUME_STEP = 0.1f

    const val TARGET_PLAYLIST_AUTO = "auto"
}
