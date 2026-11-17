/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.audio

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide vocal reduction state.
 *
 * Session-scoped on purpose: enabling applies to the current playback right
 * away and leaving any photo frame screen ends the session via [endSession],
 * so the state is intentionally not persisted across app restarts. The main
 * app, the TV activity and [com.metrolist.music.playback.MusicService] all
 * run in the same process, so this single instance is shared by every UI
 * that shows the toggle.
 */
object VocalReductionController {
    val enabled = MutableStateFlow(false)

    /**
     * End the vocal reduction session. Every frame screen calls this exactly
     * once on the exit path that owns it (view model cleared for the
     * navigation-scoped frames, composable disposed for the TV frame), so the
     * semantics stay in one place.
     */
    fun endSession() {
        enabled.value = false
    }
}
