/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.tv

import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.PlayerConnection
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TvActivity : ComponentActivity() {
    @Inject lateinit var database: MusicDatabase

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder is MusicService.MusicBinder) {
                playerConnection?.dispose()
                playerConnection = PlayerConnection(this@TvActivity, binder, database, lifecycleScope)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isAndroidTv(this)) {
            finish()
            return
        }
        setContent {
            TvTheme {
                TvScreen(database = database, playerConnection = playerConnection, onExitApp = ::stopPlaybackAndExit)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isAndroidTv(this)) return
        if (!MusicService.isRunning) {
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, MusicService::class.java))
            }.onFailure { Timber.w(it, "Unable to start TV playback service") }
        }
        if (!serviceBound) {
            serviceBound = bindService(
                Intent(this, MusicService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        }
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        playerConnection?.dispose()
        playerConnection = null
        super.onDestroy()
    }

    private fun stopPlaybackAndExit() {
        playerConnection?.service?.stopPlaybackAndService()
        stopService(Intent(this, MusicService::class.java))
        finishAndRemoveTask()
    }
}

fun isAndroidTv(context: Context): Boolean {
    val uiModeManager = context.getSystemService(UiModeManager::class.java)
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
