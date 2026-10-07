/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.tv

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.App
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject

/**
 * TV account state plus the one-shot login receiver.
 * The receiver only runs while the TV account page is open; after the phone
 * pushes its session, credentials are stored in DataStore (the same keys the
 * phone login uses) and a full sync pulls playlists down to the TV.
 */
@HiltViewModel
class TvAuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    data class State(
        val url: String? = null,
        val code: String = "",
        val starting: Boolean = false,
        val startError: Boolean = false,
        val loggedIn: Boolean = false,
        val accountName: String = "",
        val justAuthorized: String? = null,
    )

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var receiverJob: Job? = null
    private var receiver: TvAuthReceiver? = null
    private var receiving = false

    init {
        viewModelScope.launch {
            context.dataStore.data
                .map { it[InnerTubeCookieKey].orEmpty() to it[AccountNameKey].orEmpty() }
                .collect { (cookie, name) ->
                    val loggedIn = runCatching { "SAPISID" in parseCookieString(cookie) }.getOrDefault(false)
                    mutableState.value = mutableState.value.copy(
                        loggedIn = loggedIn,
                        accountName = if (loggedIn) name.ifBlank { FALLBACK_ACCOUNT } else "",
                        justAuthorized = mutableState.value.justAuthorized?.takeIf { loggedIn },
                    )
                }
        }
    }

    fun startReceiver() {
        if (receiving || mutableState.value.loggedIn) return
        receiving = true
        val code = "%06d".format(SecureRandom().nextInt(1_000_000))
        mutableState.value = mutableState.value.copy(url = null, code = code, starting = true, startError = false)
        receiverJob = viewModelScope.launch {
            val server = TvAuthReceiver(context, expectedCode = { mutableState.value.code }, onAuth = ::persist)
            try {
                val url = withContext(Dispatchers.IO) { server.start() }
                if (receiving) {
                    receiver = server
                    mutableState.value = mutableState.value.copy(url = url, starting = false)
                } else {
                    server.close()
                }
            } catch (_: Exception) {
                server.close()
                if (receiving) mutableState.value = mutableState.value.copy(starting = false, startError = true)
            }
        }
    }

    fun stopReceiver() {
        receiving = false
        receiverJob?.cancel()
        receiverJob = null
        receiver?.close()
        receiver = null
        mutableState.value = mutableState.value.copy(url = null, starting = false, startError = false)
    }

    fun syncNow() {
        syncUtils.performFullSync()
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.clearPendingPlaylistEdits()
            syncUtils.clearAllSyncedContent()
            App.forgetAccount(context)
        }
    }

    private fun persist(bundle: TvAuthBundle): Boolean = runBlocking {
        val dataSyncId = bundle.dataSyncId.substringBefore("||")
        val authUser = bundle.authUser.filter(Char::isDigit).ifBlank { "0" }
        val saved = context.safeDataStoreEdit { settings ->
            settings[InnerTubeCookieKey] = bundle.cookie
            settings[VisitorDataKey] = bundle.visitorData
            settings[DataSyncIdKey] = dataSyncId
            settings[InnerTubeAuthUserKey] = authUser
            settings[AccountNameKey] = bundle.accountName
            settings[AccountEmailKey] = bundle.accountEmail
            settings[AccountChannelHandleKey] = bundle.channelHandle
        }
        if (!saved) return@runBlocking false
        // Single use: stop listening as soon as a session lands, then pull the library.
        // Push the session into the in-memory YouTube client immediately. DataStore
        // observers in App update it asynchronously, and a full sync started here
        // would otherwise run with stale auth — liked songs (playlist LM) can still
        // succeed while the library call for saved playlists fails.
        runCatching {
            YouTube.cookie = bundle.cookie
            YouTube.visitorData = bundle.visitorData
            YouTube.dataSyncId = dataSyncId
            YouTube.authUser = authUser
        }
        stopReceiver()
        context.dataStore.data.first()
        syncUtils.performFullSync()
        val name = bundle.accountName.ifBlank { FALLBACK_ACCOUNT }
        mutableState.value = mutableState.value.copy(loggedIn = true, accountName = name, justAuthorized = name)
        true
    }

    override fun onCleared() {
        stopReceiver()
        super.onCleared()
    }

    private companion object {
        const val FALLBACK_ACCOUNT = "YouTube Account"
    }
}
